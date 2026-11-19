package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.IconButton;
import com.enderio.core.client.gui.widgets.ToggleIconButton;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.content.filters.item.DamageFilterModePickerWidget;
import com.enderio.enderio.client.content.filters.item.EnderItemFilterScreen;
import com.enderio.enderio.client.content.filters.item.LimitedItemFilterScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilterSlot;
import com.enderio.enderio.init.EIOItems;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class ClientFilterProgressionSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, PREVIEW, CRAFTED, CONDUIT, INSTALL, CONFIGURE, EDIT, EDITED, BACK,
        DISTANCE, RETURN, SAVED, DONE
    }

    private record Endpoint(String name, BlockPos pos, Direction side, int slot, Supplier<? extends Item> filter) {}

    private static final BlockPos TABLE = new BlockPos(3279, 80, 3);
    private static final List<Endpoint> ENDPOINTS = List.of(
        new Endpoint("large", new BlockPos(3279, 80, 9), Direction.WEST, 0, EIOItems.BIG_ITEM_FILTER),
        new Endpoint("large-advanced", new BlockPos(3280, 80, 13), Direction.EAST, 1, EIOItems.BIG_ADVANCED_ITEM_FILTER),
        new Endpoint("limited", new BlockPos(3280, 80, 5), Direction.EAST, 1, EIOItems.LIMITED_ITEM_FILTER));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int recipeIndex;
    private int endpointIndex;
    private int waitSince = -1;
    private ResourceLocation recipeId;
    private Item resultItem;
    private boolean returning;

    ClientFilterProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 220_000_000_000L) {
                throw new AssertionError("Filter progression stopped in " + stage + " at recipe " + recipeId);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!ready("table") || player.distanceToSqr(3279.5, 80, 5.5) > 0.1) return;
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false));
                    stage = Stage.RECIPE;
                }
                case RECIPE, PREVIEW, CRAFTED -> craft();
                default -> conduit(ENDPOINTS.get(endpointIndex));
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        if (stage == Stage.RECIPE) {
            if (ready("recipes-complete")) {
                player.closeContainer();
                signal("crafting-closed");
                stage = Stage.CONDUIT;
                return;
            }
            var descriptor = output.resolve("recipe-" + recipeIndex + ".txt");
            if (!Files.isRegularFile(descriptor)) return;
            var lines = Files.readAllLines(descriptor);
            recipeId = ResourceLocation.parse(lines.get(0));
            resultItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(lines.get(1)));
            var recipe = minecraft.level.getRecipeManager().byKey(recipeId)
                .orElseThrow(() -> new AssertionError("The client did not receive recipe " + recipeId));
            if (!player.getRecipeBook().contains(recipe)) {
                if (waitSince == -1) waitSince = player.tickCount;
                if (player.tickCount - waitSince > 80) throw new AssertionError("Recipe did not unlock: " + recipeId);
                return;
            }
            String path = recipeId.getPath();
            if (path.startsWith("erase_") || path.endsWith("_erasure")) {
                var configured = playerSlot(screen, 0);
                if (!configured.getItem().is(resultItem) || !configured.getItem().hasTag()) return;
                clickSlot(screen, configured);
                clickSlot(screen, menu.getSlot(1));
            } else {
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
            }
            waitSince = player.tickCount;
            stage = Stage.PREVIEW;
        } else if (stage == Stage.PREVIEW) {
            var result = menu.getSlot(0).getItem();
            if (!result.is(resultItem)) {
                if (player.tickCount - waitSince > 80) throw new AssertionError("Wrong recipe output for " + recipeId + ": " + result);
                return;
            }
            check("native_crafting_preview_" + recipeIndex, result.getCount() == 1 && !result.hasTag());
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
            stage = Stage.CRAFTED;
        } else if (!menu.getSlot(0).hasItem() && menu.slots.subList(1, 10).stream().noneMatch(Slot::hasItem)) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                var stack = inventory.getItem(i);
                if (!stack.is(resultItem)) continue;
                check("native_crafting_pickup_" + recipeIndex, stack.getCount() == 1 && !stack.hasTag() && menu.getCarried().isEmpty());
                var receipt = new JsonObject();
                receipt.addProperty("inventorySlot", i);
                signal("crafted-" + recipeIndex, receipt.toString());
                if (recipeIndex == 4) capture("filter-crafting");
                recipeIndex++;
                waitSince = -1;
                stage = Stage.RECIPE;
                return;
            }
        }
    }

    private void conduit(Endpoint endpoint) throws IOException {
        var player = minecraft.player;
        switch (stage) {
            case CONDUIT, RETURN -> {
                String step = stage == Stage.RETURN ? "return" : endpoint.name();
                if (!ready(step) || minecraft.screen != null
                    || player.distanceToSqr(endpoint.pos().getX() + 0.5, 80, endpoint.pos().getZ() + 2.5) > 0.15
                    || !(minecraft.level.getBlockEntity(endpoint.pos()) instanceof ConduitBundleBlockEntity bundle)) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(endpoint.pos()).relative(endpoint.side(), 0.45),
                    Direction.SOUTH, endpoint.pos(), false);
                var connection = bundle.getShape().getConnectionFromHit(endpoint.pos(), hit);
                if (connection == null || connection.getFirst() != endpoint.side()) return;
                returning = stage == Stage.RETURN;
                minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                stage = returning ? Stage.CONFIGURE : Stage.INSTALL;
            }
            case INSTALL -> {
                if (!(minecraft.screen instanceof ConduitScreen screen) || !atEndpoint(screen, endpoint)) return;
                var source = playerSlot(screen, 0);
                if (!source.getItem().is(endpoint.filter().get())) return;
                clickSlot(screen, source);
                clickSlot(screen, screen.getMenu().getSlot(endpoint.slot()));
                stage = Stage.CONFIGURE;
            }
            case CONFIGURE -> {
                if (!(minecraft.screen instanceof ConduitScreen screen) || !atEndpoint(screen, endpoint)
                    || !screen.getMenu().getSlot(endpoint.slot()).getItem().is(endpoint.filter().get())) return;
                int x = screen.getGuiLeft() + (endpoint.slot() == 0 ? 113 : 23);
                int y = screen.getGuiTop() + 89;
                var button = screen.children().stream().filter(IconButton.class::isInstance).map(IconButton.class::cast)
                    .filter(widget -> widget.getX() == x && widget.getY() == y).findFirst().orElseThrow();
                if (!button.visible) return;
                check(endpoint.name() + (returning ? "_reopens_from_conduit" : "_installed_through_native_slot"),
                    !playerSlot(screen, 0).hasItem() && screen.getMenu().getCarried().isEmpty());
                click(screen, x + 2, y + 2);
                stage = returning ? Stage.SAVED : Stage.EDIT;
            }
            case EDIT -> {
                if (minecraft.screen instanceof EnderItemFilterScreen screen) {
                    copyGhost(screen, screen.getMenu().getSlot(35));
                    toggle(screen);
                    if (endpoint.slot() == 1) {
                        var picker = screen.children().stream().filter(DamageFilterModePickerWidget.class::isInstance)
                            .map(DamageFilterModePickerWidget.class::cast).findFirst().orElseThrow();
                        check("installed_advanced_picker_accepts_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                    }
                } else if (minecraft.screen instanceof LimitedItemFilterScreen screen) {
                    copyGhost(screen, screen.getMenu().getSlot(17));
                    toggle(screen);
                } else {
                    return;
                }
                stage = Stage.EDITED;
            }
            case EDITED, SAVED -> {
                if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen) || !showsEdit(screen, endpoint)) return;
                if (stage == Stage.SAVED) {
                    check("returning_to_the_limited_editor_recovers_the_same_stock_setting", true);
                    click(screen, screen.getGuiLeft() + 5, screen.getGuiTop() + 5);
                    stage = Stage.BACK;
                    return;
                }
                check(endpoint.name() + "_installed_editor_updates_and_preserves_the_sample",
                    playerSlot(screen, 1).hasItem() && screen.getMenu().getCarried().isEmpty());
                capture(endpoint.name() + "-installed");
                signal(endpoint.name() + "-edited");
                if (endpoint.name().equals("limited")) {
                    waitSince = -1;
                    stage = Stage.DISTANCE;
                } else {
                    click(screen, screen.getGuiLeft() + 5, screen.getGuiTop() + 5);
                    stage = Stage.BACK;
                }
            }
            case DISTANCE -> {
                if (!ready("distance") || player.getZ() < endpoint.pos().getZ() + 10) return;
                if (waitSince == -1) waitSince = player.tickCount;
                if (minecraft.screen != null && player.tickCount - waitSince < 45) return;
                check("installed_filter_editor_closes_out_of_conduit_range", minecraft.screen == null);
                signal("closed-out-of-range");
                stage = Stage.RETURN;
            }
            case BACK -> {
                if (!(minecraft.screen instanceof ConduitScreen screen) || !atEndpoint(screen, endpoint)) return;
                check(endpoint.name() + "_back_restores_the_correct_endpoint",
                    screen.getMenu().getSlot(endpoint.slot()).getItem().is(endpoint.filter().get()));
                player.closeContainer();
                signal(endpoint.name() + "-closed");
                if (endpointIndex == ENDPOINTS.size() - 1) finish(null);
                else {
                    endpointIndex++;
                    stage = Stage.CONDUIT;
                }
            }
            default -> { }
        }
    }

    private static boolean showsEdit(AbstractContainerScreen<?> screen, Endpoint endpoint) {
        if (screen instanceof LimitedItemFilterScreen limited) {
            var sample = (LimitedItemFilterSlot) limited.getMenu().getSlot(17);
            return limited.getMenu().shouldCompareComponents() && sample.getResource().getCount() == 37;
        }
        if (!(screen instanceof EnderItemFilterScreen item) || !item.getMenu().getSlot(35).hasItem()) return false;
        return endpoint.slot() == 0 ? item.getMenu().isInverted()
            : item.getMenu().shouldCompareComponents() && item.getMenu().damageFilterMode() == DamageFilterMode.UP_TO_25;
    }

    private static boolean atEndpoint(ConduitScreen screen, Endpoint endpoint) {
        return screen.getMenu().getBlockPos().equals(endpoint.pos()) && screen.getMenu().getSide() == endpoint.side();
    }

    private void copyGhost(AbstractContainerScreen<?> screen, Slot ghost) {
        var item = playerSlot(screen, 1);
        if (!item.hasItem()) throw new AssertionError("The filter's physical sample is missing");
        clickSlot(screen, item);
        clickSlot(screen, ghost);
        clickSlot(screen, item);
    }

    private Slot playerSlot(AbstractContainerScreen<?> screen, int index) {
        return screen.getMenu().slots.stream().filter(slot -> slot.container == minecraft.player.getInventory()
            && slot.getSlotIndex() == index).findFirst().orElseThrow();
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Slot slot) {
        click(screen, screen.getGuiLeft() + slot.x + 7, screen.getGuiTop() + slot.y + 7);
    }

    private static void click(AbstractContainerScreen<?> screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void toggle(AbstractContainerScreen<?> screen) {
        var widget = screen.children().stream().filter(ToggleIconButton.class::isInstance).map(ToggleIconButton.class::cast)
            .findFirst().orElseThrow();
        click(screen, widget.getX() + 2, widget.getY() + 2);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO filter progression", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-ready-" + name));
    }

    private void signal(String name) throws IOException {
        signal(name, "{}");
    }

    private void signal(String name, String json) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        var temp = Files.writeString(signals.resolve(name + ".tmp"), json);
        Files.move(temp, signals.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var capture = ClientSmoke.capture(name + ".png");
        captures.put(name, capture);
        check(name + "_silent_background_capture", Boolean.TRUE.equals(capture.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_filter_progression", result);
    }
}

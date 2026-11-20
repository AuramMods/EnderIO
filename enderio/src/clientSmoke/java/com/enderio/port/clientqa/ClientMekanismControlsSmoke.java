package com.enderio.port.clientqa;

import com.enderio.core.client.gui.widgets.IconButton;
import com.enderio.core.client.gui.widgets.ToggleIconButton;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.filters.FilterSlot;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class ClientMekanismControlsSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, RESULT, TAKEN, PLACE_HEAT, HEAT_PLACED,
        OPEN_CHEMICAL, INSTALL, CONFIGURE, EDIT, CHECK, BACK, OPEN_HEAT, HEAT, HEAT_EDITED, DONE
    }

    private record Craft(String id, int count, Predicate<ItemStack> matches) { }

    private static final BlockPos CHEMICAL = new BlockPos(1151, 80, 0);
    private static final BlockPos HEAT = new BlockPos(1151, 80, 12);
    private static final BlockPos TABLE = new BlockPos(1151, 80, 5);
    private static final ResourceLocation HEAT_VARIANT = ResourceLocation.parse("enderio:heat");
    private static final ResourceLocation FILTER = ResourceLocation.parse("enderio:basic_chemical_filter");
    private static final ResourceLocation MENU = ResourceLocation.parse("enderio:chemical_filter");
    private static final List<Craft> RECIPES = List.of(
        new Craft("mek_chemical_filter", 1, ClientMekanismControlsSmoke::isFilter),
        new Craft("mek_advanced_thermodynamic_conductor", 3, ClientMekanismControlsSmoke::isHeatConduit));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopen = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final boolean progression;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final ItemStack[] samples;
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int arrivalTick = -1;
    private int recipeIndex;
    private int placedHeat;

    ClientMekanismControlsSmoke(boolean progression) {
        this.progression = progression;
        samples = new ItemStack[progression ? 1 : 4];
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Client stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Mekanism conduit QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_survival_multiplayer", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = progression && !reopen ? Stage.TABLE : Stage.OPEN_CHEMICAL;
                }
                case TABLE, RECIPE, RESULT, TAKEN -> craft();
                case PLACE_HEAT, HEAT_PLACED -> placeHeat();
                case OPEN_CHEMICAL -> open("server-chemical", CHEMICAL, reopen ? Stage.CONFIGURE : Stage.INSTALL);
                case INSTALL, CONFIGURE, EDIT, CHECK, BACK -> chemical();
                case OPEN_HEAT -> open("server-heat", HEAT, Stage.HEAT);
                case HEAT, HEAT_EDITED -> heat();
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        var player = minecraft.player;
        if (stage == Stage.TABLE) {
            if (!ready("server-table") || minecraft.screen != null
                || player.distanceToSqr(1151.5, 80, 7.5) > 0.15) return;
            player.getInventory().selected = 8;
            var hit = new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false);
            check("opens_the_native_crafting_table", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.RECIPE;
            return;
        }
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var craft = RECIPES.get(recipeIndex);
        switch (stage) {
            case RECIPE -> {
                var key = ResourceLocation.fromNamespaceAndPath("enderio", craft.id());
                var recipe = minecraft.level.getRecipeManager().byKey(key).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check(craft.id() + "_unlocked_from_its_ingredients", true);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var result = menu.getSlot(0).getItem();
                if (!craft.matches().test(result)) return;
                check(craft.id() + "_crafts_the_exact_result", result.getCount() == craft.count());
                capture(craft.id());
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKEN;
            }
            case TAKEN -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem)
                    || count(craft.matches()) != craft.count()) return;
                check(craft.id() + "_player_receives_the_complete_result", menu.getCarried().isEmpty());
                if (++recipeIndex == RECIPES.size()) {
                    player.closeContainer();
                    signal("crafted");
                    stage = Stage.PLACE_HEAT;
                } else {
                    stage = Stage.RECIPE;
                }
            }
            default -> { }
        }
    }

    private void placeHeat() throws IOException {
        var player = minecraft.player;
        var target = HEAT.offset(placedHeat, 0, 0);
        if (stage == Stage.PLACE_HEAT) {
            if (!ready("server-place-heat") || minecraft.screen != null || player.distanceToSqr(1151.5, 80, 10.5) > 0.15) return;
            var inventory = player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (isHeatConduit(inventory.getItem(slot))) {
                    inventory.selected = slot;
                    break;
                }
            }
            check("places_crafted_heat_part_" + placedHeat, isHeatConduit(player.getMainHandItem()));
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
            check("heat_placement_accepted_" + placedHeat, minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.HEAT_PLACED;
        } else if (minecraft.level.getBlockEntity(target) instanceof ConduitBundleBlockEntity bundle
                && bundle.getConduits().stream().anyMatch(holder -> holder.is(HEAT_VARIANT))
                && count(ClientMekanismControlsSmoke::isHeatConduit) == 2 - placedHeat) {
            check("one_crafted_heat_part_consumed_" + placedHeat, true);
            if (++placedHeat == 2) {
                capture("crafted-heat-line");
                signal("heat-placed");
                stage = Stage.OPEN_CHEMICAL;
            } else {
                stage = Stage.PLACE_HEAT;
            }
        }
    }

    private void open(String signal, BlockPos pos, Stage next) {
        var face = pos.equals(HEAT) ? Direction.NORTH : Direction.SOUTH;
        double playerZ = pos.getZ() + (face == Direction.NORTH ? -1.5 : 2.5);
        if (!ready(signal) || minecraft.screen != null || minecraft.player.distanceToSqr(pos.getX() + 0.5, 80, playerZ) > 0.15
            || !(minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle)) {
            arrivalTick = -1;
            return;
        }
        if (arrivalTick < 0) arrivalTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - arrivalTick < 6) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.WEST, 0.45), face, pos, false);
        var connection = bundle.getShape().getConnectionFromHit(pos, hit);
        if (connection == null || connection.getFirst() != Direction.WEST) return;
        minecraft.player.getInventory().selected = 8;
        check(pos.equals(CHEMICAL) ? "open_chemical_endpoint" : "open_heat_endpoint",
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        arrivalTick = -1;
        stage = next;
    }

    private void chemical() throws ReflectiveOperationException, IOException {
        switch (stage) {
            case INSTALL, CONFIGURE, BACK -> {
                if (!(minecraft.screen instanceof ConduitScreen screen) || !at(screen, CHEMICAL, "chemical")) return;
                var slot = screen.getMenu().getSlot(0);
                if (stage == Stage.INSTALL) {
                    var source = screen.getMenu().slots.stream().filter(candidate -> candidate.container == minecraft.player.getInventory()
                        && isFilter(candidate.getItem())).findFirst().orElse(null);
                    if (source == null) return;
                    clickSlot(screen, source);
                    clickSlot(screen, slot);
                    stage = Stage.CONFIGURE;
                    return;
                }
                if (!isFilter(slot.getItem())) return;
                if (stage == Stage.BACK) {
                    check("back_returns_to_same_installed_filter", true);
                    minecraft.player.closeContainer();
                    signal("chemical-done");
                    stage = Stage.OPEN_HEAT;
                    return;
                }
                int x = screen.getGuiLeft() + 113, y = screen.getGuiTop() + 89;
                var button = screen.children().stream().filter(IconButton.class::isInstance).map(IconButton.class::cast)
                    .filter(widget -> widget.getX() == x && widget.getY() == y).findFirst().orElseThrow();
                if (!button.visible) return;
                check("filter_is_in_the_native_extract_slot", count(ClientMekanismControlsSmoke::isFilter) == 0
                    && screen.getMenu().getCarried().isEmpty());
                click(screen, x + 2, y + 2);
                stage = reopen ? Stage.CHECK : Stage.EDIT;
            }
            case EDIT, CHECK -> {
                if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)
                    || !MENU.equals(BuiltInRegistries.MENU.getKey(screen.getMenu().getType()))) return;
                if (stage == Stage.EDIT) {
                    for (int i = 0; i < samples.length; i++) {
                        if (!playerSlot(screen, sampleIndex(i)).hasItem()) return;
                    }
                    for (int i = 0; i < samples.length; i++) {
                        var source = playerSlot(screen, sampleIndex(i));
                        samples[i] = source.getItem().copy();
                        clickSlot(screen, source);
                        clickSlot(screen, screen.getMenu().getSlot(i));
                        clickSlot(screen, source);
                    }
                    var button = screen.children().stream().filter(ToggleIconButton.class::isInstance).map(ToggleIconButton.class::cast)
                        .findFirst().orElseThrow();
                    click(screen, button.getX() + 2, button.getY() + 2);
                    stage = Stage.CHECK;
                    return;
                }
                for (int i = 0; i < samples.length; i++) {
                    var sample = playerSlot(screen, sampleIndex(i)).getItem();
                    if (!(screen.getMenu().getSlot(i) instanceof FilterSlot<?> slot) || slot.isEmpty()
                        || !slot.getResourceFrom(sample).map(slot.getResource()::equals).orElse(false)) return;
                    check("chemical_sample_" + i + "_survives_ghost_copy", sample.getCount() == 1 && (reopen || ItemStack.matches(samples[i], sample)));
                }
                if (!Boolean.TRUE.equals(screen.getMenu().getClass().getMethod("isInverted").invoke(screen.getMenu()))) return;
                check("ghost_slots_and_deny_mode_sync", screen.getMenu().getCarried().isEmpty()
                    && ((FilterSlot<?>) screen.getMenu().getSlot(samples.length)).isEmpty());
                capture(reopen ? "chemical-reopened" : "chemical-deny-list");
                click(screen, screen.getGuiLeft() + 5, screen.getGuiTop() + 5);
                stage = Stage.BACK;
            }
            default -> { }
        }
    }

    private void heat() throws ReflectiveOperationException, IOException {
        if (!(minecraft.screen instanceof ConduitScreen screen) || !at(screen, HEAT, "heat")) return;
        ConnectionConfig config = screen.getMenu().connectionConfig();
        var control = (RedstoneControl) config.getClass().getMethod("extractRedstoneControl").invoke(config);
        var target = reopen ? RedstoneControl.ALWAYS_ACTIVE : RedstoneControl.NEVER_ACTIVE;
        if (stage == Stage.HEAT) {
            check("heat_menu_recovers_expected_redstone_mode", control == (reopen ? RedstoneControl.NEVER_ACTIVE : RedstoneControl.ALWAYS_ACTIVE));
            var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
            check("native_heat_picker_scrolls", picker.mouseScrolled(picker.getX() + 7, picker.getY() + 7, reopen ? -1 : 1));
            stage = Stage.HEAT_EDITED;
        } else if (control == target) {
            capture(reopen ? "heat-enabled" : "heat-paused");
            minecraft.player.closeContainer();
            signal("heat-edited");
            finish(null);
        }
    }

    private static boolean at(ConduitScreen screen, BlockPos pos, String conduit) {
        return screen.getMenu().getBlockPos().equals(pos) && screen.getMenu().getSide() == Direction.WEST
            && screen.getMenu().getConduit().unwrapKey().orElseThrow().location().equals(ResourceLocation.fromNamespaceAndPath("enderio", conduit));
    }

    private static boolean isFilter(ItemStack stack) {
        return FILTER.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean isHeatConduit(ItemStack stack) {
        return stack.is(EIOItems.CONDUIT.get()) && HEAT_VARIANT.equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    private int count(Predicate<ItemStack> matches) {
        return minecraft.player.getInventory().items.stream().filter(matches).mapToInt(ItemStack::getCount).sum();
    }

    private int sampleIndex(int sample) {
        return progression ? 7 : sample + 1;
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

    private boolean ready(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_mekanism_controls", result);
    }
}

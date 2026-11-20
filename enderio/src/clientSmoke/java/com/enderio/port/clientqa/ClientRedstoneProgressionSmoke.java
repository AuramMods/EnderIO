package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.content.filters.redstone.RedstoneCountFilterScreen;
import com.enderio.enderio.client.foundation.widgets.DyeColorPickerWidget;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.filters.redstone.RedstoneCountFilter;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
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

final class ClientRedstoneProgressionSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, RESULT, TAKEN, PLACE, PLACED, HAND_OPEN, HAND_EDIT, HAND_CONFIRM,
        ENDPOINT_OPEN, ENDPOINT_CHANGE, ENDPOINT_CONFIRM, FINISH, DONE
    }

    private static final BlockPos TABLE = new BlockPos(3316, 80, 8);
    private static final BlockPos INLET = new BlockPos(3311, 80, 8);
    private static final BlockPos OUTLET = INLET.east();
    private static final ResourceLocation REDSTONE = ResourceLocation.parse("enderio:redstone");
    private static final List<String> RECIPES = List.of("redstone_conduit", "redstone_filter_base", "redstone_counting_filter",
        "redstone_counting_filter_erasure");
    private static final List<String> FILTER_ACTIONS = List.of("install", "recover", "reinstall");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int placed;
    private int filterAction;
    private int crouchTick = -1;

    ClientRedstoneProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 175_000_000_000L) {
                throw new AssertionError("Redstone progression stopped at " + stage + ", recipe " + recipeIndex + ", filter action " + filterAction);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                String address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Redstone conduit progression", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (minecraft.level == null || player == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_survival_dedicated_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE, RECIPE, RESULT, TAKEN -> craft();
                case PLACE, PLACED -> place();
                case HAND_OPEN, HAND_EDIT, HAND_CONFIRM -> editFilter();
                case ENDPOINT_OPEN, ENDPOINT_CHANGE, ENDPOINT_CONFIRM -> useEndpoint();
                case FINISH -> {
                    if (ready("done")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        boolean erasure = recipeIndex == 3;
        if (stage == Stage.TABLE) {
            if (!ready(erasure ? "erase" : "table") || !at(TABLE)) return;
            check("opens_native_table_" + recipeIndex, use(TABLE, Direction.SOUTH, Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5)));
            stage = Stage.RECIPE;
            return;
        }
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        String name = RECIPES.get(recipeIndex);
        Predicate<ItemStack> resultType = switch (recipeIndex) {
            case 0 -> ClientRedstoneProgressionSmoke::isConduit;
            case 1 -> stack -> stack.is(EIOItems.REDSTONE_FILTER_BASE.get());
            default -> ClientRedstoneProgressionSmoke::isCounter;
        };
        int expected = recipeIndex == 0 ? 8 : 1;
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("enderio", name)).orElseThrow();
                if (erasure) {
                    var configured = inventorySlot(menu, ClientRedstoneProgressionSmoke::isCounter);
                    if (configured == null) return;
                    check("erasure_uses_the_recovered_live_counter", countState(configured.getItem()).equals(
                        new RedstoneCountFilter.Component(DyeColor.BLUE, 2, 2, false)));
                    click(menu, configured, ClickType.PICKUP);
                    click(menu, menu.getSlot(1), ClickType.PICKUP);
                } else {
                    if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                    check(name + "_unlocks_in_survival", true);
                    minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                }
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!resultType.test(stack)) return;
                check(name + "_has_the_exact_crafting_result", stack.getCount() == expected);
                if (isCounter(stack)) {
                    check(name + "_has_default_counter_state", !stack.hasTag() && countState(stack).equals(RedstoneCountFilter.INSTANCE));
                }
                if (recipeIndex == 0 || erasure) capture(name);
                click(menu, menu.getSlot(0), ClickType.QUICK_MOVE);
                stage = Stage.TAKEN;
            }
            case TAKEN -> {
                if (menu.slots.subList(0, 10).stream().anyMatch(Slot::hasItem) || count(resultType) != expected) return;
                check(name + "_native_take_clears_the_grid_and_cursor", menu.getCarried().isEmpty());
                recipeIndex++;
                if (recipeIndex == 3 || erasure) {
                    minecraft.player.closeContainer();
                    signal(erasure ? "erased" : "crafted");
                    stage = erasure ? Stage.ENDPOINT_OPEN : Stage.PLACE;
                } else {
                    stage = Stage.RECIPE;
                }
            }
            default -> { }
        }
    }

    private void place() throws IOException {
        var target = INLET.east(placed);
        if (stage == Stage.PLACE) {
            if (!ready("place") || !at(OUTLET)) return;
            select(ClientRedstoneProgressionSmoke::isConduit);
            check("places_redstone_through_native_use_" + placed, use(target.below(), Direction.UP, Vec3.atBottomCenterOf(target)));
            stage = Stage.PLACED;
        } else if (minecraft.level.getBlockEntity(target) instanceof ConduitBundleBlockEntity bundle
            && bundle.getConduits().stream().anyMatch(holder -> holder.is(REDSTONE)) && count(ClientRedstoneProgressionSmoke::isConduit) == 7 - placed) {
            check("one_crafted_conduit_consumed_" + placed, true);
            if (++placed == 2) {
                capture("crafted-redstone-line");
                signal("placed");
                stage = Stage.HAND_OPEN;
            } else {
                stage = Stage.PLACE;
            }
        }
    }

    private void editFilter() throws IOException {
        var player = minecraft.player;
        if (stage == Stage.HAND_OPEN) {
            if (!ready("edit") || minecraft.screen != null) return;
            if (crouchTick < 0) {
                select(ClientRedstoneProgressionSmoke::isCounter);
                minecraft.options.keyShift.setDown(true);
                crouchTick = player.tickCount;
                return;
            }
            if (player.tickCount - crouchTick < 4 || !player.isShiftKeyDown()) return;
            minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            minecraft.options.keyShift.setDown(false);
            stage = Stage.HAND_EDIT;
        } else if (minecraft.screen instanceof RedstoneCountFilterScreen screen) {
            if (stage == Stage.HAND_CONFIRM) {
                if (!ready("edited")) return;
                capture("configured-counter");
                player.closeContainer();
                signal("edit-closed");
                stage = Stage.ENDPOINT_OPEN;
                return;
            }
            var filter = screen.getMenu().getFilter();
            check("fresh_counter_menu_shows_green_at_eight", filter.getChannel() == DyeColor.GREEN && filter.getMaxCount() == 8 && filter.getCount() == 0);
            var picker = screen.children().stream().filter(DyeColorPickerWidget.class::isInstance).map(DyeColorPickerWidget.class::cast)
                .findFirst().orElseThrow();
            for (int scroll = 0; scroll < 2; scroll++) {
                check("native_blue_picker_scroll_" + scroll, picker.mouseScrolled(picker.getX() + 7, picker.getY() + 7, -1));
            }
            check("native_picker_selects_blue", filter.getChannel() == DyeColor.BLUE);
            var box = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
            box.setFocused(true);
            box.moveCursorToEnd();
            box.setHighlightPos(0);
            check("native_confirm_saves_count_two", box.charTyped('2', 0) && box.getValue().equals("2")
                && button.mouseClicked(button.getX() + 2, button.getY() + 2, 0));
            signal("edited");
            stage = Stage.HAND_CONFIRM;
        }
    }

    private void useEndpoint() throws IOException {
        String step = FILTER_ACTIONS.get(filterAction);
        boolean recovering = filterAction == 1;
        if (stage == Stage.ENDPOINT_OPEN) {
            if (!ready(step) || !at(OUTLET) || !(minecraft.level.getBlockEntity(OUTLET) instanceof ConduitBundleBlockEntity bundle)) return;
            var hit = new BlockHitResult(Vec3.atCenterOf(OUTLET).relative(Direction.EAST, 0.45), Direction.SOUTH, OUTLET, false);
            var connection = bundle.getShape().getConnectionFromHit(OUTLET, hit);
            if (connection == null || connection.getFirst() != Direction.EAST || !connection.getSecond().is(REDSTONE)) return;
            check(step + "_opens_native_redstone_output", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.ENDPOINT_CHANGE;
            return;
        }
        if (!(minecraft.screen instanceof ConduitScreen screen) || !screen.getMenu().getBlockPos().equals(OUTLET)
            || screen.getMenu().getSide() != Direction.EAST || !screen.getMenu().getConduit().is(REDSTONE)) return;
        var menu = screen.getMenu();
        var slot = menu.getSlot(1);
        if (stage == Stage.ENDPOINT_CHANGE) {
            if (recovering) {
                if (!isCounter(slot.getItem()) || countState(slot.getItem()).count() != 2) return;
                click(menu, slot, ClickType.QUICK_MOVE);
            } else {
                var source = inventorySlot(menu, ClientRedstoneProgressionSmoke::isCounter);
                if (source == null || slot.hasItem()) return;
                click(menu, source, ClickType.PICKUP);
                click(menu, slot, ClickType.PICKUP);
            }
            stage = Stage.ENDPOINT_CONFIRM;
        } else if (recovering ? !slot.hasItem() && count(ClientRedstoneProgressionSmoke::isCounter) == 1
            : isCounter(slot.getItem()) && count(ClientRedstoneProgressionSmoke::isCounter) == 0) {
            check(step + "_moves_the_original_filter_through_the_native_slot", menu.getCarried().isEmpty());
            minecraft.player.closeContainer();
            signal(step + "-done");
            stage = switch (++filterAction) {
                case 1 -> Stage.ENDPOINT_OPEN;
                case 2 -> Stage.TABLE;
                default -> Stage.FINISH;
            };
        }
    }

    private boolean at(BlockPos target) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(target.getX() + 0.5, 80, target.getZ() + 2.5) < 0.15;
    }

    private boolean use(BlockPos pos, Direction face, Vec3 location) {
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(location, face, pos, false)).consumesAction();
    }

    private void select(Predicate<ItemStack> matches) {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (matches.test(inventory.getItem(slot))) {
                inventory.selected = slot;
                return;
            }
        }
        throw new AssertionError("The crafted stack is missing from the hotbar");
    }

    private int count(Predicate<ItemStack> matches) {
        return minecraft.player.getInventory().items.stream().filter(matches).mapToInt(ItemStack::getCount).sum();
    }

    private Slot inventorySlot(AbstractContainerMenu menu, Predicate<ItemStack> matches) {
        return menu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory() && matches.test(slot.getItem()))
            .findFirst().orElse(null);
    }

    private void click(AbstractContainerMenu menu, Slot slot, ClickType action) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, action, minecraft.player);
    }

    private static boolean isConduit(ItemStack stack) {
        return stack.is(EIOItems.CONDUIT.get()) && REDSTONE.equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    private static boolean isCounter(ItemStack stack) {
        return stack.is(EIOItems.COUNT_FILTER.get());
    }

    private static RedstoneCountFilter.Component countState(ItemStack stack) {
        return EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
    }

    private boolean ready(String key) {
        return Files.exists(output.resolve("server-" + key));
    }

    private void signal(String key) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(key + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(key + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_background_capture", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        minecraft.options.keyShift.setDown(false);
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_redstone_progression", result);
    }
}

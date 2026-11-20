package com.enderio.port.clientqa;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.NiardScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientNiardSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, RESULT, TAKE, PLACE, OPEN, INSTALL, FILL, RANGE, REDSTONE,
        SET, MINE, MINING, PICKUP, REPLANT, OPEN_REPLANTED, RESTORED, PLACED_LAVA, DONE
    }

    private static final Craft RECIPE = Craft.shaped("niard", 1, "BTBPVPIRI", Map.of(
        'B', "minecraft:bucket", 'T', "fluid_tank", 'P', "minecraft:piston", 'V', "void_chassis",
        'I', "dark_steel_ingot", 'R', "dark_steel_bars"));
    private static final BlockPos TABLE = new BlockPos(4273, 80, 8);
    private static final BlockPos SOURCE = new BlockPos(4277, 80, 8);
    private static final BlockPos DESTINATION = new BlockPos(4285, 80, 8);
    private static final ActionRange CARRIED_RANGE = new ActionRange(1, true);
    private static final int CHARGE = 3677;
    private static final int[] RANGES = {5, 6, 5, 4, 3, 2, 1};

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int positioned = -1;
    private int rangeIndex;
    private int miningTick;

    ClientNiardSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Niard stopped at " + stage + ", range step " + rangeIndex);
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
                    check("niard_is_enabled_for_real_dedicated_survival", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection()
                        && EIOBlocks.NIARD.get().isEnabled(minecraft.level.enabledFeatures()));
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> openWhenReady("table", TABLE, Stage.RECIPE);
                case RECIPE, RESULT, TAKE -> craft();
                case PLACE -> {
                    if (!settled("place", SOURCE) || !place(SOURCE, false)) return;
                    signal("placed");
                    stage = Stage.OPEN;
                }
                case OPEN -> openWhenReady("open", SOURCE, Stage.INSTALL);
                case INSTALL, FILL, RANGE, REDSTONE, SET, RESTORED, PLACED_LAVA -> operate();
                case MINE -> {
                    if (!settled("mine", SOURCE) || !player.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE)) return;
                    player.getInventory().selected = 0;
                    check("native_survival_break_starts", minecraft.gameMode.startDestroyBlock(SOURCE, Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(SOURCE)) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(SOURCE, Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    var carried = player.getInventory().items.stream().filter(ClientNiardSmoke::niard).findFirst();
                    if (carried.isEmpty()) return;
                    check("survival_pickup_keeps_the_exact_carried_niard_state", carriedState(carried.get()));
                    signal("picked-up");
                    stage = Stage.REPLANT;
                }
                case REPLANT -> {
                    if (!settled("replant", DESTINATION) || !place(DESTINATION, true)) return;
                    signal("replanted");
                    stage = Stage.OPEN_REPLANTED;
                }
                case OPEN_REPLANTED -> openWhenReady("reopen", DESTINATION, Stage.RESTORED);
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var result = menu.getSlot(0).getItem();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("the_original_void_chassis_unlocks_the_stock_recipe", player.getInventory().countItem(EIOBlocks.VOID_CHASSIS.get().asItem()) == 1);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (!niard(result) || grid.stream().filter(Slot::hasItem).count() != RECIPE.ingredientCount()) return;
                check("the_native_recipe_uses_the_stock_grid_and_makes_a_fresh_niard", RECIPE.matchesGrid(grid) && !result.hasTag());
                capture("niard-stock-recipe");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (grid.stream().anyMatch(Slot::hasItem) || player.getInventory().countItem(EIOBlocks.NIARD.get().asItem()) != 1) return;
                player.closeContainer();
                signal("crafted");
                stage = Stage.PLACE;
            }
            default -> {}
        }
    }

    private void operate() throws Exception {
        if (!(minecraft.screen instanceof NiardScreen screen)) return;
        var menu = screen.getMenu();
        var machine = menu.getBlockEntity();
        var tank = menu.getFluidTank();
        switch (stage) {
            case INSTALL -> {
                if (menu.getEnergyStorage() == null || menu.getEnergyStorage().energyStored() != CHARGE
                    || !minecraft.player.getInventory().getItem(1).is(EIOItems.BASIC_CAPACITOR.get())) return;
                click(menu, 2, 1, ClickType.SWAP);
                stage = Stage.FILL;
            }
            case FILL -> {
                if (!menu.isCapacitorInstalled() || !minecraft.player.getInventory().getItem(2).is(Items.LAVA_BUCKET)) return;
                click(menu, 0, 2, ClickType.SWAP);
                stage = Stage.RANGE;
            }
            case RANGE -> {
                if (tank == null || tank.contents().getAmount() != 1400 || !menu.getSlot(1).getItem().is(Items.BUCKET)
                    || machine.getRange() != RANGES[rangeIndex]) return;
                if (rangeIndex + 1 < RANGES.length) {
                    clickWidget(screen, 147, RANGES[rangeIndex + 1] > RANGES[rangeIndex] ? 37 : 45);
                    rangeIndex++;
                } else {
                    check("the_stock_tank_shows_lava_and_the_original_empty_bucket", tank.capacity() == 4000
                        && tank.contents().getFluid() == Fluids.LAVA && !menu.getSlot(0).hasItem());
                    clickWidget(screen, 161, 41);
                    stage = Stage.REDSTONE;
                }
            }
            case REDSTONE -> {
                if (!machine.getActionRange().equals(CARRIED_RANGE) || menu.getRedstoneControl() != RedstoneControl.ALWAYS_ACTIVE) return;
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check("the_native_redstone_picker_scrolls", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.SET;
            }
            case SET -> {
                if (menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                check("range_buttons_and_visibility_keep_the_exact_lava_and_energy", menu.isRangeVisible() && machine.getRange() == 1
                    && tank.contents().getAmount() == 1400 && menu.getEnergyStorage().energyStored() == CHARGE);
                capture("niard-configured-lava");
                minecraft.player.closeContainer();
                signal("configured");
                stage = Stage.MINE;
            }
            case RESTORED -> {
                if (tank == null || tank.contents().getAmount() != 1400 || menu.getEnergyStorage() == null || !menu.isCapacitorInstalled()) return;
                check("the_replanted_menu_restores_charge_lava_bucket_range_and_redstone", tank.contents().getFluid() == Fluids.LAVA
                    && menu.getEnergyStorage().energyStored() == CHARGE && menu.getSlot(2).getItem().is(EIOItems.BASIC_CAPACITOR.get())
                    && menu.getSlot(1).getItem().is(Items.BUCKET) && machine.getActionRange().equals(CARRIED_RANGE)
                    && menu.getRedstoneControl() == RedstoneControl.ACTIVE_WITH_SIGNAL);
                capture("niard-replanted-state");
                signal("reopened-menu");
                stage = Stage.PLACED_LAVA;
            }
            case PLACED_LAVA -> {
                if (!ready("finish") || tank == null || tank.contents().getAmount() != 400
                    || menu.getEnergyStorage().energyStored() != CHARGE - 1500) return;
                check("the_open_menu_updates_after_one_lava_source_is_placed", tank.contents().getFluid() == Fluids.LAVA
                    && machine.getActionRange().equals(CARRIED_RANGE));
                capture("niard-after-lava-placement");
                finish(null);
            }
            default -> {}
        }
    }

    private static boolean carriedState(ItemStack stack) {
        var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(stack);
        var inventory = EnderBlockEntity.ITEM_INVENTORY.get(stack);
        return fluid != null && fluid.getFluid() == Fluids.LAVA && fluid.getAmount() == 1400
            && EIODataComponents.ENERGY.getOrDefault(stack, 0) == CHARGE
            && CARRIED_RANGE.equals(EIODataComponents.ACTION_RANGE.get(stack))
            && RedstoneControl.ITEM_DATA.get(stack) == RedstoneControl.ACTIVE_WITH_SIGNAL && inventory != null;
    }

    private boolean place(BlockPos pos, boolean carried) {
        var player = minecraft.player;
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!niard(stack) || (carried && !carriedState(stack))) continue;
            player.getInventory().selected = slot;
            check(carried ? "survival_replants_the_original_drop" : "survival_places_the_crafted_niard", use(pos.below(), Direction.UP));
            return true;
        }
        return false;
    }

    private static boolean niard(ItemStack stack) {
        return stack.is(EIOBlocks.NIARD.get().asItem()) && stack.getCount() == 1;
    }

    private void openWhenReady(String name, BlockPos pos, Stage next) {
        if (!settled(name, pos)) return;
        minecraft.player.getInventory().selected = 8;
        check(name + "_opens_the_native_menu", minecraft.player.getMainHandItem().isEmpty() && use(pos, Direction.SOUTH));
        stage = next;
    }

    private boolean settled(String name, BlockPos pos) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(pos.getX() + 0.5, 80, pos.getZ() + 2.5) > 0.12) {
            positioned = -1;
            return false;
        }
        if (positioned < 0) positioned = player.tickCount;
        if (player.tickCount - positioned < 6) return false;
        positioned = -1;
        return true;
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private void clickWidget(NiardScreen screen, int x, int y) {
        check("the_range_widget_accepts_" + x + "_" + y, screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0));
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Niard", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_niard", result);
    }
}

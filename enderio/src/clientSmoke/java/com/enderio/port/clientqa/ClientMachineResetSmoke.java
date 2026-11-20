package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.content.machines.niard.NiardMenu;
import com.enderio.enderio.content.machines.obelisks.ObeliskMenu;
import com.enderio.enderio.content.machines.obelisks.xp.XPObeliskMenu;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIODataComponents;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientMachineResetSmoke {
    private enum Stage { TITLE, JOIN, MINE, MINING, PICKUP, TABLE, RECIPE, RESULT, TAKE, PLACE, OPEN, DEFAULTS, DONE }

    private record Machine(String name, int x, int energy, int fluid, ActionRange range, RedstoneControl redstone) {
        BlockPos source() { return new BlockPos(x, 80, 8); }
        BlockPos destination() { return new BlockPos(x, 80, 12); }
    }

    private static final BlockPos TABLE = new BlockPos(2320, 80, 16);
    private static final List<Machine> MACHINES = List.of(
        new Machine("niard", 2304, 3677, 1400, new ActionRange(1, true), RedstoneControl.ACTIVE_WITH_SIGNAL),
        new Machine("xp_obelisk", 2334, 0, 1573, null, RedstoneControl.NEVER_ACTIVE),
        new Machine("inhibitor_obelisk", 2310, 6101, 0, new ActionRange(5, true), RedstoneControl.NEVER_ACTIVE),
        new Machine("aversion_obelisk", 2316, 7302, 0, new ActionRange(4, false), RedstoneControl.NEVER_ACTIVE),
        new Machine("relocator_obelisk", 2322, 8503, 0, new ActionRange(0, true), RedstoneControl.NEVER_ACTIVE),
        new Machine("attractor_obelisk", 2328, 9704, 0, new ActionRange(3, true), RedstoneControl.NEVER_ACTIVE));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int settleBy = -1;
    private int menuReadyBy = -1;
    private int miningTick;
    private int index;

    ClientMachineResetSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Machine reset probe stopped at " + stage + " for machine " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var machine = MACHINES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("native_dedicated_survival", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    for (var sample : MACHINES) {
                        var recipe = recipe(sample);
                        check(sample.name() + "_stock_shapeless_reset_starts_locked",
                            recipe instanceof ShapelessRecipe && recipe.getIngredients().size() == 1
                                && !player.getRecipeBook().contains(recipe));
                    }
                    signal("connected");
                    stage = Stage.MINE;
                }
                case MINE -> {
                    if (!settled(machine, "mine", machine.source()) || !player.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE)) return;
                    player.getInventory().selected = 0;
                    check(machine.name() + "_starts_normal_survival_mining",
                        minecraft.gameMode.startDestroyBlock(machine.source(), Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(machine.source())) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(machine.source(), Direction.SOUTH);
                        }
                        return;
                    }
                    signal(machine.name() + "-mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    int slot = machineSlot(machine);
                    if (slot < 0) return;
                    check(machine.name() + "_pickup_delivers_the_populated_drop", populated(machine, player.getInventory().getItem(slot)));
                    signal(machine.name() + "-picked-up");
                    stage = Stage.TABLE;
                }
                case TABLE -> openWhenReady(machine, "table", TABLE, Stage.RECIPE);
                case RECIPE, RESULT, TAKE -> craft(machine);
                case PLACE -> {
                    if (!settled(machine, "place", machine.destination())) return;
                    int slot = machineSlot(machine);
                    if (slot < 0) return;
                    player.getInventory().selected = slot;
                    check(machine.name() + "_reset_result_places_in_survival", use(machine.destination().below(), Direction.UP));
                    signal(machine.name() + "-placed");
                    stage = Stage.OPEN;
                }
                case OPEN -> openWhenReady(machine, "open", machine.destination(), Stage.DEFAULTS);
                case DEFAULTS -> {
                    if (!(minecraft.screen instanceof MachineScreen<?>)) return;
                    if (menuReadyBy < 0) menuReadyBy = player.tickCount + 6;
                    if (player.tickCount < menuReadyBy) return;
                    if (!verifyDefaults(machine)) return;
                    if (index + 1 == MACHINES.size()) {
                        finish(null);
                    } else {
                        player.closeContainer();
                        signal(machine.name() + "-defaults");
                        menuReadyBy = -1;
                        index++;
                        stage = Stage.MINE;
                    }
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Machine machine) throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var result = menu.getSlot(0).getItem();
        switch (stage) {
            case RECIPE -> {
                int slot = machineSlot(machine);
                if (slot < 0 || !player.getRecipeBook().contains(recipe(machine))) return;
                check(machine.name() + "_native_drop_unlocks_the_reset",
                    grid.stream().noneMatch(Slot::hasItem) && populated(machine, player.getInventory().getItem(slot)));
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 1, slot, ClickType.SWAP, player);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (!result.is(item(machine.name())) || !populated(machine, menu.getSlot(1).getItem())) return;
                check(machine.name() + "_one_populated_input_previews_one_untagged_output",
                    result.getCount() == 1 && !result.hasTag() && grid.stream().filter(Slot::hasItem).count() == 1);
                capture(machine.name() + "-reset-recipe");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                int slot = machineSlot(machine);
                if (slot < 0 || grid.stream().anyMatch(Slot::hasItem)) return;
                check(machine.name() + "_normal_result_take_spends_the_original_item",
                    !player.getInventory().getItem(slot).hasTag() && player.getInventory().countItem(item(machine.name())) == 1);
                player.closeContainer();
                signal(machine.name() + "-reset");
                stage = Stage.PLACE;
            }
            default -> {}
        }
    }

    private boolean verifyDefaults(Machine machine) throws Exception {
        if (!(minecraft.screen instanceof MachineScreen<?> screen)) return false;
        var menu = screen.getMenu();
        boolean obelisk = menu instanceof ObeliskMenu<?>;
        IOMode defaultMode = obelisk ? IOMode.PULL : IOMode.NONE;
        for (Direction side : Direction.values()) {
            check(machine.name() + "_stock_io_" + side.getName(),
                menu.getBlockEntity().getIOConfig().getMode(side) == defaultMode);
        }
        check(machine.name() + "_stock_redstone", menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE);
        if (menu instanceof NiardMenu niard) {
            if (niard.getFluidTank() == null || niard.getEnergyStorage() == null) return false;
            check("niard_menu_recovers_empty_tank_slots_and_hidden_range_five",
                niard.getFluidTank().contents().isEmpty() && niard.getEnergyStorage().energyStored() == 0
                    && !niard.isCapacitorInstalled() && new ActionRange(5, false).equals(niard.getBlockEntity().getActionRange())
                    && niard.slots.subList(0, 3).stream().noneMatch(Slot::hasItem));
        } else if (menu instanceof XPObeliskMenu xp) {
            check("xp_obelisk_menu_recovers_empty_tank", xp.getFluid().isEmpty());
        } else if (menu instanceof ObeliskMenu<?> ranged) {
            if (ranged.getEnergyStorage() == null) return false;
            check(machine.name() + "_menu_recovers_empty_slots_and_hidden_range_zero",
                ranged.getEnergyStorage().energyStored() == 0 && !ranged.isCapacitorInstalled()
                    && new ActionRange(0, false).equals(ranged.getBlockEntity().getActionRange())
                    && ranged.slots.subList(0, machine.name().equals("inhibitor_obelisk") ? 1 : 2).stream().noneMatch(Slot::hasItem));
        } else {
            throw new AssertionError("Unexpected menu for " + machine.name());
        }
        capture(machine.name() + "-reset-menu");
        return true;
    }

    private Recipe<?> recipe(Machine machine) {
        return minecraft.level.getRecipeManager().byKey(id("erase_" + machine.name())).orElseThrow();
    }

    private static boolean populated(Machine machine, ItemStack stack) {
        if (!stack.is(item(machine.name())) || stack.getCount() != 1 || !stack.hasTag()
            || EIODataComponents.REDSTONE_CONTROL.get(stack) != machine.redstone()
            || EIODataComponents.ENERGY.getOrDefault(stack, 0) != machine.energy()
            || !Objects.equals(EIODataComponents.ACTION_RANGE.get(stack), machine.range())) return false;
        var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(stack);
        return machine.fluid() == 0 || (fluid != null && fluid.getAmount() == machine.fluid());
    }

    private int machineSlot(Machine machine) {
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(item(machine.name()))) return slot;
        }
        return -1;
    }

    private void openWhenReady(Machine machine, String marker, BlockPos pos, Stage next) {
        if (!settled(machine, marker, pos)) return;
        minecraft.player.getInventory().selected = 8;
        check(machine.name() + "_" + marker + "_opens_natively", minecraft.player.getMainHandItem().isEmpty() && use(pos, Direction.SOUTH));
        stage = next;
    }

    private boolean settled(Machine machine, String marker, BlockPos pos) {
        var stand = Vec3.atBottomCenterOf(pos.south(2));
        var player = minecraft.player;
        if (!Files.exists(output.resolve("server-" + machine.name() + "-" + marker)) || minecraft.screen != null
            || player.distanceToSqr(stand) > 0.12) {
            settleBy = -1;
            return false;
        }
        if (settleBy < 0) settleBy = player.tickCount + 6;
        if (player.tickCount < settleBy) return false;
        settleBy = -1;
        return true;
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Machine Factory Resets", address, true), false);
        stage = Stage.JOIN;
    }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_machine_reset", result);
    }
}

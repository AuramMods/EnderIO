package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.PoweredSpawnerScreen;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerBlockEntity;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerMenu;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerMode;
import com.enderio.enderio.init.EIOBlocks;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientPoweredSpawnerSmoke {
    private enum Stage { TITLE, CONNECT, SETUP, GRID, RESULT, TAKE, PLACE_CRAFTED, PLACE_CARRIED, OPEN, MENU, FEED, FINISH, DONE }

    private static final BlockPos TABLE = new BlockPos(1304, 80, 11);
    private static final BlockPos CRAFTED = new BlockPos(1302, 80, 10);
    private static final BlockPos CARRIED = new BlockPos(1309, 80, 10);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int gridSlot;
    private int productSlot;

    ClientPoweredSpawnerSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage + " at grid slot " + gridSlot);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Powered Spawner test", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.SETUP;
                }
                case SETUP -> {
                    if (!near(1304.5, 13.5) || !cowBrokenSpawner() || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    check("migrated_broken_spawner_soul_syncs_to_client", cowBrokenSpawner());
                    use(new BlockHitResult(new Vec3(1304.5, 80.5, 12), Direction.SOUTH, TABLE, false));
                    stage = Stage.GRID;
                }
                case GRID -> {
                    if (!(minecraft.screen instanceof CraftingScreen)
                        || !(minecraft.player.containerMenu instanceof CraftingMenu menu)) return;
                    if (gridSlot > 0 && !menu.getSlot(gridSlot).hasItem()) return;
                    if (!minecraft.player.getInventory().getItem(gridSlot).isEmpty()) {
                        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, gridSlot + 1, gridSlot, ClickType.SWAP, minecraft.player);
                        if (++gridSlot == 9) stage = Stage.RESULT;
                    }
                }
                case RESULT -> {
                    var menu = (CraftingMenu) minecraft.player.containerMenu;
                    var stack = menu.getSlot(0).getItem();
                    if (stack.isEmpty()) return;
                    check("native_recipe_output_inherits_cow_soul", isSpawner(stack) && stack.getCount() == 1 && hasCowSoul(stack));
                    capture("powered-spawner-recipe");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (((CraftingMenu) minecraft.player.containerMenu).getSlot(0).hasItem()) return;
                    var inventory = minecraft.player.getInventory();
                    productSlot = 0;
                    while (productSlot < 9 && !isSpawner(inventory.getItem(productSlot))) productSlot++;
                    if (productSlot == 9) return;
                    check("quick_move_puts_bound_spawner_in_inventory", hasCowSoul(inventory.getItem(productSlot)));
                    minecraft.player.closeContainer();
                    signal("taken");
                    stage = Stage.PLACE_CRAFTED;
                }
                case PLACE_CRAFTED -> {
                    if (minecraft.screen != null || !near(1302.5, 13.5)) return;
                    place(CRAFTED, productSlot);
                    signal("placed-crafted");
                    stage = Stage.PLACE_CARRIED;
                }
                case PLACE_CARRIED -> {
                    if (!near(1309.5, 13.5) || !isSpawner(minecraft.player.getInventory().getItem(0)) || !hasPlacedCow(CRAFTED)) return;
                    check("crafted_placement_syncs_bound_cow", hasPlacedCow(CRAFTED));
                    check("carried_legacy_spawner_soul_syncs_to_client", hasCowSoul(minecraft.player.getInventory().getItem(0)));
                    place(CARRIED, 0);
                    signal("placed-carried");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!Files.exists(output.resolve("server-placed-carried")) || !hasPlacedCow(CARRIED)
                        || !minecraft.player.getMainHandItem().isEmpty()) return;
                    use(new BlockHitResult(new Vec3(1309.5, 80.5, 11), Direction.SOUTH, CARRIED, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof PoweredSpawnerScreen)
                        || !(minecraft.player.containerMenu instanceof PoweredSpawnerMenu menu)
                        || menu.getEnergyStorage() == null || !menu.isCapacitorInstalled() || menu.getMode() == null
                        || menu.getRedstoneControl() == null) return;
                    check("carried_machine_menu_syncs_legacy_charge_and_capacitor", menu.getEnergyStorage().energyStored() == 877
                        && menu.getMode() == PoweredSpawnerMode.SPAWN && menu.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE
                        && menu.getSlot(0).getItem().is(EIOItems.OCTADIC_CAPACITOR.get()));
                    capture("powered-spawner-placed");
                    menu.setMode(PoweredSpawnerMode.CAPTURE);
                    menu.setRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
                    signal("capture-mode");
                    stage = Stage.FEED;
                }
                case FEED -> {
                    if (!minecraft.player.getInventory().getItem(0).is(EIOItems.SOUL_VIAL.get())) return;
                    var menu = (PoweredSpawnerMenu) minecraft.player.containerMenu;
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 1, 0, ClickType.SWAP, minecraft.player);
                    signal("vial-inserted");
                    stage = Stage.FINISH;
                }
                case FINISH -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    var menu = (PoweredSpawnerMenu) minecraft.player.containerMenu;
                    if (!menu.getSlot(2).hasItem()) return;
                    check("capture_output_syncs_to_native_menu", menu.getMode() == PoweredSpawnerMode.CAPTURE
                        && menu.getSlot(2).getItem().is(EIOItems.SOUL_VIAL.get()) && hasCowSoul(menu.getSlot(2).getItem())
                        && !menu.getSlot(1).hasItem() && menu.getEnergyStorage().energyStored() == 0);
                    capture("powered-spawner-capture");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean near(double x, double z) {
        return minecraft.player.distanceToSqr(x, 80, z) < 0.1;
    }

    private boolean cowBrokenSpawner() {
        var stack = minecraft.player.getInventory().getItem(1);
        return stack.is(EIOItems.BROKEN_SPAWNER.get()) && hasCowSoul(stack);
    }

    private boolean hasPlacedCow(BlockPos pos) {
        return minecraft.level.getBlockEntity(pos) instanceof PoweredSpawnerBlockEntity spawner
            && spawner.getBoundSoul().entityType() == EntityType.COW;
    }

    private static boolean hasCowSoul(ItemStack stack) {
        return Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).entityType() == EntityType.COW;
    }

    private static boolean isSpawner(ItemStack stack) {
        return stack.is(EIOBlocks.POWERED_SPAWNER_ITEM.get());
    }

    private void place(BlockPos pos, int slot) {
        minecraft.player.getInventory().selected = slot;
        use(new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false));
    }

    private void use(BlockHitResult hit) {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_render_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_powered_spawner", result);
    }
}

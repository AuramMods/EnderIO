package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PaintingMachineScreen;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.block.entity.DoublePaintedBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientLegacyPaintingSmoke {
    private enum Stage { TITLE, CONNECT, REPLANT, OPEN, MENU, TAKE, PLACE, CAPTURE, DONE }

    private static final BlockPos MACHINE = new BlockPos(2607, 80, 10);
    private static final BlockPos SLAB = new BlockPos(2610, 80, 13);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private int captureTick = -1;

    ClientLegacyPaintingSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 140_000_000_000L) {
                throw new AssertionError("Legacy Painting Machine client did not complete stage " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO legacy Painting Machine", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.screen != null
                        || minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.REPLANT;
                }
                case REPLANT -> {
                    if (!marker("server-replant") || !positioned(2607.5, 13.5)
                        || !minecraft.player.getInventory().getItem(0).is(EIOBlocks.PAINTING_MACHINE.get().asItem())) return;
                    minecraft.player.getInventory().selected = 0;
                    place(MACHINE, "legacy_machine_places_in_survival");
                    signal("replanted");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!marker("server-menu") || !minecraft.level.getBlockState(MACHINE).is(EIOBlocks.PAINTING_MACHINE.get())) return;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(MACHINE).add(0, 0, 0.5), Direction.SOUTH, MACHINE, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof PaintingMachineScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getEnergyStorage() == null || menu.getCraftingProgress() == 0 || !paintedSlab(menu.getSlot(3).getItem(), 1)) return;
                    check("menu_shows_original_job_progress_charge_and_painted_output", menu.getCraftingProgress() == 0.25f
                        && menu.getEnergyStorage().energyStored() == 1800 && menu.isCapacitorInstalled()
                        && menu.getSlot(1).getItem().is(Items.OAK_SLAB) && menu.getSlot(1).getItem().getCount() == 1
                        && menu.getSlot(2).getItem().is(Items.DIAMOND_BLOCK) && menu.getSlot(2).getItem().getCount() == 2);
                    capture("legacy-painting-menu");
                    signal("menu");
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!marker("server-take") || !(minecraft.screen instanceof PaintingMachineScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!paintedSlab(menu.getSlot(3).getItem(), 2) || menu.getEnergyStorage().energyStored() != 0) return;
                    check("menu_shows_one_completed_job_and_two_matching_slabs", menu.getCraftingProgress() == 0
                        && !menu.getSlot(1).hasItem() && menu.getSlot(2).getItem().getCount() == 2);
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 3, 0, ClickType.QUICK_MOVE, minecraft.player);
                    minecraft.player.closeContainer();
                    signal("taken");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (!marker("server-slab") || !positioned(2610.5, 15.5)) return;
                    var inventory = minecraft.player.getInventory();
                    for (int slot = 0; slot < 9; slot++) {
                        if (!paintedSlab(inventory.getItem(slot), 2)) continue;
                        inventory.selected = slot;
                        place(SLAB, "painted_output_places_in_survival");
                        signal("slab");
                        stage = Stage.CAPTURE;
                        break;
                    }
                }
                case CAPTURE -> {
                    if (!marker("server-verified") || !(minecraft.level.getBlockEntity(SLAB) instanceof DoublePaintedBlockEntity slab)
                        || slab.getModelData().get(DoublePaintedBlockEntity.PAINT) != Blocks.DIAMOND_BLOCK) return;
                    if (captureTick == -1) captureTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - captureTick < 12) return;
                    check("one_painted_slab_remains_after_placement", paintedSlab(minecraft.player.getMainHandItem(), 1));
                    capture("legacy-painting-slab");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void place(BlockPos pos, String name) {
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        check(name, minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private static boolean paintedSlab(ItemStack stack, int count) {
        return stack.is(EIOBlocks.PAINTED_SLAB.get().asItem()) && stack.getCount() == count
            && BlockPaintData.of(Blocks.DIAMOND_BLOCK).equals(BlockPaintData.ITEM_DATA.get(stack));
    }

    private boolean positioned(double x, double z) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(x, 80, z) < 0.1;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_legacy_painting", result);
    }
}

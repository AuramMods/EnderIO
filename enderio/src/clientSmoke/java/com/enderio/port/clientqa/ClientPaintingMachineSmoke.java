package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PaintingMachineScreen;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.block.entity.SinglePaintedBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
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
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientPaintingMachineSmoke {
    private enum Stage { TITLE, CONNECT, READY, MENU, TAKE, PLACE, PAINTED, DONE }

    private static final BlockPos MACHINE = new BlockPos(2504, 80, 8);
    private static final BlockPos FENCE = new BlockPos(2504, 80, 13);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int paintedTick = -1;

    ClientPaintingMachineSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 125_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Painting Machine", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.screen != null
                        || minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.READY;
                }
                case READY -> {
                    if (!marker("server-menu") || !positioned(11.5)) return;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(MACHINE).add(0, 0, 0.5), Direction.SOUTH, MACHINE, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof PaintingMachineScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!diamondFence(menu.getSlot(3).getItem()) || menu.getEnergyStorage() == null || !menu.isCapacitorInstalled()) return;
                    check("menu_syncs_paid_job_template_and_reusable_paint", menu.getEnergyStorage().energyStored() == 0
                        && menu.getSlot(1).getItem().is(Items.OAK_FENCE) && menu.getSlot(1).getItem().getCount() == 1
                        && menu.getSlot(2).getItem().is(Items.DIAMOND_BLOCK) && menu.getSlot(2).getItem().getCount() == 1);
                    capture("painting-machine");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 3, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof PaintingMachineScreen screen) || screen.getMenu().getSlot(3).hasItem()) return;
                    check("quick_move_carries_one_painted_output", minecraft.player.getInventory().items.stream()
                        .filter(ClientPaintingMachineSmoke::diamondFence).count() == 1);
                    minecraft.player.closeContainer();
                    signal("taken");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (!marker("server-place") || !positioned(15.5)) return;
                    var inventory = minecraft.player.getInventory();
                    for (int slot = 0; slot < 9; slot++) {
                        if (!diamondFence(inventory.getItem(slot))) continue;
                        inventory.selected = slot;
                        check("normal_survival_placement", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atBottomCenterOf(FENCE), Direction.UP, FENCE.below(), false)).consumesAction());
                        signal("placed");
                        stage = Stage.PAINTED;
                        break;
                    }
                }
                case PAINTED -> {
                    if (!marker("server-paint-verified")
                        || !(minecraft.level.getBlockEntity(FENCE) instanceof SinglePaintedBlockEntity fence)
                        || fence.getPrimaryPaint().orElse(null) != Blocks.DIAMOND_BLOCK) return;
                    if (paintedTick == -1) paintedTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - paintedTick < 12) return;
                    check("placed_fence_keeps_exact_paint_on_client", minecraft.player.getMainHandItem().isEmpty()
                        && fence.getModelData().get(SinglePaintedBlockEntity.PAINT) == Blocks.DIAMOND_BLOCK);
                    capture("painted-diamond-fence");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static boolean diamondFence(ItemStack stack) {
        return stack.is(EIOBlocks.PAINTED_FENCE.get().asItem()) && stack.getCount() == 1
            && BlockPaintData.of(Blocks.DIAMOND_BLOCK).equals(EIODataComponents.BLOCK_PAINT.get(stack));
    }

    private boolean positioned(double z) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(MACHINE.getX() + 0.5, 80, z) < 0.1;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_painting_machine", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.FluidTankScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only the client is instrumented; the external server receives real packets/console fixture commands. */
final class ClientMultiplayerSmoke {
    private enum Stage { TITLE, CONNECTING, FIXTURE, MENU, FILL, UPDATE, WORLD, DISCONNECTED, RECONNECTED, VERIFY, DONE }
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean verifyOnly = System.getProperty("enderio.clientqa.multiplayerPhase").equals("verify");
    private final BlockPos tankPos = new BlockPos(0, 80, 0);
    private final Map<String, Object> checks = new LinkedHashMap<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleReady;
    private long stageStarted = started;
    private Stage stage = Stage.TITLE;

    ClientMultiplayerSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void next(Stage value) {
        stage = value;
        stageStarted = System.nanoTime();
        System.out.println("QA_EVT " + new Gson().toJson(Map.of("event", "multiplayer_stage", "status", "info", "stage", value)));
    }

    private void signal(String name) throws Exception {
        var directory = Path.of(System.getProperty("enderio.clientqa.output"), "signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, new Gson().toJson(Map.of("signal", name, "stage", stage.name())));
        Files.move(temporary, directory.resolve(name + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void check(String name, boolean condition) {
        checks.put(name, condition);
        if (!condition) throw new AssertionError(name);
        System.out.println("QA_EVT " + new Gson().toJson(Map.of("event", name, "status", "pass")));
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check(name + "_silent_hidden_render", Boolean.TRUE.equals(result.get("passed")));
    }

    private void connect() {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO local QA", address, true), false);
    }

    private boolean inWorld() {
        return minecraft.level != null && minecraft.player != null && minecraft.screen == null;
    }

    private void checkConnection(String name) {
        var connection = minecraft.getConnection().getConnection();
        check(name, minecraft.getSingleplayerServer() == null && !minecraft.isLocalServer()
            && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
        observations.put(name + "_remote", connection.getRemoteAddress().toString());
    }

    private void openTank() {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, tankPos, false));
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) {
                throw new AssertionError("Server disconnected during " + stage + "; see failure.png for the native reason");
            }
            if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Multiplayer scenario timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 4_000_000_000L) return;
                    capture("title");
                    next(Stage.CONNECTING);
                    connect();
                }
                case CONNECTING -> {
                    if (!inWorld()) return;
                    checkConnection("dedicated_tcp_connection");
                    signal("connected");
                    next(verifyOnly ? Stage.RECONNECTED : Stage.FIXTURE);
                }
                case FIXTURE -> {
                    if (!inWorld() || minecraft.player.distanceToSqr(0.5, 80, 4.5) > 1
                        || !minecraft.level.getBlockState(tankPos).is(EIOBlocks.FLUID_TANK.get())
                        || !minecraft.player.getInventory().getItem(9).is(Items.WATER_BUCKET)
                        || System.nanoTime() - stageStarted < 3_000_000_000L) return;
                    capture("world");
                    openTank();
                    next(Stage.MENU);
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof FluidTankScreen) || !(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank() == null || menu.getFluidTank().capacity() != 16000) return;
                    check("tcp_block_use_opens_tank_menu", menu.getFluidTank().contents().isEmpty());
                    var slot = menu.slots.stream().filter(s -> s.container == minecraft.player.getInventory()
                        && s.getContainerSlot() == 9 && s.getItem().is(Items.WATER_BUCKET)).findFirst().orElseThrow();
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, minecraft.player);
                    next(Stage.FILL);
                }
                case FILL -> {
                    if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank().contents().getAmount() != 1000 || !menu.getSlot(1).getItem().is(Items.BUCKET)) return;
                    check("tcp_inventory_packet_and_fluid_sync", menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().getCount() == 1
                        && minecraft.player.getInventory().items.stream().noneMatch(item -> item.is(Items.WATER_BUCKET)));
                    observations.put("processedFluidMb", menu.getFluidTank().contents().getAmount());
                    capture("tank-filled");
                    var widget = minecraft.screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    widget.mouseScrolled(widget.getX() + 8, widget.getY() + 8, -1);
                    signal("mode_changed");
                    next(Stage.UPDATE);
                }
                case UPDATE -> {
                    if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank().contents().getAmount() != 1500
                        || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                    check("tcp_server_update_reaches_menu", true);
                    capture("tank-updated");
                    minecraft.player.closeContainer();
                    next(Stage.WORLD);
                }
                case WORLD -> {
                    if (!inWorld() || System.nanoTime() - stageStarted < 1_000_000_000L
                        || !(minecraft.level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank)
                        || tank.getFluidTank().getFluidAmount() != 1500) return;
                    check("tcp_block_entity_update_reaches_renderer", true);
                    capture("world-filled");
                    next(Stage.DISCONNECTED);
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("client_disconnected", minecraft.level == null && minecraft.getConnection() == null);
                    signal("disconnected");
                }
                case DISCONNECTED -> {
                    if (!(minecraft.screen instanceof TitleScreen) || System.nanoTime() - stageStarted < 1_000_000_000L) return;
                    next(Stage.RECONNECTED);
                    connect();
                }
                case RECONNECTED -> {
                    if (!inWorld() || !minecraft.level.getBlockState(tankPos).is(EIOBlocks.FLUID_TANK.get())) return;
                    checkConnection(verifyOnly ? "tcp_after_server_restart" : "tcp_reconnected");
                    openTank();
                    next(Stage.VERIFY);
                }
                case VERIFY -> {
                    if (!(minecraft.screen instanceof FluidTankScreen) || !(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank() == null || menu.getFluidTank().contents().getAmount() != 1500
                        || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                    check("fresh_connection_restores_tank_state", menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().is(Items.BUCKET) && menu.getSlot(1).getItem().getCount() == 1
                        && minecraft.player.getInventory().items.stream().noneMatch(item -> item.is(Items.WATER_BUCKET))
                        && minecraft.level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity tank
                        && tank.getFluidTank().getFluidAmount() == 1500);
                    observations.put("restoredFluidMb", menu.getFluidTank().contents().getAmount());
                    observations.put("restoredBuckets", menu.getSlot(1).getItem().getCount());
                    observations.put("restoredRedstoneMode", menu.getRedstoneControl().name());
                    capture(verifyOnly ? "tank-after-restart" : "tank-reconnected");
                    signal("verified");
                    next(Stage.DONE);
                    ClientSmoke.complete("client_multiplayer", new LinkedHashMap<>(Map.of("passed", true,
                        "phase", verifyOnly ? "verify" : "exercise", "checks", checks, "observations", observations, "captures", captures)));
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", false);
            result.put("stage", stage.name());
            result.put("error", failure.toString());
            result.put("checks", checks);
            result.put("observations", observations);
            result.put("captures", captures);
            try { result.put("failureCapture", ClientSmoke.capture("failure.png")); } catch (Exception ignored) { }
            next(Stage.DONE);
            ClientSmoke.complete("client_multiplayer", result);
        }
    }
}

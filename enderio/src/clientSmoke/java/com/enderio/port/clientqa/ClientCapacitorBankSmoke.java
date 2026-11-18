package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.CapacitorBankScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankManager;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankMenu;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

final class ClientCapacitorBankSmoke {
    private enum Stage { TITLE, JOIN, MENU, BLOCKED, DONE }

    private static final BlockPos BANK = new BlockPos(1042, 80, 8);
    private static final Set<BlockPos> ARRAY = Set.of(new BlockPos(1039, 80, 8), new BlockPos(1040, 80, 8),
        new BlockPos(1041, 80, 8), BANK);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> capture = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientCapacitorBankSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 160_000_000_000L) throw new AssertionError("Timed out during " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local capacitor bank test", address, true), false);
                    stage = Stage.JOIN;
                }
                case JOIN -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (minecraft.player.distanceToSqr(1042.5, 80, 11.5) > 0.1
                        || !(minecraft.level.getBlockEntity(BANK) instanceof CapacitorBankBlockEntity bank)) return;
                    var data = CapacitorBankManager.getData(bank.getUuid());
                    if (data == null) return;
                    check("one_synchronized_node_per_block", data.nodes().size() == ARRAY.size()
                        && new HashSet<>(data.nodes()).equals(ARRAY));
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_connection", minecraft.getSingleplayerServer() == null
                        && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(1042.5, 80.5, 9), Direction.SOUTH, BANK, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof CapacitorBankScreen screen)
                        || !(minecraft.player.containerMenu instanceof CapacitorBankMenu menu)
                        || menu.getEnergyStorage().getLargeMaxEnergyStored() == 0) return;
                    check("menu_shows_exact_bank_charge_and_capacity", menu.getEnergyStorage().getLargeEnergyStored() == 6372000L
                        && menu.getEnergyStorage().getLargeMaxEnergyStored() == 7000000L
                        && menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE);
                    var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    check("redstone_picker_accepts_input", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, 1));
                    Files.createDirectories(output.resolve("signals"));
                    Files.writeString(output.resolve("signals/edited.json"), "{}");
                    stage = Stage.BLOCKED;
                }
                case BLOCKED -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    var menu = (CapacitorBankMenu) minecraft.player.containerMenu;
                    check("disabled_bank_keeps_synchronized_energy", menu.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE
                        && menu.getEnergyStorage().getLargeEnergyStored() == 6372000L);
                    capture.putAll(ClientSmoke.capture("capacitor-bank-menu.png"));
                    check("silent_hidden_bank_menu", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            finish(failure);
        }
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
        result.put("capture", capture);
        if (failure != null) {
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        ClientSmoke.complete("client_capacitor_bank", result);
    }
}

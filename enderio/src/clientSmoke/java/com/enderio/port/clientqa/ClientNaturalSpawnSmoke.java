package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** A real survival player keeps the vanilla natural spawn cycle active. */
final class ClientNaturalSpawnSmoke {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private long titleReady;
    private boolean connecting;
    private boolean connected;
    private boolean finished;

    ClientNaturalSpawnSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Dedicated server disconnected");
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out waiting for the natural spawn probe");
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO natural spawn test", address, true), false);
                connecting = true;
            } else if (!connected) {
                if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                var connection = minecraft.getConnection().getConnection();
                if (minecraft.player.isCreative() || minecraft.player.isSpectator() || minecraft.getSingleplayerServer() != null
                        || connection.isMemoryConnection() || !(connection.getRemoteAddress() instanceof InetSocketAddress)) {
                    throw new AssertionError("The spawn probe needs a real dedicated survival connection");
                }
                var signals = output.resolve("signals");
                Files.createDirectories(signals);
                Files.writeString(signals.resolve("connected.json"), "{}");
                connected = true;
            } else if (Files.exists(output.resolve("server-verified"))) {
                var frame = ClientSmoke.capture("natural-spawn-station.png");
                finished = true;
                ClientSmoke.complete("client_natural_spawns", new LinkedHashMap<>(Map.of(
                    "passed", frame.get("passed"), "dedicatedSurvivalTcp", true, "frame", frame)));
            }
        } catch (Throwable failure) {
            finished = true;
            failure.printStackTrace();
            ClientSmoke.complete("client_natural_spawns", new LinkedHashMap<>(Map.of("passed", false, "error", failure.toString())));
        }
    }
}

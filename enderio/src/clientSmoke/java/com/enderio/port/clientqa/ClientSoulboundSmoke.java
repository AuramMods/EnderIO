package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSoulboundSmoke {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private long titleReady;
    private boolean connecting;
    private boolean connected;
    private boolean dead;
    private boolean requestedRespawn;
    private boolean respawned;
    private boolean finished;

    ClientSoulboundSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 125_000_000_000L) {
                throw new AssertionError("Dedicated Soulbound session did not finish");
            }
            if (!connecting) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (!connected) {
                var connection = minecraft.getConnection().getConnection();
                check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                    && connection.getRemoteAddress() instanceof InetSocketAddress);
                signal("connected");
                connected = true;
            }
            if (player.isDeadOrDying()) {
                if (!dead) {
                    check("native_client_observes_player_death", player.getHealth() <= 0);
                    signal("dead");
                    dead = true;
                }
                if (marker("disconnect")) {
                    capture("before-respawn");
                    finish(null);
                } else if (marker("respawn") && !requestedRespawn) {
                    player.respawn();
                    requestedRespawn = true;
                }
            } else if (requestedRespawn && !respawned) {
                check("native_client_observes_survival_respawn", player.getHealth() > 0);
                signal("respawned");
                respawned = true;
            } else if (marker("verified")) {
                var enchantment = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "soulbound"));
                var itemCount = (enchantment == null ? 0 : player.getInventory().items.stream()
                    .filter(stack -> stack.getEnchantmentLevel(enchantment) == 1).count());
                check("all_three_saved_stacks_have_active_soulbound", itemCount == 3);
                capture("after-respawn");
                finish(null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Soulbound migration", address, true), false);
        connecting = true;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var capture = ClientSmoke.capture(name + ".png");
        captures.put(name, capture);
        check("silent_hidden_" + name, Boolean.TRUE.equals(capture.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        finished = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_legacy_soulbound", result);
    }
}

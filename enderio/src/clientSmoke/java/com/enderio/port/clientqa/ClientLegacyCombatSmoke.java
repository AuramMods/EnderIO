package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientLegacyCombatSmoke {
    private enum Stage { TITLE, JOIN, MAIN_HAND, OFF_HAND, VERIFIED, DONE }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientLegacyCombatSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 135_000_000_000L) {
                throw new AssertionError("Legacy combat client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO legacy combat", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.MAIN_HAND;
                }
                case MAIN_HAND -> {
                    if (!marker("main-hand") || !player.getMainHandItem().is(Items.IRON_SWORD)) return;
                    var target = nearbyCow("Withering target");
                    if (target == null) return;
                    attack(target, "main-hand-hit");
                    stage = Stage.OFF_HAND;
                }
                case OFF_HAND -> {
                    if (!marker("off-hand") || !player.getMainHandItem().is(Items.STONE_SWORD)
                        || !player.getOffhandItem().is(Items.IRON_SWORD)) return;
                    var target = nearbyCow("Offhand control");
                    if (target == null) return;
                    attack(target, "off-hand-hit");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!marker("verified")) return;
                    var enchantment = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "repellent"));
                    var armor = player.getItemBySlot(EquipmentSlot.CHEST);
                    check("original_armor_has_active_repellent", enchantment != null && armor.getEnchantmentLevel(enchantment) == 4);
                    var capture = ClientSmoke.capture("legacy-combat.png");
                    captures.put("legacy-combat", capture);
                    check("silent_hidden_combat", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private Cow nearbyCow(String name) {
        var player = minecraft.player;
        return minecraft.level.getEntitiesOfClass(Cow.class, player.getBoundingBox().inflate(3),
            cow -> player.distanceToSqr(cow) < 6.25 && cow.getName().getString().equals(name)).stream().findFirst().orElse(null);
    }

    private void attack(Cow target, String signalName) throws IOException {
        minecraft.gameMode.attack(minecraft.player, target);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        signal(signalName);
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        Path signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_legacy_combat", result);
    }
}

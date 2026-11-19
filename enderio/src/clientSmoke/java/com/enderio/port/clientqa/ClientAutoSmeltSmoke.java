package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientAutoSmeltSmoke {
    private enum Stage { TITLE, CONNECT, READY, MINING, PICKUP, DONE }

    private static final BlockPos ORE = new BlockPos(3730, 80, 8);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final int expectedScrap = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase")) ? 2 : 1;
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int miningTick;

    ClientAutoSmeltSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 125_000_000_000L) {
                throw new AssertionError("Auto Smelt client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Auto Smelt", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.screen != null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.READY;
                }
                case READY -> {
                    if (!Files.exists(output.resolve("server-mine")) || player.distanceToSqr(3730.5, 80, 10.5) > 0.1
                        || !minecraft.level.getBlockState(ORE).is(Blocks.ANCIENT_DEBRIS)
                        || !player.getMainHandItem().is(Items.DIAMOND_PICKAXE)) return;
                    var enchantment = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "auto_smelt"));
                    check("migrated_pickaxe_has_active_auto_smelt", enchantment != null && player.getMainHandItem().getEnchantmentLevel(enchantment) == 1);
                    check("native_survival_mining", minecraft.gameMode.startDestroyBlock(ORE, Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(ORE)) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(ORE, Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    if (!Files.exists(output.resolve("server-verified"))
                        || player.getInventory().countItem(Items.NETHERITE_SCRAP) != expectedScrap) return;
                    check("smelted_result_and_exact_tool_wear_sync", player.getInventory().countItem(Items.ANCIENT_DEBRIS) == 0
                        && player.getMainHandItem().getDamageValue() == 37 + expectedScrap);
                    var capture = ClientSmoke.capture("auto-smelt-result.png");
                    captures.put("auto-smelt-result", capture);
                    check("silent_hidden_mining", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
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
        ClientSmoke.complete("client_legacy_auto_smelt", result);
    }
}

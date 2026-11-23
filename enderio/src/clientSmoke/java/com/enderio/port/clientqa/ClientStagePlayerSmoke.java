package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientStagePlayerSmoke {
    private static final BlockPos PLACEMENT_BASE = new BlockPos(3848, 79, 5);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private long titleReady;
    private boolean joining;
    private boolean joined;
    private boolean done;
    private boolean retried;
    private boolean triedLockedPlacement;
    private boolean triedUnlockedPlacement;

    ClientStagePlayerSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            if (minecraft.screen instanceof DisconnectedScreen disconnected) {
                if (!joined && !retried) {
                    disconnected.onClose();
                    joining = false;
                    titleReady = 0;
                    retried = true;
                    return;
                }
                ClientSmoke.capture("stage-disconnected.png");
                throw new AssertionError("Disconnected while exercising GameStages");
            }
            if (!joining && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 3_000_000_000L) return;
                joining = true;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO GameStages QA", address, true), false);
                return;
            }
            if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
                return;
            }
            if (!joined) {
                if (minecraft.getConnection().getConnection().isMemoryConnection()) {
                    throw new AssertionError("GameStages QA requires a dedicated server");
                }
                signal("connected");
                joined = true;
            }
            if (minecraft.screen == null && minecraft.player.distanceToSqr(3848.5, 80, 8.5) < 0.25
                    && minecraft.player.getMainHandItem().is(EIOBlocks.ALLOY_SMELTER.asItem())) {
                if (!triedLockedPlacement && Files.exists(output.resolve("server-try-locked-placement"))) {
                    placeMachine("tried-locked-placement");
                    triedLockedPlacement = true;
                }
                if (!triedUnlockedPlacement && Files.exists(output.resolve("server-try-unlocked-placement"))) {
                    placeMachine("tried-unlocked-placement");
                    triedUnlockedPlacement = true;
                }
            }
            if (Files.exists(output.resolve("server-done"))) {
                done = true;
                var capture = ClientSmoke.capture("stage-player.png");
                var result = Map.of("passed", Boolean.TRUE.equals(capture.get("passed")),
                        "checks", Map.of("connected_to_dedicated_server", true), "captures", Map.of("stage-player", capture));
                ClientSmoke.complete("client_crafttweaker_stage_player", result);
            }
        } catch (Throwable failure) {
            done = true;
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", false);
            result.put("error", failure.toString());
            ClientSmoke.complete("client_crafttweaker_stage_player", result);
        }
    }

    private void placeMachine(String marker) throws Exception {
        var hit = new BlockHitResult(new Vec3(3848.5, 80, 5.5), Direction.UP, PLACEMENT_BASE, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        signal(marker);
    }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }
}

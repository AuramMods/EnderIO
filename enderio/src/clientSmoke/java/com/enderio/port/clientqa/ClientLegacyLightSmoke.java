package com.enderio.port.clientqa;

import com.enderio.enderio.content.misc_blocks.light.LegacyLightBlock;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientLegacyLightSmoke {
    private enum Stage { TITLE, WORLD, LIT, DARK, DONE }

    private static final BlockPos LIGHT = new BlockPos(1, 80, 1);
    private static final BlockPos NODE = LIGHT.above();
    private final Minecraft minecraft = Minecraft.getInstance();
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private CompletableFuture<?> pending;
    private long renderReadySince;
    private int readyFrames;

    ClientLegacyLightSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 190_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    checkModels();
                    String name = System.getProperty("enderio.clientqa.worldName");
                    var options = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(name, options,
                        new WorldOptions(56L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    var server = minecraft.getSingleplayerServer();
                    if (server == null || minecraft.player == null || minecraft.screen != null) return;
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-1, 79, -1), new BlockPos(3, 79, 6))) {
                            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(LIGHT, EIOBlocks.POWERED_LIGHT_WIRELESS.get().defaultBlockState()
                            .setValue(LegacyLightBlock.FACE, AttachFace.FLOOR));
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.connection.teleport(1.5, 80, 5.2, 180, 18);
                    });
                    stage = Stage.LIT;
                }
                case LIT -> {
                    if (!pending.isDone()) return;
                    pending.join();
                    var state = minecraft.level.getBlockState(LIGHT);
                    if (!state.is(EIOBlocks.POWERED_LIGHT_WIRELESS.get()) || !state.getValue(LegacyLightBlock.ENABLED)
                        || !minecraft.level.getBlockState(NODE).is(EIOBlocks.LIGHT_NODE.get())
                        || minecraft.level.getBrightness(LightLayer.BLOCK, NODE) != 15) return;
                    if (!meshRebuildSettled()) return;
                    capture("legacy-light-on");
                    check("wireless_node_and_block_light_synchronized", true);
                    var server = minecraft.getSingleplayerServer();
                    pending = server.submit(() -> server.overworld().setBlockAndUpdate(LIGHT.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()));
                    renderReadySince = 0;
                    readyFrames = 0;
                    stage = Stage.DARK;
                }
                case DARK -> {
                    if (!pending.isDone()) return;
                    pending.join();
                    var state = minecraft.level.getBlockState(LIGHT);
                    if (!state.is(EIOBlocks.POWERED_LIGHT_WIRELESS.get()) || state.getValue(LegacyLightBlock.ENABLED)
                        || !minecraft.level.isEmptyBlock(NODE) || minecraft.level.getBrightness(LightLayer.BLOCK, NODE) != 0) return;
                    if (!meshRebuildSettled()) return;
                    capture("legacy-light-off");
                    check("removal_and_darkening_synchronized", true);
                    stage = Stage.DONE;
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("integrated_world_stopped", minecraft.getSingleplayerServer() == null);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean meshRebuildSettled() {
        // Client block and light data arrive before the renderer replaces the chunk mesh.
        if (renderReadySince == 0) renderReadySince = System.nanoTime();
        return ++readyFrames >= 10 && System.nanoTime() - renderReadySince >= 750_000_000L;
    }

    private void checkModels() {
        var models = minecraft.getModelManager();
        for (var holder : List.of(EIOBlocks.LIGHT, EIOBlocks.LIGHT_INVERTED, EIOBlocks.POWERED_LIGHT,
            EIOBlocks.POWERED_LIGHT_INVERTED, EIOBlocks.POWERED_LIGHT_WIRELESS, EIOBlocks.POWERED_LIGHT_INVERTED_WIRELESS)) {
            var block = holder.get();
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = models.getBlockModelShaper().getBlockModel(state);
                if (model == models.getMissingModel() || model.getParticleIcon() == models.getMissingModel().getParticleIcon()) {
                    throw new AssertionError("Missing light model or texture for " + state);
                }
            }
            check("item_and_all_block_models_" + holder.getId().getPath(),
                minecraft.getItemRenderer().getModel(new ItemStack(block), null, null, 0) != models.getMissingModel());
        }
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check("silent_hidden_capture_" + name, Boolean.TRUE.equals(result.get("passed")));
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
        ClientSmoke.complete("client_legacy_lights", result);
    }
}

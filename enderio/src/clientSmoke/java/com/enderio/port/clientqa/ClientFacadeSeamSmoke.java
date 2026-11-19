package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.conduits.ConduitFacadeRendering;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientFacadeSeamSmoke {
    private enum Stage { TITLE, CONNECT, MATCHING, REPAINTED, RESTORED, GHOSTED, VISIBLE_AGAIN, DONE }

    private static final BlockPos WEST = new BlockPos(2703, 80, 9);
    private static final BlockPos EAST = WEST.east();
    private static final BlockPos DIFFERENT_WEST = WEST.south(10);
    private static final BlockPos DIFFERENT_EAST = EAST.south(10);
    private static final BlockPos ORDINARY_NEIGHBOR = WEST.south(15);
    private static final Block RED = Blocks.RED_STAINED_GLASS;
    private static final Block BLUE = Blocks.BLUE_STAINED_GLASS;
    // The stone floor already hides the bottom quad of every glass block.
    private static final int OPEN_VERTICES = 20;
    private static final int JOINED_VERTICES = 16;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean mixed;
    private final boolean embeddium;
    private Stage stage = Stage.TITLE;
    private int readyTick = -1;

    ClientFacadeSeamSmoke(boolean mixed, boolean embeddium) {
        this.mixed = mixed;
        this.embeddium = embeddium;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 135_000_000_000L) {
                throw new AssertionError("Facade seam client did not complete stage " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO facade seams", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("expected_chunk_renderer", ModList.get().isLoaded("embeddium") == embeddium);
                    signal("connected");
                    stage = Stage.MATCHING;
                }
                case MATCHING -> {
                    if (!marker("server-original") || minecraft.player.distanceToSqr(2700.5, 80, 12.5) > 0.1
                        || !matchingPaintIsLoaded(RED)
                        || !hasPaint(DIFFERENT_WEST, RED) || !hasPaint(DIFFERENT_EAST, BLUE)
                        || !hasPaint(ORDINARY_NEIGHBOR, RED) || !minecraft.level.getBlockState(ORDINARY_NEIGHBOR.east()).is(RED)) return;
                    if (!waitForMesh(true)) return;
                    int[] matching = pairVertices();
                    int[] different = {vertices(DIFFERENT_WEST), vertices(DIFFERENT_EAST)};
                    int ordinary = vertices(ORDINARY_NEIGHBOR);
                    var frame = capture("matching-facades");
                    frame.put("matchingVertices", matching);
                    frame.put("differentVertices", different);
                    frame.put("besideVanillaGlassVertices", ordinary);
                    check("different_glass_retains_both_shared_faces", sameCount(different, OPEN_VERTICES));
                    check("facade_culls_its_face_next_to_matching_vanilla_glass", ordinary == JOINED_VERTICES);
                    checkVanillaFace("matching", false);
                    check("matching_facades_cull_each_face_across_a_chunk_boundary", sameCount(matching, JOINED_VERTICES));
                    signal("original");
                    stage = Stage.REPAINTED;
                }
                case REPAINTED -> {
                    if (!marker("server-repainted") || !matchingPaintIsLoaded(BLUE) || !waitForMesh(true)) return;
                    checkPair("repainted-facades", OPEN_VERTICES, OPEN_VERTICES);
                    checkVanillaFace("repainted", true);
                    signal("repainted");
                    stage = Stage.RESTORED;
                }
                case RESTORED -> {
                    if (!marker("server-restored") || !matchingPaintIsLoaded(RED) || !waitForMesh(true)) return;
                    if (!minecraft.player.getInventory().getItem(8).is(EIOItems.YETA_WRENCH.get())) return;
                    checkPair("restored-facades", JOINED_VERTICES, JOINED_VERTICES);
                    checkVanillaFace("restored", false);
                    minecraft.player.getInventory().selected = 8;
                    stage = Stage.GHOSTED;
                }
                case GHOSTED -> {
                    if (!waitForMesh(false)) return;
                    checkPair("ghosted-facades", JOINED_VERTICES, mixed ? OPEN_VERTICES : JOINED_VERTICES);
                    if (mixed) {
                        checkVanillaFace("ghosted", true);
                        minecraft.player.getInventory().selected = 0;
                        stage = Stage.VISIBLE_AGAIN;
                    } else {
                        finish(null);
                    }
                }
                case VISIBLE_AGAIN -> {
                    if (!waitForMesh(true)) return;
                    checkPair("facades-visible-again", JOINED_VERTICES, JOINED_VERTICES);
                    checkVanillaFace("visible_again", false);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean matchingPaintIsLoaded(Block paint) {
        return mixed ? hasPaint(WEST, paint) && minecraft.level.getBlockState(EAST).is(RED)
            : hasPaint(WEST, RED) && hasPaint(EAST, paint);
    }

    private void checkPair(String name, int westVertices, int eastVertices) throws IOException {
        int[] counts = pairVertices();
        capture(name).put("vertices", counts);
        check(name + "_both_expected_meshes", counts[0] == westVertices && counts[1] == eastVertices);
    }

    private int[] pairVertices() {
        return new int[] {vertices(WEST), mixed ? vanillaVertices() : vertices(EAST)};
    }

    private void checkVanillaFace(String phase, boolean visible) {
        if (!mixed) return;
        var level = minecraft.level;
        var state = level.getBlockState(EAST);
        check("forge_vanilla_face_" + phase, Block.shouldRenderFace(state, level, EAST, Direction.WEST, WEST) == visible);
        if (embeddium) {
            check("embeddium_vanilla_face_" + phase, EmbeddiumProbe.shouldDrawSide(state, level, EAST, Direction.WEST) == visible);
        }
    }

    private static boolean sameCount(int[] counts, int expected) {
        return Arrays.stream(counts).allMatch(count -> count == expected);
    }

    private int vertices(BlockPos pos) {
        var bundle = (ConduitBundleBlockEntity) minecraft.level.getBlockEntity(pos);
        var facades = ConduitFacadeRendering.capture(Map.of(pos, bundle.getModelData()), SectionPos.of(pos));
        var buffer = new BufferBuilder(1024);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        facades.render(minecraft.level, new PoseStack(), layer -> {
            check("stained_glass_uses_the_translucent_layer", layer == RenderType.translucent());
            return buffer;
        });
        return finishVertices(buffer);
    }

    private int vanillaVertices() {
        var level = minecraft.level;
        var state = level.getBlockState(EAST);
        var renderer = minecraft.getBlockRenderer();
        var model = renderer.getBlockModel(state);
        var buffer = new BufferBuilder(1024);
        var random = RandomSource.create(42);
        var data = model.getModelData(level, EAST, state, ModelData.EMPTY);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        renderer.getModelRenderer().tesselateBlock(level, model, state, EAST, new PoseStack(), buffer,
            true, random, 42, OverlayTexture.NO_OVERLAY, data, RenderType.translucent());
        return finishVertices(buffer);
    }

    private static int finishVertices(BufferBuilder buffer) {
        var drawn = buffer.end();
        int count = drawn.drawState().vertexCount();
        drawn.release();
        return count;
    }

    private static final class EmbeddiumProbe {
        static boolean shouldDrawSide(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
            return new me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache()
                .shouldDrawSide(state, level, pos, side);
        }
    }

    private boolean hasPaint(BlockPos pos, Block paint) {
        return minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
            && bundle.hasFacade() && bundle.getFacadeBlock() == paint;
    }

    private boolean waitForMesh(boolean visible) {
        if (ClientFacadeVisibility.areFacadesVisible() != visible) return false;
        if (readyTick == -1) {
            readyTick = minecraft.player.tickCount;
            minecraft.gui.getChat().clearMessages(false);
        }
        if (minecraft.player.tickCount - readyTick < 30) return false;
        readyTick = -1;
        return true;
    }

    private Map<String, Object> capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
        return frame;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_facade_seams", result);
    }
}

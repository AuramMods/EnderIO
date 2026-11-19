package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleRenderState;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientMEColorsSmoke {
    private enum Stage { TITLE, JOIN, MATCHED, MIXED, PLACE, UPDATED, DONE }

    private static final ResourceKey<Conduit<?, ?>> RED = conduit("red_me");
    private static final ResourceKey<Conduit<?, ?>> BLUE = conduit("blue_me");
    private static final ResourceLocation RED_TEXTURE = ResourceLocation.parse("minecraft:block/red_wool");
    private static final ResourceLocation BLUE_TEXTURE = ResourceLocation.parse("minecraft:block/blue_wool");
    private static final int MATCHED_ROUTE = 4;
    private static final int MIXED_ROUTE = 8;
    private static final int HEAD_X = 1119;
    private static final int TAIL_X = 1120;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int nextCaptureTick;

    ClientMEColorsSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private static ResourceKey<Conduit<?, ?>> conduit(String name) {
        return ResourceKey.create(EnderIORegistries.Keys.CONDUIT, ResourceLocation.parse("enderio_modded_conduits_tests:" + name));
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 130_000_000_000L) {
                throw new AssertionError("ME color scenario stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("ME color QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && minecraft.getSingleplayerServer() == null
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    var registry = minecraft.level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT);
                    check("server_registry_syncs_both_authored_conduits",
                        registry.getHolderOrThrow(RED).value().texture().equals(RED_TEXTURE)
                            && registry.getHolderOrThrow(BLUE).value().texture().equals(BLUE_TEXTURE));
                    signal("connected");
                    stage = Stage.MATCHED;
                }
                case MATCHED -> {
                    if (!ready("server-matched") || !positioned(MATCHED_ROUTE)
                        || minecraft.player.getInventory().getItem(1).isEmpty() || !settled()) return;
                    verifyInventory(0, RED, RED_TEXTURE, "Test Red ME Conduit");
                    verifyInventory(1, BLUE, BLUE_TEXTURE, "Test Blue ME Conduit");
                    verifyRoute(MATCHED_ROUTE, true, "matched");
                    signal("matched");
                    stage = Stage.MIXED;
                }
                case MIXED -> {
                    if (!ready("server-mixed") || !positioned(MIXED_ROUTE) || !settled()) return;
                    verifyRoute(MIXED_ROUTE, false, "mixed");
                    signal("mixed");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    var tail = new BlockPos(TAIL_X, 80, MIXED_ROUTE);
                    if (!ready("server-place") || !minecraft.level.isEmptyBlock(tail)) return;
                    minecraft.player.getInventory().selected = 0;
                    var hit = new BlockHitResult(Vec3.atBottomCenterOf(tail), Direction.UP, tail.below(), false);
                    check("survival_places_the_datapack_red_variant",
                        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.UPDATED;
                }
                case UPDATED -> {
                    if (!ready("server-updated") || !minecraft.player.getInventory().getItem(0).isEmpty() || !settled()) return;
                    verifyRoute(MIXED_ROUTE, true, "updated");
                    signal("updated");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void verifyInventory(int slot, ResourceKey<Conduit<?, ?>> key, ResourceLocation texture, String name) {
        var stack = minecraft.player.getInventory().getItem(slot);
        var conduit = EnderIODataComponents.CONDUIT.get(stack, minecraft.level.registryAccess());
        check(key.location().getPath() + "_stack_uses_synced_holder_and_name",
            stack.getCount() == 1 && conduit != null && conduit.is(key) && stack.getHoverName().getString().equals(name));
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
        check(key.location().getPath() + "_inventory_model_bakes_authored_sprite",
            model != minecraft.getModelManager().getMissingModel() && model.getParticleIcon().contents().name().equals(texture)
                && model.getQuads(null, null, RandomSource.create(42)).stream().anyMatch(quad -> usesTexture(quad, texture)));
    }

    private void verifyRoute(int route, boolean linked, String label) throws IOException {
        verifyBundle(new BlockPos(HEAD_X, 80, route), RED, RED_TEXTURE, Direction.EAST, linked, label + "_head");
        verifyBundle(new BlockPos(TAIL_X, 80, route), linked ? RED : BLUE, linked ? RED_TEXTURE : BLUE_TEXTURE,
            Direction.WEST, linked, label + "_tail");
        var frame = ClientSmoke.capture("me-colors-" + label + ".png");
        int redPixels = redSeamPixels();
        frame.put("redSeamPixels", redPixels);
        captures.put(label, frame);
        check(label + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
        check(label + "_frame_shows_expected_seam", linked ? redPixels > 35 : redPixels < 12);
    }

    private void verifyBundle(BlockPos pos, ResourceKey<Conduit<?, ?>> key, ResourceLocation texture,
        Direction seamSide, boolean linked, String label) {
        if (!(minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle)) {
            throw new AssertionError("No client conduit bundle at " + pos);
        }
        var data = bundle.getModelData();
        var renderState = data.get(ConduitBundleRenderState.PROPERTY);
        check(label + "_syncs_holder_and_connection",
            renderState != null && renderState.conduits().size() == 1 && renderState.conduits().get(0).is(key)
                && renderState.getConnectedConduits(seamSide).stream().anyMatch(conduit -> conduit.is(key)) == linked);

        var state = minecraft.level.getBlockState(pos);
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(42), data, RenderType.cutout()));
        for (var direction : Direction.values()) {
            quads.addAll(model.getQuads(state, direction, RandomSource.create(42), data, RenderType.cutout()));
        }
        var authored = quads.stream().filter(quad -> usesTexture(quad, texture)).toList();
        float edge = seamSide == Direction.EAST ? 1 : 0;
        check(label + "_bakes_authored_sprite_and_expected_seam_geometry",
            !authored.isEmpty() && authored.stream().anyMatch(quad -> touchesX(quad, edge)) == linked);
    }

    private static boolean usesTexture(BakedQuad quad, ResourceLocation texture) {
        return quad.getSprite().contents().name().equals(texture);
    }

    private static boolean touchesX(BakedQuad quad, float edge) {
        var vertices = quad.getVertices();
        for (int i = 0; i < vertices.length; i += IQuadTransformer.STRIDE) {
            if (Math.abs(Float.intBitsToFloat(vertices[i + IQuadTransformer.POSITION]) - edge) < 0.0001F) return true;
        }
        return false;
    }

    private int redSeamPixels() {
        int count = 0;
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (int y = image.getHeight() * 43 / 100; y < image.getHeight() * 57 / 100; y++) {
                for (int x = image.getWidth() * 47 / 100; x < image.getWidth() * 53 / 100; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = pixel & 255;
                    int green = pixel >>> 8 & 255;
                    int blue = pixel >>> 16 & 255;
                    if (red - green > 35 && red - blue > 28) count++;
                }
            }
        }
        return count;
    }

    private boolean positioned(int route) {
        return minecraft.player.distanceToSqr(1120, 80, route + 3) < 0.025;
    }

    private boolean settled() {
        int tick = minecraft.player.tickCount;
        if (nextCaptureTick == 0) nextCaptureTick = tick + 25;
        if (tick < nextCaptureTick) return false;
        nextCaptureTick = 0;
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
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
        ClientSmoke.complete("client_me_colors", result);
    }
}

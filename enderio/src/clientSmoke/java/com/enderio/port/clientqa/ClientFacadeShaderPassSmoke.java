package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Reads authored translucent terrain and the shadow map from the same rendered frame. */
final class ClientFacadeShaderPassSmoke {
    private record Phase(String name, Block paint, boolean wrench) {}
    private record Markers(int red, int blue, int lime) {
        boolean matches(Phase phase) {
            return red > 8 && (!phase.wrench() && phase.paint() == Blocks.BLUE_STAINED_GLASS ? blue > 8 : blue == 0)
                && (!phase.wrench() && phase.paint() == Blocks.LIME_STAINED_GLASS ? lime > 8 : lime == 0);
        }
    }

    private static final BlockPos FACADE = new BlockPos(2751, 80, 9);
    private static final BlockPos CONTROL = new BlockPos(2754, 80, 9);
    private static final List<Phase> PHASES = List.of(
        new Phase("visible", Blocks.BLUE_STAINED_GLASS, false), new Phase("repainted", Blocks.LIME_STAINED_GLASS, false),
        new Phase("restored", Blocks.BLUE_STAINED_GLASS, false), new Phase("wrench", Blocks.BLUE_STAINED_GLASS, true),
        new Phase("revealed", Blocks.BLUE_STAINED_GLASS, false), new Phase("reloaded", Blocks.BLUE_STAINED_GLASS, false));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private boolean connecting;
    private boolean joined;
    private boolean done;
    private long titleAt;
    private int phaseIndex;
    private int readyTick = -1;
    private CompletableFuture<Void> reload;
    private Markers initialTerrain;
    private Markers initialShadow;
    private int[] initialBackdrop;

    ClientFacadeShaderPassSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            var phase = PHASES.get(phaseIndex);
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Shader pass capture stopped at " + phase.name());
            }
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                var address = System.getProperty("enderio.clientqa.server");
                connecting = true;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO shader passes", address, true), false);
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            if (!joined) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                check("expected_oculus_installation", ModList.get().isLoaded("oculus") && ModList.get().isLoaded("embeddium"));
                check("expected_athena_installation", ModList.get().isLoaded("athena"));
                minecraft.options.hideGui = true;
                joined = true;
                signal("connected");
            }
            if (!ready(phase)) return;
            if (phase.name().equals("reloaded")) {
                if (reload == null) {
                    readyTick = -1;
                    reload = minecraft.reloadResourcePacks();
                    return;
                }
                if (!reload.isDone() || minecraft.getOverlay() != null) return;
                reload.join();
            }
            observe(phase);
            signal(phase.name());
            readyTick = -1;
            if (reopened || ++phaseIndex == PHASES.size()) finish(null);
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean ready(Phase phase) {
        var player = minecraft.player;
        if (!Files.exists(output.resolve("server-" + phase.name())) || player.distanceToSqr(2752.5, 80, 15.5) > 0.1
            || player.getMainHandItem().is(EIOItems.YETA_WRENCH.get()) != phase.wrench()
            || ClientFacadeVisibility.areFacadesVisible() == phase.wrench()
            || !minecraft.level.getBlockState(CONTROL).is(Blocks.RED_STAINED_GLASS)
            || !(minecraft.level.getBlockEntity(FACADE) instanceof ConduitBundleBlockEntity bundle)
            || !bundle.hasFacade() || bundle.getFacadeBlock() != phase.paint()) return false;
        if (readyTick == -1) readyTick = player.tickCount;
        return player.tickCount - readyTick >= 30;
    }

    private void observe(Phase phase) throws IOException {
        String name = phase.name();
        var frame = ClientSmoke.capture(name + ".png", 50);
        captures.put(name, frame);
        check("hidden_muted_" + name, Boolean.TRUE.equals(frame.get("passed")));
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        int width = image.getWidth();
        int height = image.getHeight();
        int marker = image.getRGB(16, height - 16) & 0xFFFFFF;
        frame.put("finalPassMarker", String.format("%06x", marker));
        check("active_shader_final_pass_" + name, IrisApi.getInstance().isShaderPackInUse() && marker == 0xFF00FF);

        var terrain = markers(image, width / 5, height * 53 / 100, width * 4 / 5, height * 56 / 100 + 1);
        var shadow = markers(image, width - 256, 0, width, 256);
        // Sample the plain tinted glass above the marker strip, to the left of the exposed conduit.
        int backdropX = width * 403 / 1000;
        int backdropY = height * 454 / 1000;
        int backdropWidth = width * 421 / 1000 - backdropX;
        int backdropHeight = height * 517 / 1000 - backdropY;
        int[] backdrop = image.getRGB(backdropX, backdropY, backdropWidth, backdropHeight, null, 0, backdropWidth);
        frame.put("translucentStainedGlassMarkers", terrain);
        frame.put("shadowMapStainedGlassMarkers", shadow);
        check("authored_translucent_terrain_has_exact_paints_" + name, terrain.matches(phase));
        check("authored_shadow_pass_has_exact_paints_" + name, shadow.matches(phase));
        var painted = phase.paint().defaultBlockState();
        check("facade_paint_uses_native_translucent_layer_" + name, minecraft.getBlockRenderer().getBlockModel(painted)
            .getRenderTypes(painted, RandomSource.create(42), ModelData.EMPTY).contains(RenderType.translucent()));

        if (name.equals("visible")) {
            initialTerrain = terrain;
            initialShadow = shadow;
            initialBackdrop = backdrop;
        } else {
            check("ordinary_glass_keeps_its_terrain_and_shadow_" + name,
                terrain.red() == initialTerrain.red() && shadow.red() == initialShadow.red());
            if (!phase.wrench() && phase.paint() == Blocks.BLUE_STAINED_GLASS) {
                check("restored_paint_matches_initial_terrain_and_shadow_" + name,
                    terrain.equals(initialTerrain) && shadow.equals(initialShadow));
                check("restored_paint_matches_initial_blended_pixels_" + name, Arrays.equals(initialBackdrop, backdrop));
            }
            if (phase.wrench()) verifyBlendedBackdrop(frame, backdrop, backdropWidth);
        }
    }

    private void verifyBlendedBackdrop(Map<String, Object> frame, int[] exposed, int width) {
        int stoneEdges = 0;
        int visibleThroughGlass = 0;
        for (int y = 0; y < exposed.length; y += width) {
            for (int x = y; x < y + width - 1; x++) {
                int plain = exposed[x] & 255;
                int next = exposed[x + 1] & 255;
                if ((exposed[x] & 0xFFFFFF) != plain * 0x010101 || (exposed[x + 1] & 0xFFFFFF) != next * 0x010101
                    || Math.abs(plain - next) < 4) continue;
                stoneEdges++;
                int painted = initialBackdrop[x];
                int paintedNext = initialBackdrop[x + 1];
                if (hasBlueTint(painted) && hasBlueTint(paintedNext)
                    && (plain - next) * (brightness(painted) - brightness(paintedNext)) > 0) visibleThroughGlass++;
            }
        }
        frame.put("backdropStoneEdges", stoneEdges);
        frame.put("backdropStoneEdgesVisibleThroughBlueGlass", visibleThroughGlass);
        check("translucent_facade_blends_its_tint_with_real_stone_texture",
            stoneEdges >= 25 && visibleThroughGlass * 100 >= stoneEdges * 95);
    }

    private static boolean hasBlueTint(int color) {
        return (color & 255) - Math.max(color >> 16 & 255, color >> 8 & 255) > 5;
    }

    private static int brightness(int color) {
        return (color >> 16 & 255) + (color >> 8 & 255) + (color & 255);
    }

    private static Markers markers(BufferedImage image, int x0, int y0, int x1, int y1) {
        int red = 0;
        int blue = 0;
        int lime = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                switch (image.getRGB(x, y) & 0xFFFFFF) {
                    case 0xFF0033 -> red++;
                    case 0x0033FF -> blue++;
                    case 0x33FF00 -> lime++;
                    default -> {}
                }
            }
        }
        return new Markers(red, blue, lime);
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
    }

    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean::booleanValue));
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_facade_shader_passes", result);
    }
}

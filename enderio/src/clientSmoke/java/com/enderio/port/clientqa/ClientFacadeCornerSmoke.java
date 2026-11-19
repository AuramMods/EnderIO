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
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Measures the live baked and drawn corners in two perpendicular fused-quartz panels. */
final class ClientFacadeCornerSmoke {
    private static final BlockPos FACADE = new BlockPos(2736, 80, 16);
    private static final String PREFIX = "enderio:block/ctm/fused_quartz/";
    private static final String PACK = "mod/enderio:resourcepacks/athena";
    private static final List<String> PHASES = List.of("matching", "wrench", "revealed", "repainted", "restored", "reloaded");

    private enum View {
        SOUTH(Direction.SOUTH, new BlockPos(2735, 79, 16), new Vec3(2736, 79, 21.5)),
        EAST(Direction.EAST, new BlockPos(2736, 79, 15), new Vec3(2741.5, 79, 16));

        final Direction face;
        final BlockPos corner;
        final Vec3 camera;

        View(Direction face, BlockPos corner, Vec3 camera) {
            this.face = face;
            this.corner = corner;
            this.camera = camera;
        }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final boolean athena;
    private final boolean shaders;
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<View, int[]> matching = new EnumMap<>(View.class);
    private CompletableFuture<Void> reload;
    private boolean connecting;
    private boolean joined;
    private boolean done;
    private int phaseIndex;
    private int viewIndex;
    private int readyTick = -1;

    ClientFacadeCornerSmoke(boolean athena, boolean shaders) {
        this.athena = athena;
        this.shaders = shaders;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Corner textures did not complete " + PHASES.get(phaseIndex) + " view " + viewIndex);
            }
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                var address = System.getProperty("enderio.clientqa.server");
                connecting = true;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO quartz corners", address, true), false);
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            if (!joined) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                check("expected_athena_installation", ModList.get().isLoaded("athena") == athena
                    && ModList.get().isLoaded("embeddium") == shaders);
                check("expected_oculus_installation", ModList.get().isLoaded("oculus") == shaders);
                check("expected_quartz_blockstate_pack", quartzPackMatches());
                minecraft.options.hideGui = true;
                joined = true;
                signal("connected");
            }
            var phase = PHASES.get(phaseIndex);
            var view = View.values()[viewIndex];
            var name = phase + "-" + view.face.getName();
            boolean wrench = phase.equals("wrench");
            boolean split = phase.equals("repainted") || (wrench && shaders);
            var paint = block(phase.equals("repainted") ? "fused_quartz_red" : "fused_quartz_blue");
            if (!ready(name, view, paint, wrench)) return;
            if (phase.equals("reloaded")) {
                if (reload == null) {
                    readyTick = -1;
                    reload = minecraft.reloadResourcePacks();
                    return;
                }
                if (!reload.isDone() || minecraft.getOverlay() != null) return;
                reload.join();
                check("quartz_pack_still_active_after_reload", quartzPackMatches());
            }
            observe(name, phase, view, split);
            signal(name);
            readyTick = -1;
            if (++viewIndex == View.values().length) {
                viewIndex = 0;
                if (reopened || ++phaseIndex == PHASES.size()) finish(null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean ready(String name, View view, Block paint, boolean wrench) {
        var player = minecraft.player;
        if (!Files.exists(output.resolve("server-" + name)) || player.distanceToSqr(view.camera) > 0.1
            || player.getMainHandItem().is(EIOItems.YETA_WRENCH.get()) != wrench
            || ClientFacadeVisibility.areFacadesVisible() == wrench
            || !minecraft.level.getBlockState(view.corner).is(block("fused_quartz_blue"))
            || !(minecraft.level.getBlockEntity(FACADE) instanceof ConduitBundleBlockEntity bundle)
            || !bundle.hasFacade() || bundle.getFacadeBlock() != paint) return false;
        if (readyTick == -1) readyTick = player.tickCount;
        return player.tickCount - readyTick >= 30;
    }

    private void observe(String name, String phase, View view, boolean split) throws IOException {
        // The HUD-free stone and glass scene uses a limited color palette.
        var frame = ClientSmoke.capture(name + ".png", 50);
        captures.put(name, frame);
        check("hidden_muted_" + name, Boolean.TRUE.equals(frame.get("passed")));
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        if (shaders) {
            int marker = image.getRGB(16, image.getHeight() - 16) & 0xFFFFFF;
            frame.put("shaderSignature", String.format("%06x", marker));
            check("shader_drew_" + name, IrisApi.getInstance().isShaderPackInUse() && marker == 0xFF00FF);
        }

        var state = minecraft.level.getBlockState(view.corner);
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var modelData = model.getModelData(minecraft.level, view.corner, state, ModelData.EMPTY);
        var sprites = model.getQuads(state, view.face, RandomSource.create(42), modelData, RenderType.cutout()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
        frame.put("ordinaryQuartzFaceSprites", sprites);
        List<String> expected = athena ? List.of(PREFIX + (split ? "center" : "empty"),
            PREFIX + "horizontal", PREFIX + "particle", PREFIX + "vertical") : List.of("enderio:block/fused_quartz");
        check("quartz_inner_corner_model_" + name, sprites.equals(expected));

        int[] corner = cornerPixels(image, view);
        if (phase.equals("matching")) {
            var origin = SectionPos.of(FACADE);
            var target = SectionPos.of(view.corner);
            int axes = Math.abs(origin.x() - target.x()) + Math.abs(origin.y() - target.y()) + Math.abs(origin.z() - target.z());
            check("corner_is_two_section_offsets_from_facade_" + name, axes == 2);
            matching.put(view, corner);
        } else {
            var initial = matching.get(view);
            int changed = 0;
            for (int i = 0; i < corner.length; i++) {
                if (corner[i] != initial[i]) changed++;
            }
            frame.put("changedCornerPixels", changed);
            check("quartz_inner_corner_pixels_" + name, athena && split ? changed > 10 : changed == 0);
        }
    }

    private static int[] cornerPixels(BufferedImage image, View view) {
        int width = image.getWidth() / 25;
        int height = image.getHeight() / 14;
        int x = image.getWidth() / 2 + (view == View.SOUTH ? -width : 0);
        int y = image.getHeight() / 2;
        return image.getRGB(x, y, width, height, null, 0, width);
    }

    private boolean quartzPackMatches() {
        var file = ResourceLocation.parse("enderio:blockstates/fused_quartz_blue.json");
        return PACK.equals(minecraft.getResourceManager().getResource(file).orElseThrow().sourcePackId()) == athena;
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("enderio:" + id));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
    }

    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_facade_corners", result);
    }
}

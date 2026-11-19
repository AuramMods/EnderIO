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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientFacadeCtmSmoke {
    enum Renderer { CORE, ATHENA, EMBEDDIUM, OCULUS, OCULUS_SHADER, OCULUS_TERRAIN }

    private enum Stage { TITLE, CONNECT, MATCHED, SPLIT, REMATCHED, WRENCH, RESTORED, RELOADED, DONE }

    private static final BlockPos WEST = new BlockPos(2735, 80, 9);
    private static final BlockPos CENTER = WEST.east();
    private static final BlockPos EAST = CENTER.east();
    private static final ResourceLocation BLUE_ID = ResourceLocation.fromNamespaceAndPath("enderio", "clear_glass_blue");
    private static final ResourceLocation RED_ID = ResourceLocation.fromNamespaceAndPath("enderio", "clear_glass_red");
    private static final String PACK = "mod/enderio:resourcepacks/athena";
    private static final String GLASS = "enderio:block/clear_glass";
    private static final String CTM = "enderio:block/ctm/clear_glass/";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final boolean athena;
    private final boolean embeddium;
    private final boolean oculus;
    private final boolean shaders;
    private final boolean terrainPrograms;
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private int readyTick = -1;
    private CompletableFuture<Void> reload;
    private Map<String, List<String>> initial;
    private int[][] initialJoins;

    ClientFacadeCtmSmoke(Renderer renderer) {
        athena = renderer != Renderer.CORE;
        terrainPrograms = renderer == Renderer.OCULUS_TERRAIN;
        shaders = renderer == Renderer.OCULUS_SHADER || terrainPrograms;
        oculus = renderer == Renderer.OCULUS || shaders;
        embeddium = renderer == Renderer.EMBEDDIUM || oculus;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Connected textures did not complete stage " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO connected facade textures", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            var blue = BuiltInRegistries.BLOCK.get(BLUE_ID);
            var red = BuiltInRegistries.BLOCK.get(RED_ID);
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("expected_renderer_and_pack", ModList.get().isLoaded("athena") == athena
                        && ModList.get().isLoaded("embeddium") == embeddium && hasExpectedGlassBlockState());
                    check("expected_oculus_installation", ModList.get().isLoaded("oculus") == oculus);
                    signal("connected");
                    stage = Stage.MATCHED;
                }
                case MATCHED -> {
                    if (minecraft.player.distanceToSqr(2736.5, 80, 14.5) > 0.1 || !ready("server-matched", blue, blue)) return;
                    initial = observe("connected-facades", blue, blue);
                    List<String> end = athena ? List.of(CTM + "horizontal", CTM + "horizontal", CTM + "particle", CTM + "particle")
                        : List.of(GLASS);
                    List<String> middle = athena ? Collections.nCopies(4, CTM + "horizontal") : List.of(GLASS);
                    check("matching_paint_connects_both_across_chunks_and_to_ordinary_glass",
                        initial.equals(Map.of("west", end, "center", middle, "east", end)));
                    signal("matched");
                    stage = Stage.SPLIT;
                }
                case SPLIT -> {
                    if (!ready("server-split", blue, red)) return;
                    var split = observe("repainted-facades", blue, red);
                    List<String> isolated = athena ? Collections.nCopies(4, CTM + "particle") : List.of(GLASS);
                    check("repainting_removes_the_connected_texture_from_all_three_faces",
                        split.equals(Map.of("west", isolated, "center", isolated, "east", isolated)));
                    signal("split");
                    stage = Stage.REMATCHED;
                }
                case REMATCHED -> {
                    if (!ready("server-rematched", blue, blue)) return;
                    check("restoring_matching_paint_reconnects_both_joins",
                        initial.equals(observe("reconnected-facades", blue, blue)));
                    if (oculus) {
                        signal("rematched");
                        stage = Stage.WRENCH;
                    } else {
                        startReload();
                    }
                }
                case WRENCH -> {
                    if (!readyWithWrench("server-wrench", blue, true)) return;
                    observeWrench(blue);
                    signal("wrench");
                    stage = Stage.RESTORED;
                }
                case RESTORED -> {
                    if (!readyWithWrench("server-wrench-away", blue, false)) return;
                    check("putting_away_wrench_restores_both_join_faces",
                        initial.equals(observe("after-wrench-facades", blue, blue)));
                    startReload();
                }
                case RELOADED -> {
                    if (!reload.isDone() || minecraft.getOverlay() != null) return;
                    reload.join();
                    if (!ready("server-rematched", blue, blue)) return;
                    check("reloaded_pack_keeps_the_same_connected_textures", hasExpectedGlassBlockState()
                        && initial.equals(observe("reloaded-facades", blue, blue)));
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean hasExpectedGlassBlockState() {
        var file = ResourceLocation.fromNamespaceAndPath("enderio", "blockstates/clear_glass_blue.json");
        return PACK.equals(minecraft.getResourceManager().getResource(file).orElseThrow().sourcePackId()) == athena;
    }

    private void startReload() {
        reload = minecraft.reloadResourcePacks();
        stage = Stage.RELOADED;
    }

    private boolean readyWithWrench(String marker, Block blue, boolean held) {
        return minecraft.player.getMainHandItem().is(EIOItems.YETA_WRENCH.get()) == held
            && ClientFacadeVisibility.areFacadesVisible() != held && ready(marker, blue, blue);
    }

    private boolean ready(String marker, Block west, Block center) {
        if (!Files.exists(output.resolve(marker)) || !minecraft.level.getBlockState(EAST).is(west)
                || !hasPaint(WEST, west) || !hasPaint(CENTER, center)) return false;
        if (readyTick == -1) {
            readyTick = minecraft.player.tickCount;
            minecraft.gui.getChat().clearMessages(false);
        }
        if (minecraft.player.tickCount - readyTick < 30) return false;
        readyTick = -1;
        return true;
    }

    private boolean hasPaint(BlockPos pos, Block paint) {
        return minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
            && bundle.hasFacade() && bundle.getFacadeBlock() == paint;
    }

    private Map<String, List<String>> observe(String name, Block west, Block center) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
        var faces = Map.of("west", face(WEST, west), "center", face(CENTER, center), "east", face(EAST, west));
        frame.put("southFaceSprites", faces);
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        verifyShaderFrame(name, frame, image, false);
        verifyGlassPixels(name, frame, image, west != center, false);
        verifyJoinPixels(name, frame, image, west != center);
        return faces;
    }

    private void observeWrench(Block blue) throws IOException {
        String name = "wrench-facades";
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        verifyShaderFrame(name, frame, image, true);
        verifyGlassPixels(name, frame, image, false, true);
        int border = ordinaryGlassBorderPixels(image);
        var eastFace = face(EAST, blue);
        frame.put("ordinaryGlassWestBorderPixels", border);
        frame.put("ordinaryGlassSouthSprites", eastFace);
        check("ordinary_connected_glass_follows_actual_ghost_visibility", shaders
            ? border > 100 && eastFace.equals(Collections.nCopies(4, CTM + "particle"))
            : border < 35 && eastFace.equals(initial.get("east")));
        check("wrench_exposes_both_energy_conduit_models", List.of(WEST, CENTER).stream().allMatch(pos -> {
            var bundle = (ConduitBundleBlockEntity) minecraft.level.getBlockEntity(pos);
            var state = bundle.getBlockState();
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            return !model.getQuads(state, null, RandomSource.create(42), bundle.getModelData(), RenderType.cutout()).isEmpty()
                || Arrays.stream(Direction.values()).anyMatch(side ->
                    !model.getQuads(state, side, RandomSource.create(42), bundle.getModelData(), RenderType.cutout()).isEmpty());
        }));
    }

    private static int ordinaryGlassBorderPixels(BufferedImage image) {
        int count = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = height * 51 / 100; y < height * 60 / 100; y++) {
            for (int x = width * 539 / 1000; x < width * 550 / 1000; x++) {
                int rgb = image.getRGB(x, y);
                int blue = rgb & 255;
                if (blue - (rgb >> 16 & 255) > 12 && blue - (rgb >> 8 & 255) > 12) count++;
            }
        }
        return count;
    }

    private void verifyShaderFrame(String name, Map<String, Object> frame, BufferedImage image, boolean wrench) {
        if (!oculus) return;
        boolean active = IrisApi.getInstance().isShaderPackInUse();
        int marker = image.getRGB(16, image.getHeight() - 16) & 0xFFFFFF;
        frame.put("shaderPackInUse", active);
        frame.put("shaderSignature", String.format("%06x", marker));
        check(shaders ? "shader_pack_drew_" + name : "shaders_disabled_" + name,
            active == shaders && (marker == 0xFF00FF) == shaders);
        if (terrainPrograms) {
            int width = image.getWidth();
            int height = image.getHeight();
            int stone = image.getRGB(width / 4, height * 3 / 4) & 0xFFFFFF;
            int cutout = 0;
            for (int y = height * 48 / 100; y < height * 52 / 100; y++) {
                for (int x = width * 365 / 1000; x < width * 375 / 1000; x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFFFF00) cutout++;
                }
            }
            frame.put("terrainSignature", String.format("%06x", stone));
            frame.put("cutoutSignaturePixels", cutout);
            check("custom_solid_terrain_drew_" + name, stone == 0x00FFFF);
            check("custom_cutout_terrain_drew_" + name, wrench ? cutout == 0 : cutout > 10);
        }
    }

    private void verifyGlassPixels(String name, Map<String, Object> frame, BufferedImage image, boolean split, boolean wrench) {
        int[] tinted = new int[3];
        int[] background = new int[3];
        int width = image.getWidth();
        int height = image.getHeight();
        int halfSample = width * 40 / 1000;
        for (int i = 0; i < 3; i++) {
            int xCenter = width * (414 + 86 * i) / 1000;
            // The ordinary glass's exposed west face projects into the middle sample.
            int right = wrench && shaders && i == 1 ? width * 530 / 1000 : xCenter + halfSample;
            for (int y = height * 52 / 100; y < height * 62 / 100; y++) {
                for (int x = xCenter - halfSample; x < right; x++) {
                    int pixel = image.getRGB(x, y);
                    int red = pixel >> 16 & 255;
                    int green = pixel >> 8 & 255;
                    int blue = pixel & 255;
                    int hue = split && i == 1 ? red : blue;
                    int other = split && i == 1 ? blue : red;
                    int contrast = wrench && !shaders && i < 2 ? 5 : 12;
                    if (hue - other > contrast && hue - green > contrast) tinted[i]++;
                    if (red == green && green == blue && red >= 64) background[i]++;
                }
            }
        }
        frame.put("glassTintPixels", tinted);
        frame.put("visibleStonePixels", background);
        if (wrench && shaders) {
            check("active_shaders_hide_only_the_ghost_facades",
                tinted[0] < 35 && tinted[1] < 35 && tinted[2] > 35 && Arrays.stream(background).allMatch(count -> count > 400));
        } else {
            check("paint_color_and_transparency_" + name,
                Arrays.stream(tinted).allMatch(count -> count > 35) && Arrays.stream(background).allMatch(count -> count > 400));
        }
    }

    private void verifyJoinPixels(String name, Map<String, Object> frame, BufferedImage image, boolean split) {
        int[][] joins = new int[2][];
        int width = image.getWidth();
        int height = image.getHeight();
        int halfWidth = width / 80;
        for (int i = 0; i < joins.length; i++) {
            int x = width * (457 + 86 * i) / 1000 - halfWidth;
            joins[i] = image.getRGB(x, height * 52 / 100, halfWidth * 2, height / 10, null, 0, halfWidth * 2);
        }
        if (initialJoins == null) {
            initialJoins = joins;
            return;
        }
        int[] differences = new int[2];
        for (int i = 0; i < differences.length; i++) {
            for (int pixel = 0; pixel < joins[i].length; pixel++) {
                if (joins[i][pixel] != initialJoins[i][pixel]) differences[i]++;
            }
        }
        frame.put("changedJoinPixels", differences);
        check("rendered_joins_" + name, Arrays.stream(differences).allMatch(count -> split ? count > 100 : count == 0));
    }

    private List<String> face(BlockPos pos, Block paint) {
        var state = paint.defaultBlockState();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var rawData = minecraft.level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle
            ? bundle.getModelData() : ModelData.EMPTY;
        var data = model.getModelData(minecraft.level, pos, state, rawData);
        return model.getQuads(state, Direction.SOUTH, RandomSource.create(42), data, RenderType.cutout()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
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
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_facade_ctm", result);
    }
}

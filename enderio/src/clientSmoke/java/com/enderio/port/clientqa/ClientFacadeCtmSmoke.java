package com.enderio.port.clientqa;

import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientFacadeCtmSmoke {
    private enum Stage { TITLE, CONNECT, MATCHED, SPLIT, REMATCHED, RELOADED, DONE }

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
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private int readyTick = -1;
    private CompletableFuture<Void> reload;
    private Map<String, List<String>> initial;

    ClientFacadeCtmSmoke(boolean athena, boolean embeddium) {
        this.athena = athena;
        this.embeddium = embeddium;
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
                    reload = minecraft.reloadResourcePacks();
                    stage = Stage.RELOADED;
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
        verifyGlassPixels(name, frame, west != center);
        return faces;
    }

    private void verifyGlassPixels(String name, Map<String, Object> frame, boolean split) throws IOException {
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        int[] tinted = new int[3];
        int[] background = new int[3];
        int width = image.getWidth();
        int height = image.getHeight();
        int halfSample = width * 40 / 1000;
        for (int i = 0; i < 3; i++) {
            int xCenter = width * (414 + 86 * i) / 1000;
            for (int y = height * 52 / 100; y < height * 62 / 100; y++) {
                for (int x = xCenter - halfSample; x < xCenter + halfSample; x++) {
                    int pixel = image.getRGB(x, y);
                    int red = pixel >> 16 & 255;
                    int green = pixel >> 8 & 255;
                    int blue = pixel & 255;
                    int hue = split && i == 1 ? red : blue;
                    int other = split && i == 1 ? blue : red;
                    if (hue - other > 12 && hue - green > 12) tinted[i]++;
                    if (red == green && green == blue && red >= 64) background[i]++;
                }
            }
        }
        frame.put("glassTintPixels", tinted);
        frame.put("visibleStonePixels", background);
        check("paint_color_and_transparency_" + name,
            java.util.Arrays.stream(tinted).allMatch(count -> count > 35)
                && java.util.Arrays.stream(background).allMatch(count -> count > 400));
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

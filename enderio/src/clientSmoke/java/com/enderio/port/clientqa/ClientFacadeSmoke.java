package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.api.conduits.facade.FacadeType;
import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleRenderState;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.client.content.conduits.model.facades.FacadeItemModel;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientFacadeSmoke {
    enum Renderer { FORGE, EMBEDDIUM, OCULUS }

    private enum Stage { TITLE, CONNECT, PLACE, ADD_CONDUIT, VISIBLE, GHOST, RESTORED, RELOADED, DONE }

    private record Sample(BlockPos pos, FacadeType type, Block paint) {}
    private static final BlockPos STANDALONE = new BlockPos(2656, 80, 13);
    private static final List<Sample> OPAQUE_SAMPLES = List.of(
        new Sample(new BlockPos(2652, 80, 13), FacadeType.BASIC, Blocks.GOLD_BLOCK),
        new Sample(new BlockPos(2654, 80, 13), FacadeType.HARDENED, Blocks.DIAMOND_BLOCK),
        new Sample(STANDALONE, FacadeType.TRANSPARENT, Blocks.REDSTONE_BLOCK),
        new Sample(new BlockPos(2658, 80, 13), FacadeType.TRANSPARENT_HARDENED, Blocks.EMERALD_BLOCK));
    private static final List<Sample> STAINED_SAMPLES = List.of(
        new Sample(new BlockPos(2652, 80, 13), FacadeType.BASIC, Blocks.RED_STAINED_GLASS),
        new Sample(new BlockPos(2654, 80, 13), FacadeType.HARDENED, Blocks.BLUE_STAINED_GLASS),
        new Sample(STANDALONE, FacadeType.TRANSPARENT, Blocks.RED_STAINED_GLASS),
        new Sample(new BlockPos(2658, 80, 13), FacadeType.TRANSPARENT_HARDENED, Blocks.BLUE_STAINED_GLASS));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final Renderer renderer;
    private final boolean stainedGlass;
    private final List<Sample> samples;
    private Stage stage = Stage.TITLE;
    private int nextSlot;
    private int readyTick = -1;
    private int[] visibleColors;
    private CompletableFuture<Void> reload;

    ClientFacadeSmoke(Renderer renderer, boolean stainedGlass) {
        this.renderer = renderer;
        this.stainedGlass = stainedGlass;
        samples = stainedGlass ? STAINED_SAMPLES : OPAQUE_SAMPLES;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Facade client did not complete stage " + stage + " at placement " + nextSlot);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO conduit facades", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("expected_chunk_renderer", ModList.get().isLoaded("embeddium") == (renderer != Renderer.FORGE));
                    check("expected_oculus_installation", ModList.get().isLoaded("oculus") == (renderer == Renderer.OCULUS));
                    signal("connected");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (!placeFacade()) return;
                    var placed = samples.get(nextSlot++);
                    if (stainedGlass && placed.pos().equals(STANDALONE)) {
                        stage = Stage.ADD_CONDUIT;
                    } else if (nextSlot == samples.size()) {
                        stage = Stage.VISIBLE;
                    }
                }
                case ADD_CONDUIT -> {
                    if (addConduit()) stage = Stage.PLACE;
                }
                case VISIBLE -> {
                    if (!marker("server-inspect") || !positioned(2655.5, 19.5)
                        || !minecraft.player.getInventory().getItem(8).is(EIOItems.YETA_WRENCH.get())
                        || !samples.stream().allMatch(this::matchesModel)) return;
                    if (stainedGlass && readyTick == -1) minecraft.gui.getChat().clearMessages(false);
                    if (!waitForMesh(true)) return;
                    check("all_four_world_models_match_their_installed_facades", true);
                    visibleColors = capture("facades-visible", stainedGlass ? 14 : 55);
                    check("four_facade_colors_render_in_world", enoughPaint(visibleColors));
                    if (stainedGlass) checkGlassConduits(true);
                    minecraft.player.getInventory().selected = 8;
                    stage = Stage.GHOST;
                }
                case GHOST -> {
                    if (!waitForMesh(false)) return;
                    var colors = capture("facades-ghosted", stainedGlass ? 7 : 55);
                    if (stainedGlass) {
                        check("stained_glass_remains_visible_as_a_ghost", enoughPaint(colors));
                        checkGlassConduits(false);
                    } else {
                        var weakColors = palettePixels(14);
                        for (int i = 0; i < colors.length; i++) {
                            check(samples.get(i).type().getSerializedName() + "_wrench_ghost_is_visible_and_less_saturated",
                                weakColors[i] > 100 && colors[i] * 2 < visibleColors[i]);
                        }
                    }
                    minecraft.player.getInventory().selected = 0;
                    stage = Stage.RESTORED;
                }
                case RESTORED -> {
                    if (!waitForMesh(true)) return;
                    check("four_facades_restore_their_normal_colors", enoughPaint(capture("facades-restored", stainedGlass ? 14 : 55)));
                    if (stainedGlass) {
                        checkGlassConduits(true);
                        reload = minecraft.reloadResourcePacks();
                        stage = Stage.RELOADED;
                    } else {
                        finish(null);
                    }
                }
                case RELOADED -> {
                    if (!reload.isDone() || minecraft.getOverlay() != null || !waitForMesh(true)) return;
                    reload.join();
                    check("resource_reload_keeps_all_four_model_states", samples.stream().allMatch(this::matchesModel));
                    check("resource_reload_rebuilds_the_four_stained_facade_meshes", enoughPaint(capture("facades-reloaded", 14)));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean placeFacade() throws IOException {
        var sample = samples.get(nextSlot);
        if (!marker("server-place-" + nextSlot) || !positioned(sample.pos().getX() + 0.5, 15.5)) return false;
        var player = minecraft.player;
        var inventory = player.getInventory();
        var stack = inventory.getItem(nextSlot);
        if (stack.isEmpty()) return false;
        var provider = stack.getCapability(EnderIOCapabilities.CONDUIT_FACADE_PROVIDER).orElse(null);
        var itemModel = minecraft.getItemRenderer().getModel(stack, minecraft.level, player, 0);
        String type = sample.type().getSerializedName();
        check(type + "_paint_and_variant_arrive_in_the_inventory", stack.getCount() == 1 && provider != null
            && provider.isValid() && provider.type() == sample.type() && provider.block() == sample.paint());
        check(type + "_has_a_painted_item_render_pass", itemModel instanceof FacadeItemModel
            && itemModel.getRenderPasses(stack, false).stream().anyMatch(pass -> pass != itemModel));
        BlockHitResult hit;
        if (stainedGlass && sample.pos().equals(STANDALONE)) {
            if (!(player.pick(player.getBlockReach(), 1.0f, false) instanceof BlockHitResult floor)
                || !floor.getBlockPos().equals(STANDALONE.below()) || floor.getDirection() != Direction.UP) return false;
            hit = floor;
        } else {
            hit = new BlockHitResult(Vec3.atCenterOf(sample.pos()).add(0, 0, 0.125), Direction.SOUTH, sample.pos(), false);
        }
        inventory.selected = nextSlot;
        check(type + "_placed_by_survival_use", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal("placed-" + nextSlot);
        return true;
    }

    private boolean addConduit() throws IOException {
        if (!marker("server-add-conduit") || !positioned(STANDALONE.getX() + 0.5, 15.5)) return false;
        if (!(minecraft.level.getBlockEntity(STANDALONE) instanceof ConduitBundleBlockEntity bundle)
            || !bundle.hasFacade() || !bundle.getConduits().isEmpty()) return false;
        var player = minecraft.player;
        var inventory = player.getInventory();
        var id = EnderIODataComponents.CONDUIT.getId(inventory.getItem(4));
        if (!ResourceLocation.parse("enderio:energy").equals(id)) return false;
        inventory.selected = 4;
        if (!(player.pick(player.getBlockReach(), 1.0f, false) instanceof BlockHitResult hit)
            || !hit.getBlockPos().equals(STANDALONE)) return false;
        check("standalone_glass_facade_accepts_a_conduit_through_native_use",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal("added-conduit");
        return true;
    }

    private boolean matchesModel(Sample sample) {
        if (!(minecraft.level.getBlockEntity(sample.pos()) instanceof ConduitBundleBlockEntity bundle)
            || !bundle.hasFacade() || bundle.getFacadeType() != sample.type() || bundle.getConduits().size() != 1) return false;
        var model = bundle.getModelData().get(ConduitBundleRenderState.PROPERTY);
        return model != null && model.hasFacade() && model.facade().is(sample.paint())
            && model.doesFacadeHideConduits() == sample.type().doesHideConduits();
    }

    private boolean waitForMesh(boolean visible) {
        if (ClientFacadeVisibility.areFacadesVisible() != visible) return false;
        if (readyTick == -1) readyTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - readyTick < 35) return false;
        readyTick = -1;
        return true;
    }

    private void checkGlassConduits(boolean facadesVisible) {
        String phase = facadesVisible ? stage.name().toLowerCase(java.util.Locale.ROOT) : "wrench";
        for (var sample : samples) {
            var state = minecraft.level.getBlockState(sample.pos());
            var data = minecraft.level.getBlockEntity(sample.pos()).getModelData();
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            var random = RandomSource.create(42);
            boolean conduitRenders = !model.getQuads(state, null, random, data, RenderType.cutout()).isEmpty()
                || Arrays.stream(Direction.values()).anyMatch(side -> !model.getQuads(state, side, random, data, RenderType.cutout()).isEmpty());
            check(sample.type().getSerializedName() + "_conduit_visibility_" + phase,
                conduitRenders == !(facadesVisible && sample.type().doesHideConduits()));
        }
    }

    private static boolean enoughPaint(int[] colors) {
        return Arrays.stream(colors).allMatch(count -> count > 300);
    }

    private int[] capture(String name, int contrast) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        var colors = palettePixels(contrast);
        frame.put("facadeColorPixels", colors);
        if (!stainedGlass && name.equals("facades-ghosted")) frame.put("faintFacadeColorPixels", palettePixels(14));
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
        return colors;
    }

    private int[] palettePixels(int contrast) {
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int[] counts = new int[samples.size()];
            int width = image.getWidth();
            int height = image.getHeight();
            int halfWidth = width * 35 / 1000;
            // Sample the interior face of each facade at the fixture's fixed camera position.
            // The cyan sample excludes the blue sky above its facade.
            for (int i = 0; i < counts.length; i++) {
                int centerX = width * (295 + i * 135) / 1000;
                for (int y = height * 47 / 100; y < height * 58 / 100; y++) {
                    for (int x = centerX - halfWidth; x < centerX + halfWidth; x++) {
                        if (matchesPaint(i, image.getPixelRGBA(x, y), contrast)) counts[i]++;
                    }
                }
            }
            return counts;
        }
    }

    private boolean matchesPaint(int sample, int pixel, int contrast) {
        int red = pixel & 255;
        int green = pixel >> 8 & 255;
        int blue = pixel >> 16 & 255;
        if (stainedGlass) {
            return sample % 2 == 0 ? red - green >= contrast && red - blue >= contrast
                : blue - red >= contrast && blue - green >= contrast;
        }
        return switch (sample) {
            case 0 -> red - blue >= contrast && green - blue >= contrast && red >= green;
            case 1 -> green - red >= contrast && blue - red >= contrast;
            case 2 -> red - green >= contrast && red - blue >= contrast;
            case 3 -> green - red >= contrast && green - blue >= contrast;
            default -> throw new IllegalArgumentException("Unexpected facade sample " + sample);
        };
    }

    private boolean positioned(double x, double z) {
        return minecraft.player.distanceToSqr(x, 80, z) < 0.1;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_facades", result);
    }
}

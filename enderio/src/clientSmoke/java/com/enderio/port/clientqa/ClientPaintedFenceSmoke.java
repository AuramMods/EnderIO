package com.enderio.port.clientqa;

import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.block.entity.SinglePaintedBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientPaintedFenceSmoke {
    private enum Stage { TITLE, JOIN, PLACE, VIEW, FINISH, DONE }

    private record Site(BlockPos pos, Block paint) { }

    private enum View {
        PLACED("placed", true, false, true), SWAPPED("swapped", false, true, false),
        RESTORED("restored", false, true, false), ISOLATED("isolated", false, false, false);

        private final String id;
        private final boolean north;
        private final boolean east;
        private final boolean west;

        View(String id, boolean north, boolean east, boolean west) {
            this.id = id;
            this.north = north;
            this.east = east;
            this.west = west;
        }

        boolean matches(BlockState state) {
            return state.is(EIOBlocks.PAINTED_FENCE.get()) && state.getValue(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.NORTH) == north && state.getValue(BlockStateProperties.EAST) == east
                && state.getValue(BlockStateProperties.WEST) == west && !state.getValue(BlockStateProperties.SOUTH);
        }
    }

    private static final Site[] SITES = {
        new Site(new BlockPos(4800, 80, 8), Blocks.EMERALD_BLOCK),
        new Site(new BlockPos(4807, 80, 8), Blocks.DIAMOND_BLOCK)
    };

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private View view = reopened ? View.RESTORED : View.PLACED;
    private int siteIndex;
    private int viewSince = -1;

    ClientPaintedFenceSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 175_000_000_000L) {
                throw new AssertionError("Painted fence stopped at " + stage + " / " + view + " / " + siteIndex);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                String address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Painted fence QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", minecraft.getSingleplayerServer() == null
                        && !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    minecraft.options.hideGui = true;
                    signal("connected");
                    stage = reopened ? Stage.VIEW : Stage.PLACE;
                }
                case PLACE -> placeFence();
                case VIEW -> inspectFence();
                case FINISH -> {
                    if (ready("done")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void placeFence() throws IOException {
        var site = SITES[siteIndex];
        var player = minecraft.player;
        var item = player.getInventory().getItem(siteIndex);
        if (!ready("place-" + siteIndex) || !positioned(site) || item.isEmpty() || !sourceWater(site.pos())) return;
        check("site_" + siteIndex + "_has_one_original_painted_item_and_source_water", item.is(EIOBlocks.PAINTED_FENCE.get().asItem())
            && item.getCount() == 1 && BlockPaintData.of(site.paint()).equals(BlockPaintData.ITEM_DATA.get(item)));
        player.getInventory().selected = siteIndex;
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(site.pos()), Direction.UP, site.pos().below(), false);
        check("site_" + siteIndex + "_native_survival_placement", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal("used-" + siteIndex);
        stage = Stage.VIEW;
    }

    private void inspectFence() throws IOException {
        var site = SITES[siteIndex];
        String id = view.id + "-" + siteIndex;
        var state = minecraft.level.getBlockState(site.pos());
        if (!ready(id) || !positioned(site) || !view.matches(state)) return;
        int tick = minecraft.player.tickCount;
        if (viewSince < 0) viewSince = tick;
        if (tick - viewSince < 12) return;
        check(id + "_keeps_source_water_and_spends_the_original_item", sourceWater(site.pos())
            && minecraft.player.getInventory().getItem(siteIndex).isEmpty());
        var entity = minecraft.level.getBlockEntity(site.pos());
        check(id + "_syncs_original_paint", entity instanceof SinglePaintedBlockEntity paint && paint.getPrimaryPaint().orElse(null) == site.paint());
        var data = entity.getModelData();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<BakedQuad>();
        for (var layer : model.getRenderTypes(state, RandomSource.create(42), data)) {
            quads.addAll(model.getQuads(state, null, RandomSource.create(42), data, layer));
            for (var side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(42), data, layer));
        }
        var sprite = ResourceLocation.fromNamespaceAndPath("minecraft", "block/" + BuiltInRegistries.BLOCK.getKey(site.paint()).getPath());
        check(id + "_all_fence_quads_use_the_paint_sprite", !quads.isEmpty()
            && quads.stream().allMatch(quad -> quad.getSprite().contents().name().equals(sprite)));
        boolean north = false;
        boolean east = false;
        boolean west = false;
        boolean south = false;
        for (var quad : quads) {
            var vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int index = 0; index < vertices.length; index += stride) {
                float x = Float.intBitsToFloat(vertices[index]);
                float z = Float.intBitsToFloat(vertices[index + 2]);
                north |= z < 0.04f;
                east |= x > 0.96f;
                west |= x < 0.04f;
                south |= z > 0.96f;
            }
        }
        check(id + "_real_baked_model_draws_the_expected_rails", view.north == north && view.east == east && view.west == west && !south);
        var frame = ClientSmoke.capture(id + ".png");
        frame.put("quads", quads.size());
        frame.put("rails", Map.of("north", north, "east", east, "west", west, "south", south));
        captures.put(id, frame);
        check(id + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
        signal(id);
        advanceView();
    }

    private void advanceView() {
        viewSince = -1;
        if (view == View.PLACED || view == View.RESTORED) {
            view = reopened ? View.ISOLATED : View.SWAPPED;
            return;
        }
        if (++siteIndex >= SITES.length) {
            stage = Stage.FINISH;
            return;
        }
        view = reopened ? View.RESTORED : View.PLACED;
        stage = reopened ? Stage.VIEW : Stage.PLACE;
    }

    private boolean positioned(Site site) {
        return minecraft.player.distanceToSqr(site.pos().getX() + 0.5, 80, 11.5) < 0.09;
    }

    private boolean sourceWater(BlockPos pos) {
        var fluid = minecraft.level.getFluidState(pos);
        return fluid.isSource() && fluid.is(FluidTags.WATER);
    }

    private boolean ready(String id) {
        return Files.exists(output.resolve("server-" + id));
    }

    private void signal(String id) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(id + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(id + ".json"));
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
        ClientSmoke.complete("client_painted_fence", result);
    }
}

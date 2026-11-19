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
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class ClientPaintedRedstoneSmoke {
    private enum Stage { TITLE, JOIN, PLACE, VIEW, ACTIVATE, ACTIVE, CLOSE, CLOSED, FINISH, DONE }

    private record Sample(BlockPos pos, String name, Supplier<? extends Block> registeredBlock, Block paint) {
        Block block() { return registeredBlock.get(); }
    }

    private static final Sample[] SAMPLES = {
        new Sample(new BlockPos(2200, 80, 8), "redstone-block", EIOBlocks.PAINTED_REDSTONE_BLOCK::get, Blocks.DIAMOND_BLOCK),
        new Sample(new BlockPos(2207, 80, 8), "pressure-plate", EIOBlocks.PAINTED_WOODEN_PRESSURE_PLATE::get, Blocks.GOLD_BLOCK),
        new Sample(new BlockPos(2214, 80, 8), "fence-gate", EIOBlocks.PAINTED_FENCE_GATE::get, Blocks.EMERALD_BLOCK),
        new Sample(new BlockPos(2221, 80, 8), "trapdoor", EIOBlocks.PAINTED_TRAPDOOR::get, Blocks.LAPIS_BLOCK)
    };

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean wet = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int slot;
    private int nextCaptureTick;
    private List<Integer> restingGeometry = List.of();

    ClientPaintedRedstoneSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 160_000_000_000L) {
                throw new AssertionError("Painted redstone scenario stopped at " + stage + " for slot " + slot);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Painted redstone QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            var sample = SAMPLES[slot];
            var state = minecraft.level.getBlockState(sample.pos());
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    minecraft.options.hideGui = true;
                    signal("connected");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    var item = minecraft.player.getInventory().getItem(slot);
                    if (!ready("place-" + slot) || !positioned(sample) || item.isEmpty()) return;
                    check(sample.name() + "_hopper_output_syncs_with_original_paint",
                        item.is(sample.block().asItem()) && item.getCount() == 1
                            && BlockPaintData.of(sample.paint()).equals(BlockPaintData.ITEM_DATA.get(item)));
                    minecraft.player.getInventory().selected = slot;
                    var pos = sample.pos();
                    if (wetTrapdoor()) {
                        check("trapdoor_is_placed_into_an_existing_water_source", hasWaterSource(pos));
                    }
                    var click = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
                    check(sample.name() + "_survival_placement_consumes_action", use(click));
                    signal("placed-" + slot);
                    stage = Stage.VIEW;
                }
                case VIEW -> {
                    if (!ready("view-" + slot) || !state.is(sample.block()) || !settled()) return;
                    check(sample.name() + "_placement_consumes_its_only_item", minecraft.player.getMainHandItem().isEmpty());
                    restingGeometry = paintedGeometry(sample, state, "placed");
                    capture(sample.name() + "-placed");
                    signal("viewed-" + slot);
                    if (slot == 0) advance();
                    else stage = slot == 1 ? Stage.ACTIVE : Stage.ACTIVATE;
                }
                case ACTIVATE -> {
                    if (!ready("active-" + slot)) return;
                    check(sample.name() + "_ordinary_hand_use_opens_it", use(hit(sample, state)));
                    stage = Stage.ACTIVE;
                }
                case ACTIVE -> {
                    if (!ready("active-" + slot) || !state.is(sample.block())
                        || !state.getValue(slot == 1 ? BlockStateProperties.POWERED : BlockStateProperties.OPEN)
                        || !settled()) return;
                    check(sample.name() + "_active_geometry_differs_from_rest",
                        !paintedGeometry(sample, state, "active").equals(restingGeometry));
                    capture(sample.name() + "-active");
                    signal("active-" + slot);
                    if (slot == 1) advance();
                    else stage = Stage.CLOSE;
                }
                case CLOSE -> {
                    if (!ready("close-" + slot)) return;
                    check(sample.name() + "_second_use_closes_it", use(hit(sample, state)));
                    stage = Stage.CLOSED;
                }
                case CLOSED -> {
                    if (!state.is(sample.block()) || state.getValue(BlockStateProperties.OPEN)) return;
                    verifyWaterlogged(sample, state, "closed");
                    signal("closed-" + slot);
                    if (slot == SAMPLES.length - 1) stage = Stage.FINISH;
                    else advance();
                }
                case FINISH -> {
                    if (!ready("finish") || !state.is(sample.block()) || !state.getValue(BlockStateProperties.OPEN)
                        || !state.getValue(BlockStateProperties.POWERED) || !settled()) return;
                    paintedGeometry(sample, state, "powered");
                    capture("trapdoor-powered");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private BlockHitResult hit(Sample sample, BlockState state) {
        var pos = sample.pos();
        var center = state.getShape(minecraft.level, pos).bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        return new BlockHitResult(center, Direction.SOUTH, pos, false);
    }

    private boolean use(BlockHitResult hit) {
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private List<Integer> paintedGeometry(Sample sample, BlockState state, String phase) {
        check(sample.name() + "_" + phase + "_syncs_painted_entity",
            minecraft.level.getBlockEntity(sample.pos()) instanceof SinglePaintedBlockEntity painted
                && painted.getPrimaryPaint().orElse(null) == sample.paint());
        var entity = minecraft.level.getBlockEntity(sample.pos());
        ModelData data = entity.getModelData();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<BakedQuad>();
        for (var type : model.getRenderTypes(state, RandomSource.create(42), data)) {
            quads.addAll(model.getQuads(state, null, RandomSource.create(42), data, type));
            for (var side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(42), data, type));
        }
        var paint = ResourceLocation.parse("minecraft:block/" + BuiltInRegistries.BLOCK.getKey(sample.paint()).getPath());
        check(sample.name() + "_" + phase + "_bakes_only_the_correct_paint",
            !quads.isEmpty() && quads.stream().allMatch(quad -> quad.getSprite().contents().name().equals(paint)));
        verifyWaterlogged(sample, state, phase);
        return quads.stream().map(quad -> Arrays.hashCode(quad.getVertices())).sorted().toList();
    }

    private void verifyWaterlogged(Sample sample, BlockState state, String phase) {
        if (wetTrapdoor()) {
            check("trapdoor_" + phase + "_retains_its_water_source",
                state.getValue(BlockStateProperties.WATERLOGGED) && hasWaterSource(sample.pos()));
        }
    }

    private boolean wetTrapdoor() {
        return wet && slot == SAMPLES.length - 1;
    }

    private boolean hasWaterSource(BlockPos pos) {
        var fluid = minecraft.level.getFluidState(pos);
        return fluid.isSource() && fluid.is(FluidTags.WATER);
    }

    private void advance() {
        slot++;
        stage = Stage.PLACE;
    }

    private boolean positioned(Sample sample) {
        return minecraft.player.distanceToSqr(sample.pos().getX() + 0.5, 80, 11.5) < 0.09;
    }

    private boolean settled() {
        int tick = minecraft.player.tickCount;
        if (nextCaptureTick == 0) nextCaptureTick = tick + 16;
        if (tick < nextCaptureTick) return false;
        nextCaptureTick = 0;
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_painted_redstone", result);
    }
}

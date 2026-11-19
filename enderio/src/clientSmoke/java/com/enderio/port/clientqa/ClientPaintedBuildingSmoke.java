package com.enderio.port.clientqa;

import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.block.entity.SinglePaintedBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

final class ClientPaintedBuildingSmoke {
    private enum Stage { TITLE, JOIN, PLACE, VIEW, UPDATED, OPEN_CRAFTING, CRAFT, PREVIEW, TAKE, RELIGHT, UNLIT, FINISH, DONE }

    private record Sample(BlockPos pos, String name, Supplier<? extends Block> registeredBlock, Block paint) {
        Block block() { return registeredBlock.get(); }
    }

    private static final Sample[] SAMPLES = {
        new Sample(new BlockPos(2400, 80, 8), "stairs", EIOBlocks.PAINTED_STAIRS::get, Blocks.EMERALD_BLOCK),
        new Sample(new BlockPos(2407, 80, 8), "wall", EIOBlocks.PAINTED_WALL::get, Blocks.GOLD_BLOCK),
        new Sample(new BlockPos(2414, 80, 8), "crafting-table", EIOBlocks.PAINTED_CRAFTING_TABLE::get, Blocks.DIAMOND_BLOCK),
        new Sample(new BlockPos(2421, 80, 8), "glowstone", EIOBlocks.PAINTED_GLOWSTONE::get, Blocks.OBSIDIAN)
    };
    private static final int GLOWSTONE = SAMPLES.length - 1;
    private static final BlockPos LIGHT_PROBE = SAMPLES[GLOWSTONE].pos().east();
    private static final ResourceLocation SLABS = ResourceLocation.parse("minecraft:oak_slab");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean wet;
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int slot;
    private int nextCaptureTick;
    private List<Integer> initialGeometry = List.of();

    ClientPaintedBuildingSmoke(boolean wet) {
        this.wet = wet;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 160_000_000_000L) {
                throw new AssertionError("Painted building scenario stopped at " + stage + " for slot " + slot);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Painted building QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            var sample = SAMPLES[slot];
            var state = minecraft.level.getBlockState(sample.pos());
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    minecraft.options.hideGui = true;
                    signal("connected");
                    if (reopened) {
                        slot = GLOWSTONE;
                        stage = Stage.RELIGHT;
                    } else stage = Stage.PLACE;
                }
                case PLACE -> {
                    var item = minecraft.player.getInventory().getItem(slot);
                    if (!ready("place-" + slot) || !positioned(sample) || item.isEmpty()) return;
                    check(sample.name() + "_native_output_syncs_with_its_paint",
                        item.is(sample.block().asItem()) && item.getCount() == 1
                            && BlockPaintData.of(sample.paint()).equals(BlockPaintData.ITEM_DATA.get(item)));
                    minecraft.player.getInventory().selected = slot;
                    if (slot == GLOWSTONE) {
                        check("the_glowstone_site_starts_with_no_block_light", state.isAir() && blockLight(LIGHT_PROBE) == 0);
                    }
                    var pos = sample.pos();
                    if (wet && slot < 2) {
                        check(sample.name() + "_is_placed_into_a_real_water_source", hasWaterSource(pos));
                    }
                    var click = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
                    check(sample.name() + "_survival_placement_consumes_action",
                        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, click).consumesAction());
                    signal("placed-" + slot);
                    stage = Stage.VIEW;
                }
                case VIEW -> {
                    if (!ready("view-" + slot) || !state.is(sample.block()) || (slot == GLOWSTONE && blockLight(LIGHT_PROBE) != 14)
                        || !settled()) return;
                    check(sample.name() + "_placement_consumes_its_only_item", minecraft.player.getMainHandItem().isEmpty());
                    initialGeometry = paintedGeometry(sample, state, "placed");
                    if (slot == GLOWSTONE) verifyLight(sample);
                    capture(sample.name() + "-placed");
                    signal("viewed-" + slot);
                    stage = slot < 2 ? Stage.UPDATED : slot == 2 ? Stage.OPEN_CRAFTING : Stage.FINISH;
                }
                case UPDATED -> {
                    if (!ready("updated-" + slot) || !state.is(sample.block()) || !updated(state) || !settled()) return;
                    check(sample.name() + "_neighbor_update_changes_the_baked_geometry",
                        !initialGeometry.equals(paintedGeometry(sample, state, "updated")));
                    capture(sample.name() + "-updated");
                    signal("updated-" + slot);
                    advance();
                }
                case OPEN_CRAFTING -> {
                    if (!ready("craft") || !positioned(sample)) return;
                    minecraft.options.hideGui = false;
                    var hit = new BlockHitResult(Vec3.atCenterOf(sample.pos()).relative(Direction.SOUTH, 0.5),
                        Direction.SOUTH, sample.pos(), false);
                    check("right_click_opens_the_painted_crafting_table",
                        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.CRAFT;
                }
                case CRAFT, PREVIEW, TAKE -> craft();
                case RELIGHT -> {
                    if (!ready("relight") || !positioned(sample) || !state.is(sample.block()) || blockLight(LIGHT_PROBE) != 14
                        || !settled()) return;
                    paintedGeometry(sample, state, "restored");
                    verifyLight(sample);
                    capture("glowstone-restored");
                    signal("relit");
                    stage = Stage.UNLIT;
                }
                case UNLIT -> {
                    if (!ready("unlit") || !state.isAir() || blockLight(LIGHT_PROBE) != 0 || !settled()) return;
                    check("the_removed_glowstone_no_longer_lights_the_site", blockLight(sample.pos()) == 0);
                    capture("glowstone-removed");
                    signal("unlit");
                    stage = Stage.FINISH;
                }
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

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case CRAFT -> {
                var recipe = minecraft.level.getRecipeManager().byKey(SLABS).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe) || !settled()) return;
                check("painted_table_keeps_a_full_three_by_three_crafting_menu_open",
                    menu.getGridWidth() == 3 && menu.getGridHeight() == 3);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.PREVIEW;
            }
            case PREVIEW -> {
                var result = menu.getSlot(0).getItem();
                if (!result.is(Items.OAK_SLAB) || !settled()) return;
                var frame = capture("painted-table-crafting");
                long templates = menu.slots.subList(1, 10).stream().filter(slot -> slot.getItem().is(Items.OAK_PLANKS)).count();
                frame.put("plankSlots", templates);
                frame.put("resultCount", result.getCount());
                check("three_oak_planks_preview_six_slabs", result.getCount() == 6
                    && templates == 3);
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem) || !settled()) return;
                check("the_crafting_grid_debits_the_planks_and_the_player_receives_six_slabs",
                    minecraft.player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.OAK_SLAB) && stack.getCount() == 6)
                        && minecraft.player.getInventory().items.stream().noneMatch(stack -> stack.is(Items.OAK_PLANKS)));
                minecraft.player.closeContainer();
                minecraft.options.hideGui = true;
                signal("crafted");
                advance();
            }
            default -> { }
        }
    }

    private List<Integer> paintedGeometry(Sample sample, BlockState state, String phase) {
        var entity = minecraft.level.getBlockEntity(sample.pos());
        check(sample.name() + "_" + phase + "_syncs_the_original_paint", entity instanceof SinglePaintedBlockEntity painted
            && painted.getPrimaryPaint().orElse(null) == sample.paint());
        var data = entity.getModelData();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<BakedQuad>();
        for (var type : model.getRenderTypes(state, RandomSource.create(42), data)) {
            quads.addAll(model.getQuads(state, null, RandomSource.create(42), data, type));
            for (var side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(42), data, type));
        }
        var sprite = ResourceLocation.parse("minecraft:block/" + BuiltInRegistries.BLOCK.getKey(sample.paint()).getPath());
        check(sample.name() + "_" + phase + "_uses_the_expected_sprite",
            !quads.isEmpty() && quads.stream().allMatch(quad -> quad.getSprite().contents().name().equals(sprite)));
        if (wet && slot < 2) {
            check(sample.name() + "_" + phase + "_retains_source_water",
                state.getValue(BlockStateProperties.WATERLOGGED) && hasWaterSource(sample.pos()));
        }
        return quads.stream().map(quad -> Arrays.hashCode(quad.getVertices())).sorted().toList();
    }

    private boolean updated(BlockState state) {
        return slot == 0 ? state.getValue(BlockStateProperties.STAIRS_SHAPE) == StairsShape.OUTER_LEFT
            : state.getValue(BlockStateProperties.EAST_WALL) == WallSide.LOW && !state.getValue(BlockStateProperties.UP);
    }

    private int blockLight(BlockPos pos) {
        return minecraft.level.getBrightness(LightLayer.BLOCK, pos);
    }

    private boolean hasWaterSource(BlockPos pos) {
        var fluid = minecraft.level.getFluidState(pos);
        return fluid.isSource() && fluid.is(FluidTags.WATER);
    }

    private void verifyLight(Sample sample) {
        check("obsidian_paint_preserves_light_15_at_the_glowstone_and_14_next_to_it",
            blockLight(sample.pos()) == 15 && blockLight(LIGHT_PROBE) == 14);
    }

    private void advance() {
        slot++;
        stage = Stage.PLACE;
    }

    private boolean positioned(Sample sample) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(sample.pos().getX() + 0.5, 80, 11.5) < 0.09;
    }

    private boolean settled() {
        int tick = minecraft.player.tickCount;
        if (nextCaptureTick == 0) nextCaptureTick = tick + 20;
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

    private Map<String, Object> capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
        return frame;
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
        ClientSmoke.complete("client_painted_building", result);
    }
}

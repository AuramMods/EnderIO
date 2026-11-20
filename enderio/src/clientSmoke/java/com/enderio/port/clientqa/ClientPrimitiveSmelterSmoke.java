package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PrimitiveAlloySmelterScreen;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientPrimitiveSmelterSmoke {
    private enum Stage { TITLE, WORLD, OPEN, MENU, WORKING, OUTPUT, COLLECT, PLACE, CLEAR, BREAK, RECOVER, REOPEN, REFILL, DONE }

    private record Basin(String name, BlockPos placement, Block liquid, boolean slab) {
        BlockPos flowing() { return placement.east(); }
        BlockPos source() { return placement.east(2); }
        BlockPos waterlogged() { return source().north(); }
        BlockPos isolated() { return source().north(3); }
        Vec3 approach() { return Vec3.atBottomCenterOf(placement.south().above()); }

        boolean hasFill(Level level) {
            return level.getBlockState(source()).is(liquid) && level.getFluidState(source()).isSource()
                && level.getBlockState(flowing()).is(liquid) && !level.getFluidState(flowing()).isSource()
                && (!slab || level.getBlockState(waterlogged()).is(Blocks.STONE_SLAB)
                    && !level.getFluidState(waterlogged()).isEmpty());
        }

        boolean isCleared(Level level) {
            return level.isEmptyBlock(source()) && level.isEmptyBlock(flowing())
                && level.getBlockState(isolated()).is(liquid) && level.getFluidState(isolated()).isSource()
                && (!slab || level.getBlockState(waterlogged()).is(Blocks.STONE_SLAB)
                    && !level.getBlockState(waterlogged()).getValue(SlabBlock.WATERLOGGED));
        }
    }

    private static final BlockPos POS = new BlockPos(0, 80, 0);
    private static final List<Basin> BASINS = List.of(new Basin("water", new BlockPos(2, 80, 1), Blocks.WATER, true),
        new Basin("lava", new BlockPos(8, 80, 1), Blocks.LAVA, false));
    private final Minecraft minecraft = Minecraft.getInstance();
    private final boolean insulation;
    private final String worldName = System.getProperty("enderio.clientqa.worldName");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private CompletableFuture<?> pending;
    private CompletableFuture<Boolean> observation;
    private int basinIndex;
    private int clearedAt;
    private int mineTick = -1;
    private int refillTick;

    ClientPrimitiveSmelterSmoke(boolean insulation) {
        this.insulation = insulation;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 195_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    var block = EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get();
                    var models = minecraft.getModelManager();
                    for (var state : block.getStateDefinition().getPossibleStates()) {
                        check("baked_primitive_" + state.getValues(), models.getBlockModelShaper().getBlockModel(state) != models.getMissingModel());
                    }
                    check("baked_primitive_item", minecraft.getItemRenderer().getModel(new ItemStack(block), null, null, 0) != models.getMissingModel());
                    var options = new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(worldName, options, new WorldOptions(57L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    var server = minecraft.getSingleplayerServer();
                    if (server == null || minecraft.player == null || minecraft.screen != null) return;
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        for (int z = 0; z < 4; z++) {
                            level.setBlockAndUpdate(new BlockPos(0, 79, z), Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(POS, EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get().defaultBlockState());
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.getInventory().setItem(9, new ItemStack(Items.COAL, 2));
                        if (insulation) {
                            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                            level.setDayTime(6000);
                            player.getInventory().setItem(10, new ItemStack(EIOItems.POWDERED_LAPIS_LAZULI.get()));
                            player.getInventory().setItem(11, new ItemStack(Items.WHITE_WOOL));
                            player.getInventory().setItem(12, new ItemStack(EIOItems.POWDERED_IRON.get()));
                            BASINS.forEach(basin -> buildBasin(level, basin));
                        } else {
                            player.getInventory().setItem(10, new ItemStack(Items.COPPER_INGOT));
                            player.getInventory().setItem(11, new ItemStack(EIOItems.SILICON.get()));
                        }
                        player.connection.teleport(0.5, 80, 2.5, 180, 0);
                    });
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!pending.isDone() || !minecraft.level.getBlockState(POS).is(EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get())
                        || minecraft.player.distanceToSqr(Vec3.atCenterOf(POS)) > 10) return;
                    pending.join();
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, POS, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof PrimitiveAlloySmelterScreen)
                        || !(minecraft.player.containerMenu instanceof PrimitiveAlloySmelterMenu menu)) return;
                    check("opened_native_five_slot_menu", menu.slots.size() == 41);
                    for (int i = 5; i < (insulation ? 9 : 8); i++) quickMove(menu, i);
                    stage = Stage.WORKING;
                }
                case WORKING -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (menu.getBurnProgress() < 0.1f || menu.getCraftingProgress() < 0.15f) return;
                    check("menu_syncs_flame_and_job_progress", menu.getSlot(0).getItem().is(Items.COAL)
                        && menu.getSlot(0).getItem().getCount() == 1);
                    capture("primitive-smelter-working");
                    stage = Stage.OUTPUT;
                }
                case OUTPUT -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (menu.getSlot(4).getItem().isEmpty()) return;
                    check(insulation ? "one_stock_insulation_output" : "one_historical_alloy_output", menu.getSlot(4).getItem().is(product())
                        && menu.getSlot(4).getItem().getCount() == 1 && menu.getSlot(1).getItem().isEmpty()
                        && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty());
                    capture("primitive-smelter-output");
                    quickMove(menu, 4);
                    stage = Stage.COLLECT;
                }
                case COLLECT -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (!menu.getSlot(4).getItem().isEmpty()) return;
                    check("output_in_player_inventory", minecraft.player.getInventory().countItem(product()) == 1);
                    minecraft.player.closeContainer();
                    if (insulation) {
                        approachBasin();
                        stage = Stage.PLACE;
                        return;
                    }
                    stage = Stage.DONE;
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("integrated_world_stopped", minecraft.getSingleplayerServer() == null);
                    finish(null);
                }
                case PLACE -> placeInsulation();
                case CLEAR -> verifyCleared();
                case BREAK -> breakInsulation();
                case RECOVER -> recoverInsulation();
                case REOPEN -> verifyReopened();
                case REFILL -> verifyRefill();
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static void buildBasin(Level level, Basin basin) {
        var stone = Blocks.STONE.defaultBlockState();
        var pos = basin.placement();
        for (int x = -1; x <= 3; x++) {
            for (int z = -4; z <= 1; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), stone);
            }
            level.setBlockAndUpdate(pos.offset(x, 0, 1), stone);
        }
        for (var wall : List.of(pos.west(), pos.north(), basin.flowing().north(), basin.source().east(),
            basin.waterlogged().east(), basin.waterlogged().north(), basin.isolated().east(),
            basin.isolated().west(), basin.isolated().north(), basin.isolated().south())) {
            level.setBlockAndUpdate(wall, stone);
        }
        level.setBlockAndUpdate(basin.waterlogged(), basin.slab()
            ? Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true) : stone);
        level.setBlockAndUpdate(basin.isolated(), basin.liquid().defaultBlockState());
        level.setBlockAndUpdate(basin.source(), basin.liquid().defaultBlockState());
        level.setBlockAndUpdate(basin.flowing(), basin.liquid().defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
    }

    private Item product() {
        return insulation ? EIOBlocks.INDUSTRIAL_INSULATION.get().asItem() : EIOItems.CONDUCTIVE_ALLOY_INGOT.get();
    }

    private void approachBasin() {
        var server = minecraft.getSingleplayerServer();
        var approach = BASINS.get(basinIndex).approach();
        server.execute(() -> server.getPlayerList().getPlayers().get(0).connection.teleport(approach.x, approach.y, approach.z, 180, 50));
    }

    private void placeInsulation() throws Exception {
        var player = minecraft.player;
        var basin = BASINS.get(basinIndex);
        if (minecraft.screen != null || player.distanceToSqr(basin.approach()) > 0.15 || !basin.hasFill(minecraft.level)) return;
        var inventory = player.getInventory();
        int slot = inventory.findSlotMatchingItem(new ItemStack(product()));
        if (slot < 0) return;
        if (slot >= 9) {
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
            return;
        }
        player.getInventory().selected = slot;
        check(basin.name() + "_uses_the_only_smelter_output", inventory.countItem(product()) == 1);
        capture(basin.name() + "-filled");
        var support = basin.placement().below();
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        check(basin.name() + "_native_survival_placement", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        clearedAt = -1;
        stage = Stage.CLEAR;
    }

    private void verifyCleared() throws Exception {
        var basin = BASINS.get(basinIndex);
        if (!minecraft.level.getBlockState(basin.placement()).is(EIOBlocks.INDUSTRIAL_INSULATION.get())) return;
        var server = minecraft.getSingleplayerServer();
        if (observation == null) {
            observation = server.submit(() -> basin.isCleared(server.overworld())
                && server.overworld().getBlockState(basin.placement()).is(EIOBlocks.INDUSTRIAL_INSULATION.get())
                && server.getPlayerList().getPlayers().get(0).getInventory().countItem(product()) == 0);
            return;
        }
        if (!observation.isDone()) return;
        if (clearedAt < 0) {
            check(basin.name() + "_server_drains_connected_fluid_and_preserves_isolated_source", observation.join());
            if (!basin.isCleared(minecraft.level)) return;
            clearedAt = minecraft.player.tickCount;
        }
        // Let the async section mesh catch up to the synchronized block states before capturing it.
        if (minecraft.player.tickCount - clearedAt < 8) return;
        observation = null;
        capture(basin.name() + "-cleared");
        stage = basinIndex == 0 ? Stage.BREAK : Stage.REOPEN;
        if (stage == Stage.REOPEN) {
            minecraft.level.disconnect();
            minecraft.clearLevel(new TitleScreen());
            check("first_integrated_world_exits_to_title", minecraft.getSingleplayerServer() == null);
            minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), worldName);
        }
    }

    private void breakInsulation() {
        var player = minecraft.player;
        var pos = BASINS.get(0).placement();
        if (minecraft.level.isEmptyBlock(pos)) {
            minecraft.options.keyUp.setDown(true);
            player.setXRot(0);
            stage = Stage.RECOVER;
            return;
        }
        if (mineTick < 0) check("native_survival_starts_mining_the_used_block", minecraft.gameMode.startDestroyBlock(pos, Direction.UP));
        else if (mineTick != player.tickCount) minecraft.gameMode.continueDestroyBlock(pos, Direction.UP);
        mineTick = player.tickCount;
    }

    private void recoverInsulation() {
        if (minecraft.player.getInventory().countItem(product()) == 0) return;
        minecraft.options.keyUp.setDown(false);
        var server = minecraft.getSingleplayerServer();
        if (observation == null) {
            observation = server.submit(() -> server.getPlayerList().getPlayers().get(0).getInventory().countItem(product()) == 1
                && server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(BASINS.get(0).placement()).inflate(4),
                    entity -> entity.getItem().is(product())).isEmpty());
            return;
        }
        if (!observation.isDone()) return;
        check("native_loot_returns_the_original_block_exactly_once", observation.join());
        observation = null;
        basinIndex++;
        approachBasin();
        stage = Stage.PLACE;
    }

    private void verifyReopened() throws Exception {
        var server = minecraft.getSingleplayerServer();
        var basin = BASINS.get(1);
        if (server == null || minecraft.player == null || minecraft.level == null || minecraft.screen != null
            || !minecraft.level.getBlockState(basin.placement()).is(EIOBlocks.INDUSTRIAL_INSULATION.get())) return;
        check("both_drained_basins_and_placed_block_survive_reopening", BASINS.stream().allMatch(test -> test.isCleared(minecraft.level))
            && minecraft.level.isEmptyBlock(BASINS.get(0).placement()) && minecraft.player.getInventory().countItem(product()) == 0);
        capture("insulation-reopened");
        observation = server.submit(() -> {
            var level = server.overworld();
            boolean placed = level.setBlock(basin.flowing(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            return placed && basin.isCleared(level) && level.getBlockState(basin.placement()).is(EIOBlocks.INDUSTRIAL_INSULATION.get());
        });
        refillTick = minecraft.player.tickCount;
        stage = Stage.REFILL;
    }

    private void verifyRefill() {
        if (!observation.isDone() || minecraft.player.tickCount - refillTick < 10) return;
        check("reopened_insulation_drains_a_new_adjacent_source_on_neighbor_update", observation.join()
            && BASINS.get(1).isCleared(minecraft.level));
        stage = Stage.DONE;
        minecraft.level.disconnect();
        minecraft.clearLevel(new TitleScreen());
        check("integrated_world_stopped", minecraft.getSingleplayerServer() == null);
        finish(null);
    }

    private void quickMove(PrimitiveAlloySmelterMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check("silent_hidden_capture_" + name, Boolean.TRUE.equals(result.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyUp.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete(insulation ? "client_industrial_insulation" : "client_primitive_smelter", result);
    }
}

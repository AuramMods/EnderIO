package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientBlockDetectorSmoke {
    private enum Stage {
        TITLE, JOIN, LOCKED, TABLE, RECIPE, RESULT, TAKE, PLACE, PLACED, POWERED, UNPOWERED, MINE, MINING,
        PICKUP, REPLACE, REPLACED, DONE
    }

    private static final Craft RECIPE = Craft.shaped("block_detector", 1, "SSSRGRSSS",
        Map.of('S', "minecraft:stone", 'R', "minecraft:redstone", 'G', "grains_of_infinity"));
    private static final Direction[] FACES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
    private static final BlockPos TABLE = new BlockPos(4370, 80, 8);
    private static final int FIRST_X = 4376;
    private static final int SPACING = 8;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int settleBy = -1;
    private int miningTick;
    private int index;

    ClientBlockDetectorSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Block Detector stopped at " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("native_dedicated_survival", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.LOCKED;
                }
                case LOCKED -> {
                    if (!ready("no-grains") || player.getInventory().countItem(Items.REDSTONE) != 12) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                    check("detector_recipe_is_locked_without_grains", !player.getRecipeBook().contains(recipe));
                    signal("locked");
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!settled("table", Vec3.atBottomCenterOf(TABLE.south(2)))) return;
                    player.getInventory().selected = 8;
                    check("native_workshop_opens", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, TAKE -> craft();
                case PLACE -> {
                    if (!settled("place-" + face(), stand(index))) return;
                    int slot = hotbarDetector();
                    if (slot < 0) throw new AssertionError("No original detector for " + face());
                    player.getInventory().selected = slot;
                    BlockPos pos = position(index);
                    boolean vertical = FACES[index].getAxis().isVertical();
                    check("native_placement_" + face(), use(vertical ? pos.east() : pos.below(), vertical ? Direction.WEST : Direction.UP));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(position(index)).is(EIOBlocks.BLOCK_DETECTOR.get())) return;
                    check("native_placement_faces_" + face(),
                        minecraft.level.getBlockState(position(index)).getValue(BlockStateProperties.FACING) == FACES[index]);
                    signal("placed-" + face());
                    stage = Stage.POWERED;
                }
                case POWERED -> {
                    if (!ready("powered-" + face()) || !state(index, true)) return;
                    verifySignalAndModel(true);
                    capture("detector-" + face() + "-powered");
                    signal("saw-powered-" + face());
                    stage = Stage.UNPOWERED;
                }
                case UNPOWERED -> {
                    if (!ready("unpowered-" + face()) || !state(index, false)) return;
                    verifySignalAndModel(false);
                    capture("detector-" + face() + "-unpowered");
                    signal("saw-unpowered-" + face());
                    stage = ++index == FACES.length ? Stage.MINE : Stage.PLACE;
                }
                case MINE -> {
                    if (!settled("mine", stand(0)) || !player.getInventory().getItem(0).is(Items.DIAMOND_PICKAXE)) return;
                    player.getInventory().selected = 0;
                    check("native_survival_mining_starts", minecraft.gameMode.startDestroyBlock(position(0), Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(position(0))) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(position(0), Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    int slot = hotbarDetector();
                    if (slot < 0) return;
                    var stack = player.getInventory().getItem(slot);
                    check("mining_returns_one_untagged_detector", stack.getCount() == 1 && !stack.hasTag());
                    signal("picked-up");
                    stage = Stage.REPLACE;
                }
                case REPLACE -> {
                    if (!settled("replace", stand(0))) return;
                    int slot = hotbarDetector();
                    check("the_original_drop_replants", slot >= 0);
                    player.getInventory().selected = slot;
                    check("normal_survival_replacement", use(position(0).below(), Direction.UP));
                    signal("replaced");
                    stage = Stage.REPLACED;
                }
                case REPLACED -> {
                    if (!ready("finish") || !state(0, false) || hotbarDetector() >= 0) return;
                    check("server_syncs_the_replanted_detector_and_consumed_item", true);
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var player = minecraft.player;
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("six_grains_unlock_the_stock_recipe",
                    player.getInventory().countItem(EIOItems.GRAINS_OF_INFINITY.get()) == 6);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, true);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var result = menu.getSlot(0).getItem();
                if (!result.is(EIOBlocks.BLOCK_DETECTOR.get().asItem()) || grid.stream().anyMatch(slot -> !slot.hasItem())) return;
                check("stock_recipe_book_arrays_the_exact_nine_ingredients",
                    RECIPE.matchesGrid(grid, 6) && result.getCount() == 1 && !result.hasTag());
                capture("detector-stock-craft");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (grid.stream().anyMatch(Slot::hasItem) || hotbarDetector() < 0) return;
                check("six_blank_detectors_are_collected", player.getInventory().countItem(EIOBlocks.BLOCK_DETECTOR.get().asItem()) == 6);
                player.closeContainer();
                signal("crafted");
                stage = Stage.PLACE;
            }
            default -> {}
        }
    }

    private boolean state(int fixture, boolean powered) {
        var state = minecraft.level.getBlockState(position(fixture));
        return state.is(EIOBlocks.BLOCK_DETECTOR.get()) && state.getValue(BlockStateProperties.FACING) == FACES[fixture]
            && state.getValue(BlockStateProperties.POWERED) == powered;
    }

    private void verifySignalAndModel(boolean powered) {
        var level = minecraft.level;
        var pos = position(index);
        var state = level.getBlockState(pos);
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var front = FACES[index];
        var frontSprite = id("block/block_detector_front");
        var backSprite = id(powered ? "block/block_detector_back_on" : "block/block_detector_back");
        check("native_baked_input_and_output_faces_" + face() + "_" + powered,
            model.getQuads(state, front, RandomSource.create(42)).stream()
                .anyMatch(quad -> quad.getSprite().contents().name().equals(frontSprite))
            && model.getQuads(state, front.getOpposite(), RandomSource.create(42)).stream()
                .anyMatch(quad -> quad.getSprite().contents().name().equals(backSprite)));
        for (Direction side : Direction.values()) {
            int expected = powered && side == FACES[index] ? 15 : 0;
            check("native_signal_" + face() + "_" + powered + "_" + side.getName(),
                state.getSignal(level, pos, side) == expected && state.getDirectSignal(level, pos, side) == expected);
        }
    }

    private static BlockPos position(int fixture) {
        return new BlockPos(FIRST_X + fixture * SPACING, 80, 8);
    }

    private static Vec3 stand(int fixture) {
        var pos = position(fixture);
        var face = FACES[fixture];
        return Vec3.atBottomCenterOf(switch (face) {
            case UP -> pos.south(2).below(3);
            case DOWN -> pos.south(2).above(2);
            default -> pos.relative(face.getOpposite(), 3).relative(face.getClockWise());
        });
    }

    private int hotbarDetector() {
        for (int slot = 0; slot < 9; slot++) {
            if (minecraft.player.getInventory().getItem(slot).is(EIOBlocks.BLOCK_DETECTOR.get().asItem())) return slot;
        }
        return -1;
    }

    private boolean settled(String marker, Vec3 stand) {
        var player = minecraft.player;
        if (!ready(marker) || minecraft.screen != null || player.distanceToSqr(stand) > 0.15) {
            settleBy = -1;
            return false;
        }
        if (settleBy < 0) settleBy = player.tickCount + 6;
        if (player.tickCount < settleBy) return false;
        settleBy = -1;
        return true;
    }

    private String face() {
        return FACES[index].getName();
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Block Detector", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String marker) {
        return Files.exists(output.resolve("server-" + marker));
    }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_block_detector", result);
    }
}

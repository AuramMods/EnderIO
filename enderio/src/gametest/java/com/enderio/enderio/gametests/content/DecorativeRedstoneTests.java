package com.enderio.enderio.gametests.content;

import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DecorativeRedstoneTests {
    private record LeverTier(String name, int ticks) {
        Block block(boolean inverted) {
            return blockById("resetting_lever_" + name + (inverted ? "_inv" : ""));
        }
    }

    private static final List<LeverTier> LEVERS = List.of(
        new LeverTier("five", 100), new LeverTier("ten", 200), new LeverTier("thirty", 600),
        new LeverTier("sixty", 1200), new LeverTier("three_hundred", 6000));

    private record Plate(String id, int items, int hostile, int player, boolean silent) {
        Block block() {
            return blockById(id);
        }
    }

    private static final List<Plate> PLATES = List.of(
        new Plate("dark_steel_pressure_plate", 0, 0, 15, false),
        new Plate("silent_dark_steel_pressure_plate", 0, 0, 15, true),
        new Plate("soularium_pressure_plate", 0, 15, 0, false),
        new Plate("silent_soularium_pressure_plate", 0, 15, 0, true),
        itemPlate("oak"), itemPlate("acacia"), itemPlate("dark_oak"), itemPlate("spruce"),
        itemPlate("birch"), itemPlate("jungle"), itemPlate("crimson"), itemPlate("warped"),
        itemPlate("stone"), itemPlate("polished_blackstone"),
        new Plate("silent_heavy_weighted_pressure_plate", 2, 1, 1, true),
        new Plate("silent_light_weighted_pressure_plate", 12, 1, 1, true));

    @GameTest(timeoutTicks = 6100)
    public static void registeredResettingLeversKeepTheirExactPulseLengths(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(16, 5, 8));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.assertValueEqual(EIOBlocks.RESETTING_LEVERS.size(), 10, "registered resetting lever count");
            var level = helper.getLevel();
            var player = FakePlayerFactory.getMinecraft(level);
            for (int i = 0; i < LEVERS.size(); i++) {
                var tier = LEVERS.get(i);
                for (boolean inverted : new boolean[]{false, true}) {
                    var pos = leverPos(i, inverted);
                    helper.setBlock(pos.below(), Blocks.STONE);
                    helper.setBlock(pos, tier.block(inverted).defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
                    assertLever(helper, pos, inverted, false);
                    var absolute = helper.absolutePos(pos);
                    var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
                    player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 1.5);
                    helper.assertValueEqual(player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.OFF_HAND, hit),
                        InteractionResult.PASS, tier.name + " ignores the off hand");
                    assertLever(helper, pos, inverted, false);
                    helper.assertTrue(player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit)
                        .consumesAction(), tier.name + " accepts the normal use action");
                    assertLever(helper, pos, inverted, true);
                }
            }
            var timing = helper.startSequence();
            int previous = 0;
            for (int i = 0; i < LEVERS.size(); i++) {
                int tier = i;
                int ticks = LEVERS.get(i).ticks;
                timing.thenExecuteAfter(ticks - 1 - previous, () -> assertLeverPair(helper, tier, true))
                    .thenExecuteAfter(1, () -> assertLeverPair(helper, tier, false));
                previous = ticks;
            }
            timing.thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 170)
    public static void registeredPlatesFilterActualEntitiesAndRelease(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(13, 6, 13));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            for (int i = 0; i < PLATES.size(); i++) {
                var pos = platePos(i);
                helper.setBlock(pos.below(), Blocks.STONE);
                helper.setBlock(pos, PLATES.get(i).block());
            }
            var level = helper.getLevel();
            var entities = new ArrayList<Entity>();
            var sounds = new PlateSounds(level);
            helper.startSequence()
                .thenExecute(() -> {
                    for (int i = 0; i < PLATES.size(); i++) {
                        var pos = helper.absolutePos(platePos(i));
                        int count = PLATES.get(i).items == 12 || PLATES.get(i).items == 2 ? 12 : 1;
                        for (int j = 0; j < count; j++) {
                            var item = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                                new ItemStack(Items.COBBLESTONE));
                            item.setNeverPickUp();
                            item.setNoGravity(true);
                            item.setDeltaMovement(Vec3.ZERO);
                            level.addFreshEntity(item);
                            entities.add(item);
                        }
                    }
                })
                .thenExecuteAfter(15, () -> {
                    for (int i = 0; i < PLATES.size(); i++) assertPlate(helper, i, PLATES.get(i).items);
                    discardEntities(level, entities);
                })
                .thenExecuteAfter(25, () -> {
                    for (int i = 0; i < PLATES.size(); i++) assertPlate(helper, i, 0);
                    for (int i = 0; i < 4; i++) {
                        var cow = EntityType.COW.create(level);
                        cow.setNoAi(true);
                        standOn(level, helper.absolutePos(platePos(i)), cow, entities);
                        cow.move(MoverType.SELF, new Vec3(0.0625, 0, 0));
                    }
                })
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < 4; i++) assertPlate(helper, i, 0);
                    discardEntities(level, entities);
                    for (int i = 0; i < 4; i++) {
                        var hostile = EntityType.ZOMBIE.create(level);
                        hostile.setNoAi(true);
                        hostile.setPersistenceRequired();
                        standOn(level, helper.absolutePos(platePos(i)), hostile, entities);
                        hostile.move(MoverType.SELF, new Vec3(0.0625, 0, 0));
                    }
                })
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < 4; i++) assertPlate(helper, i, PLATES.get(i).hostile);
                    discardEntities(level, entities);
                })
                .thenExecuteAfter(25, () -> {
                    for (int i = 0; i < 4; i++) {
                        assertPlate(helper, i, 0);
                        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PlateTest" + i));
                        var pos = helper.absolutePos(platePos(i));
                        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        level.addNewPlayer(player);
                        entities.add(player);
                        // Forge fake players do not move or tick through block collisions themselves.
                        level.getBlockState(pos).entityInside(level, pos, player);
                        assertPlate(helper, i, PLATES.get(i).player);
                    }
                    discardEntities(level, entities);
                })
                .thenExecuteAfter(25, () -> {
                    for (int i = 0; i < PLATES.size(); i++) {
                        assertPlate(helper, i, 0);
                        verifyPlateSounds(helper, i, sounds);
                    }
                })
                .thenSucceed();
        });
    }

    @GameTest
    public static void decorativeRedstoneRecipesAndLootUseRegisteredBlocks(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 4, 3));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            var blocks = new ArrayList<Block>();
            for (var tier : LEVERS) {
                blocks.add(tier.block(false));
                blocks.add(tier.block(true));
            }
            PLATES.forEach(plate -> blocks.add(plate.block()));
            var player = FakePlayerFactory.getMinecraft(level);
            var registry = level.registryAccess();
            var recipes = level.getRecipeManager();
            var ids = blocks.stream().map(BuiltInRegistries.BLOCK::getKey).toList();
            for (var id : ids) {
                helper.assertTrue(recipes.byKey(id).isPresent(), id + " crafting recipe is loaded");
            }
            var matching = recipes.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(recipe -> ids.contains(BuiltInRegistries.ITEM.getKey(recipe.getResultItem(registry).getItem())))
                .toList();
            for (var recipe : matching) {
                var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
                int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
                var ingredients = recipe.getIngredients();
                for (int i = 0; i < ingredients.size(); i++) {
                    var choices = ingredients.get(i).getItems();
                    if (choices.length > 0) grid.setItem(i / width * 3 + i % width, choices[0].copy());
                }
                helper.assertTrue(recipe.matches(grid, level), recipe.getId() + " matches installed ingredients");
                helper.assertTrue(ItemStack.matches(recipe.getResultItem(registry), recipe.assemble(grid, registry)),
                    recipe.getId() + " crafts its registered result");
            }
            helper.assertValueEqual(matching.size(), 44, "shipped decorative redstone crafts");
            var pos = helper.absolutePos(new BlockPos(1, 2, 1));
            for (var block : blocks) {
                var drops = Block.getDrops(block.defaultBlockState(), level, pos, null, player, new ItemStack(Items.DIAMOND_PICKAXE));
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1,
                    BuiltInRegistries.BLOCK.getKey(block) + " drops one of its own item");
            }
            helper.startSequence().thenSucceed();
        });
    }

    private static Block blockById(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("enderio", id));
    }

    private static Plate itemPlate(String type) {
        return new Plate("silent_" + type + "_pressure_plate", 15, 15, 15, true);
    }

    private static BlockPos leverPos(int index, boolean inverted) {
        return new BlockPos(1 + index * 3, 2, inverted ? 5 : 1);
    }

    private static BlockPos platePos(int index) {
        return new BlockPos(1 + index % 4 * 3, 2, 1 + index / 4 * 3);
    }

    private static void assertLeverPair(EnderGameTestHelper helper, int tier, boolean on) {
        assertLever(helper, leverPos(tier, false), false, on);
        assertLever(helper, leverPos(tier, true), true, on);
    }

    private static void assertLever(EnderGameTestHelper helper, BlockPos pos, boolean inverted, boolean on) {
        var state = helper.getBlockState(pos);
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        helper.assertValueEqual(state.getValue(LeverBlock.POWERED), on, name + " handle");
        int signal = on != inverted ? 15 : 0;
        helper.assertValueEqual(state.getSignal(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH), signal, name + " weak output");
        helper.assertValueEqual(state.getDirectSignal(helper.getLevel(), helper.absolutePos(pos), Direction.UP), signal, name + " strong output");
    }

    private static void assertPlate(EnderGameTestHelper helper, int index, int power) {
        var pos = platePos(index);
        var state = helper.getBlockState(pos);
        var absolute = helper.absolutePos(pos);
        var level = helper.getLevel();
        helper.assertValueEqual(state.getSignal(level, absolute, Direction.NORTH), power, PLATES.get(index).id + " weak output");
        helper.assertValueEqual(state.getDirectSignal(level, absolute, Direction.UP), power, PLATES.get(index).id + " strong output");
    }

    private static void standOn(ServerLevel level, BlockPos pos, Entity entity, List<Entity> entities) {
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
        entities.add(entity);
    }

    private static void discardEntities(ServerLevel level, List<Entity> entities) {
        for (var entity : entities) {
            if (entity instanceof ServerPlayer player) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            else entity.discard();
        }
        entities.clear();
    }

    private static void verifyPlateSounds(EnderGameTestHelper helper, int index, PlateSounds probe) {
        var plate = PLATES.get(index);
        var level = helper.getLevel();
        var pos = helper.absolutePos(platePos(index));
        var occupants = new ArrayList<Entity>();
        Entity trigger;
        if (plate.player > 0 && plate.items == 0) {
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PlateSound" + index));
            player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            level.addNewPlayer(player);
            occupants.add(player);
            trigger = player;
        } else {
            trigger = (plate.items == 0 ? EntityType.ZOMBIE : EntityType.COW).create(level);
            standOn(level, pos, trigger, occupants);
        }
        probe.at = pos;
        probe.events.clear();
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            level.getBlockState(pos).entityInside(level, pos, trigger);
            discardEntities(level, occupants);
            level.getBlockState(pos).tick(level, pos, level.getRandom());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            discardEntities(level, occupants);
        }
        var clicks = plate.silent ? List.of(SoundEvents.EMPTY, SoundEvents.EMPTY)
            : List.of(BlockSetType.IRON.pressurePlateClickOn(), BlockSetType.IRON.pressurePlateClickOff());
        helper.assertValueEqual(probe.events, clicks, plate.id + " press and release sounds");
    }

    private static final class PlateSounds {
        private final ServerLevel level;
        private final List<SoundEvent> events = new ArrayList<>();
        private BlockPos at;

        PlateSounds(ServerLevel level) {
            this.level = level;
        }

        @SubscribeEvent
        public void onSound(PlayLevelSoundEvent.AtPosition event) {
            if (event.getLevel() == level && BlockPos.containing(event.getPosition()).equals(at) && event.getSound() != null) {
                events.add(event.getSound().value());
            }
        }
    }
}

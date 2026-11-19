package com.enderio.enderio.gametests.content;

import com.enderio.enderio.content.glass.GlassCollisionPredicate;
import com.enderio.enderio.content.glass.GlassIdentifier;
import com.enderio.enderio.content.glass.GlassLighting;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.UUID;

public final class GlassTests {
    private static final BlockPos GLASS = new BlockPos(2, 1, 1);
    private static final BlockPos CLEAR_GLASS_SHAFT = new BlockPos(2, 4, 2);
    private static final BlockPos QUARTZ_SHAFT = new BlockPos(20, 4, 2);

    @GameTest
    public static void registeredGlassVariantsFilterEntityMovement(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 5, 3));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            var absolute = helper.absolutePos(GLASS);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GlassTest"));
            var zombie = EntityType.ZOMBIE.create(level);
            var cow = EntityType.COW.create(level);
            var item = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.STICK));

            helper.assertValueEqual(EIOBlocks.GLASS_BLOCKS.size(), 42, "glass family count");
            EIOBlocks.GLASS_BLOCKS.forEach((identifier, family) -> {
                var blocks = family.getAllBlocks().toList();
                helper.assertValueEqual(blocks.size(), 17, identifier.glassName() + " clear and dyed variants");
                for (var block : blocks) {
                    String id = block.getId().toString();
                    helper.setBlock(GLASS, block.get());
                    var state = level.getBlockState(absolute);
                    helper.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(level, absolute)),
                        id + " has a full collision shape without an entity");
                    helper.assertTrue(Block.isShapeFullBlock(state.getShape(level, absolute)),
                        id + " retains a full targeting outline");
                    var rule = identifier.collisionPredicate();
                    assertMovement(helper, absolute, player, rule == GlassCollisionPredicate.PLAYERS_PASS
                        || rule == GlassCollisionPredicate.MONSTER_BLOCK || rule == GlassCollisionPredicate.ANIMALS_BLOCK, id);
                    assertMovement(helper, absolute, zombie, rule == GlassCollisionPredicate.MONSTER_PASS
                        || rule == GlassCollisionPredicate.PLAYERS_BLOCK || rule == GlassCollisionPredicate.ANIMALS_BLOCK, id);
                    assertMovement(helper, absolute, cow, rule == GlassCollisionPredicate.ANIMALS_PASS
                        || rule == GlassCollisionPredicate.PLAYERS_BLOCK || rule == GlassCollisionPredicate.MONSTER_BLOCK, id);
                    assertMovement(helper, absolute, item, rule == GlassCollisionPredicate.PLAYERS_BLOCK
                        || rule == GlassCollisionPredicate.MONSTER_BLOCK || rule == GlassCollisionPredicate.ANIMALS_BLOCK, id);
                }
            });
            helper.startSequence().thenSucceed();
        });
    }

    private static void assertMovement(EnderGameTestHelper helper, BlockPos pos, Entity entity, boolean passes, String glass) {
        double start = pos.getX() - 0.5;
        entity.moveTo(start, pos.getY(), pos.getZ() + 0.5, 0, 0);
        entity.move(MoverType.SELF, new Vec3(2, 0, 0));
        double expected = passes ? start + 2 : pos.getX() - entity.getBbWidth() / 2.0;
        helper.assertTrue(Math.abs(entity.getX() - expected) < 0.0001,
            glass + " movement for " + EntityType.getKey(entity.getType()) + " expected x=" + expected
                + ", got " + entity.getX());
    }

    @GameTest(timeoutTicks = 200)
    public static void darkAndEnlightenedGlassUpdateBlockLight(GameplayTest test) {
        test.registerGameTestTemplate(() -> {
            var fixture = FixtureBuilder.withSize(23, 7, 5);
            for (BlockPos shaft : List.of(CLEAR_GLASS_SHAFT, QUARTZ_SHAFT)) {
                fixture.set(shaft.getX(), 0, shaft.getZ(), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 5; y++) {
                    for (var side : Direction.Plane.HORIZONTAL) {
                        fixture.set(shaft.getX() + side.getStepX(), y, shaft.getZ() + side.getStepZ(),
                            Blocks.STONE.defaultBlockState());
                    }
                }
            }
            return fixture;
        });
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    helper.setBlock(CLEAR_GLASS_SHAFT.above(), Blocks.GLOWSTONE);
                    helper.setBlock(QUARTZ_SHAFT.above(), Blocks.GLOWSTONE);
                    setShafts(helper, GlassLighting.NONE);
                })
                .thenWaitUntil(() -> assertShaftLight(helper, "plain", 14, 13))
                .thenExecute(() -> setShafts(helper, GlassLighting.BLOCKING))
                .thenWaitUntil(() -> assertShaftLight(helper, "dark", 0, 0))
                .thenExecute(() -> {
                    helper.setBlock(CLEAR_GLASS_SHAFT.above(), Blocks.AIR);
                    helper.setBlock(QUARTZ_SHAFT.above(), Blocks.AIR);
                    setShafts(helper, GlassLighting.EMITTING);
                })
                .thenWaitUntil(() -> assertShaftLight(helper, "enlightened", 15, 14))
                .thenExecute(() -> setShafts(helper, GlassLighting.NONE))
                .thenWaitUntil(() -> assertShaftLight(helper, "plain again", 0, 0))
                .thenSucceed();
        });
    }

    private static void setShafts(EnderGameTestHelper helper, GlassLighting lighting) {
        var clear = new GlassIdentifier(lighting, GlassCollisionPredicate.PLAYERS_PASS, false);
        var quartz = new GlassIdentifier(lighting, GlassCollisionPredicate.MONSTER_BLOCK, true);
        helper.setBlock(CLEAR_GLASS_SHAFT, EIOBlocks.GLASS_BLOCKS.get(clear).COLORS.get(DyeColor.BLUE).get());
        helper.setBlock(QUARTZ_SHAFT, EIOBlocks.GLASS_BLOCKS.get(quartz).CLEAR.get());
    }

    private static void assertShaftLight(EnderGameTestHelper helper, String stage, int atGlass, int belowGlass) {
        var level = helper.getLevel();
        for (BlockPos shaft : List.of(CLEAR_GLASS_SHAFT, QUARTZ_SHAFT)) {
            var pos = helper.absolutePos(shaft);
            String label = stage + " shaft at " + pos.toShortString();
            helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, pos), atGlass, label + " block light at glass");
            helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, pos.below()), belowGlass, label + " block light below");
        }
    }

    @GameTest
    public static void quartzSurvivesTheBlastThatBreaksClearGlass(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(7, 5, 5));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var origin = new BlockPos(3, 2, 2);
            var clear = new GlassIdentifier(GlassLighting.BLOCKING, GlassCollisionPredicate.ANIMALS_PASS, false);
            var quartz = new GlassIdentifier(GlassLighting.BLOCKING, GlassCollisionPredicate.ANIMALS_PASS, true);
            var clearBlock = EIOBlocks.GLASS_BLOCKS.get(clear).COLORS.get(DyeColor.BLUE).get();
            var quartzBlock = EIOBlocks.GLASS_BLOCKS.get(quartz).COLORS.get(DyeColor.BLUE).get();
            helper.setBlock(origin.west(), clearBlock);
            helper.setBlock(origin.east(), quartzBlock);
            var center = Vec3.atCenterOf(helper.absolutePos(origin));
            helper.getLevel().explode(null, center.x, center.y, center.z, 2.0f, Level.ExplosionInteraction.TNT);
            helper.assertBlockPresent(Blocks.AIR, origin.west());
            helper.assertBlockPresent(quartzBlock, origin.east());
            helper.startSequence().thenSucceed();
        });
    }
}

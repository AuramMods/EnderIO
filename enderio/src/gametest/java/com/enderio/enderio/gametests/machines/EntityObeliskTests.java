package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.filters.soul.EnderSoulFilter;
import com.enderio.enderio.content.machines.obelisks.ObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.attractor.AttractorObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.aversion.AversionObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.inhibitor.InhibitorObeliskBlockEntity;
import com.enderio.enderio.content.machines.obelisks.relocator.RelocatorObeliskBlockEntity;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;

public class EntityObeliskTests {
    @GameTest
    public static void nearbyInhibitorDoesNotMaskTheProtectedOrigin(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(39, 3, 4)
            .set(1, 0, 1, EIOBlocks.INHIBITOR_OBELISK.get().defaultBlockState())
            .set(35, 0, 1, EIOBlocks.INHIBITOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var origin = helper.getBlockEntity(1, 1, 1, InhibitorObeliskBlockEntity.class);
            var other = helper.getBlockEntity(35, 1, 1, InhibitorObeliskBlockEntity.class);
            power(origin, 4);
            power(other, 1);
            var pig = mob(helper, EntityType.PIG, 2, 1, 1);
            var target = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(35, 6, 1)));
            helper.assertTrue(ForgeEventFactory.onChorusFruitTeleport(pig, target.x, target.y, target.z).isCanceled(),
                "a destination outside its obelisk must still honor the protected origin in another chunk");
            var settings = origin.saveWithoutMetadata();
            settings.putString(MachineNBTKeys.REDSTONE_CONTROL, "never_active");
            origin.load(settings);
            helper.assertTrue(!ForgeEventFactory.onChorusFruitTeleport(pig, target.x, target.y, target.z).isCanceled(),
                "an inactive origin must allow the same teleport");
            var destination = Vec3.atCenterOf(other.getBlockPos().above());
            helper.assertTrue(ForgeEventFactory.onChorusFruitTeleport(pig, destination.x, destination.y, destination.z).isCanceled(),
                "a protected destination blocks a teleport from an inactive origin");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void inhibitorRequiresItsCapacitorAndPaysTheConfiguredUpkeep(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 3, 3)
            .set(1, 0, 1, EIOBlocks.INHIBITOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var obelisk = helper.getBlockEntity(1, 1, 1, InhibitorObeliskBlockEntity.class);
            helper.assertTrue(!obelisk.isActive(), "capacitor-free inhibitor must be inactive");
            power(obelisk, 4);
            int cost = obelisk.getPerTickEnergyCost();
            helper.assertTrue(cost > 0, "positive range has a positive energy cost");
            obelisk.getEnergyStorage().setEnergyStored(cost - 1);
            helper.assertTrue(!obelisk.isActive(), "short of a full tick's upkeep");
            obelisk.getEnergyStorage().setEnergyStored(cost);
            helper.assertTrue(obelisk.isActive(), "exact upkeep can run the machine");
            obelisk.serverTick();
            helper.assertValueEqual(obelisk.getEnergyStorage().getEnergyStored(), 0, "last full tick is paid exactly");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void aversionChecksNaturalCauseFilterAndLiveRange(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(14, 3, 5)
            .set(2, 1, 2, EIOBlocks.AVERSION_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 2, 2, AversionObeliskBlockEntity.class);
            power(machine, 8);
            filter(machine, EntityType.PIG, false);
            helper.assertTrue(!accepts(helper, EntityType.PIG, 7, 2, MobSpawnType.NATURAL), "matching natural pig is blocked");
            helper.assertTrue(accepts(helper, EntityType.COW, 7, 2, MobSpawnType.NATURAL), "nonmatching natural cow is admitted");
            helper.assertTrue(accepts(helper, EntityType.PIG, 7, 2, MobSpawnType.SPAWNER), "spawner pigs are admitted");
            machine.setActionRange(new ActionRange(2, false));
            helper.assertTrue(accepts(helper, EntityType.PIG, 7, 2, MobSpawnType.NATURAL), "reducing the range releases old positions");
            machine.setActionRange(new ActionRange(8, false));
            filter(machine, EntityType.PIG, true);
            helper.assertTrue(accepts(helper, EntityType.PIG, 7, 2, MobSpawnType.NATURAL), "deny list excludes pigs from the effect");
            helper.assertTrue(!accepts(helper, EntityType.COW, 7, 2, MobSpawnType.NATURAL), "deny list still affects other mobs");
            helper.destroyBlock(new BlockPos(2, 2, 2));
            helper.assertTrue(accepts(helper, EntityType.COW, 7, 2, MobSpawnType.NATURAL), "breaking the block unregisters its area");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void relocatorKeepsTheOriginalSpawnWhenItsDestinationIsSealed(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(20, 5, 13)
            .set(6, 1, 6, EIOBlocks.RELOCATOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(6, 2, 6, RelocatorObeliskBlockEntity.class);
            power(machine, 14);
            filter(machine, EntityType.ZOMBIE, false);
            for (int y = 1; y <= 4; y++) {
                for (int x = 1; x <= 11; x++) {
                    for (int z = 1; z <= 11; z++) {
                        if (x != 6 || y != 2 || z != 6) helper.setBlock(x, y, z, Blocks.STONE);
                    }
                }
            }
            var zombie = mob(helper, EntityType.ZOMBIE, 16, 2, 6);
            helper.assertTrue(machine.getAABB().contains(zombie.position()), "fixture spawn is in configured range");
            Vec3 original = zombie.position();
            spawn(helper, zombie, MobSpawnType.NATURAL);
            helper.assertValueEqual(zombie.position(), original, "sealed destination keeps the native spawn position");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void relocatorMovesMatchingNaturalMobsOntoItsOpenPlatform(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(20, 4, 13)
            .set(6, 1, 6, EIOBlocks.RELOCATOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(6, 2, 6, RelocatorObeliskBlockEntity.class);
            power(machine, 14);
            filter(machine, EntityType.ZOMBIE, false);
            var pig = mob(helper, EntityType.PIG, 16, 2, 6);
            Vec3 original = pig.position();
            spawn(helper, pig, MobSpawnType.NATURAL);
            helper.assertValueEqual(pig.position(), original, "nonmatching pig keeps its position");
            var zombie = mob(helper, EntityType.ZOMBIE, 16, 2, 6);
            spawn(helper, zombie, MobSpawnType.NATURAL);
            helper.assertTrue(zombie.position().distanceToSqr(Vec3.atCenterOf(machine.getBlockPos())) < 50,
                "matching zombie moved into the obelisk's destination area");
            helper.assertTrue(NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, helper.getLevel(),
                zombie.blockPosition(), EntityType.ZOMBIE)
                && helper.getLevel().noCollision(zombie, zombie.getBoundingBox()), "destination has ground support and body clearance");
            helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Forge accepts the relocated spawn");
            zombie.discard();
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 100)
    public static void attractorStartsNavigationOnlyForMatchingMobs(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(13, 3, 9)
            .set(2, 1, 4, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 2, 4, AttractorObeliskBlockEntity.class);
            power(machine, 8);
            filter(machine, EntityType.PIG, false);
            var pig = mob(helper, EntityType.PIG, 8, 2, 4);
            var cow = mob(helper, EntityType.COW, 8, 2, 6);
            helper.getLevel().addFreshEntity(pig);
            helper.getLevel().addFreshEntity(cow);
            // Match the ground state established by the first native movement tick.
            pig.setOnGround(true);
            cow.setOnGround(true);
            Vec3 center = Vec3.atCenterOf(machine.getBlockPos());
            double initialDistance = pig.position().distanceToSqr(center);
            machine.serverTick();
            helper.assertTrue(pig.getNavigation().isInProgress() && cow.getNavigation().isDone(),
                "Attractor routes the matched pig and leaves the cow alone");
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                pig.position().distanceToSqr(center) < initialDistance / 2, "pig approaches using native pathfinding"))
                .thenExecute(() -> {
                    pig.discard();
                    cow.discard();
                }).thenSucceed();
        });
    }

    private static FixtureBuilder floor(int width, int height, int depth) {
        var builder = FixtureBuilder.withSize(width, height, depth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) builder.set(x, 0, z, Blocks.STONE.defaultBlockState());
        }
        return builder;
    }

    private static void power(ObeliskBlockEntity<?> obelisk, int range) {
        obelisk.getInventory().setStackInSlot(obelisk.getCapacitorSlotIndex(), new ItemStack(EIOItems.BASIC_CAPACITOR.get()));
        obelisk.setActionRange(new ActionRange(range, false));
        obelisk.getEnergyStorage().setEnergyStored(10_000);
    }

    private static void filter(ObeliskBlockEntity<?> machine, EntityType<?> type, boolean denyList) {
        var stack = new ItemStack(EIOItems.BASIC_SOUL_FILTER.get());
        EIODataComponents.SOUL_FILTER.set(stack, new EnderSoulFilter(List.of(Soul.of(type)), denyList, false));
        ObeliskBlockEntity.FILTER.setStackInSlot(machine, stack);
    }

    private static <T extends Mob> T mob(EnderGameTestHelper helper, EntityType<T> type, int x, int y, int z) {
        var mob = type.create(helper.getLevel());
        var pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, y, z)));
        mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
        return mob;
    }

    private static void spawn(EnderGameTestHelper helper, Mob mob, MobSpawnType reason) {
        var level = helper.getLevel();
        ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null, null);
    }

    private static boolean accepts(EnderGameTestHelper helper, EntityType<? extends Mob> type, int x, int z, MobSpawnType reason) {
        var mob = mob(helper, type, x, 2, z);
        spawn(helper, mob, reason);
        boolean admitted = helper.getLevel().addFreshEntity(mob);
        mob.discard();
        return admitted;
    }
}

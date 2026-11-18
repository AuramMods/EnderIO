package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.config.machines.MachinesConfig;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.util.FakePlayer;

import java.util.EnumSet;
import java.util.List;

public class EntityObeliskTests {
    @GameTest
    public static void aversionPreservesAnExplicitZeroRangeOnWorldLoad(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 3, 3)
            .set(1, 0, 1, EIOBlocks.AVERSION_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(1, 1, 1, AversionObeliskBlockEntity.class);
            power(machine, 4);
            machine.setActionRange(new ActionRange(0, false));
            var saved = machine.saveWithFullMetadata();
            helper.assertTrue(saved.contains(MachineNBTKeys.ACTION_RANGE), "explicit zero is different from an unconfigured range");

            var legacy = saved.copy();
            legacy.remove(MachineNBTKeys.ACTION_RANGE);
            legacy.putInt("Range", 0);
            legacy.putBoolean("RangeVisible", true);
            var port = reloadAversion(saved, machine);
            var migrated = reloadAversion(legacy, machine);
            helper.assertTrue(port.isCapacitorInstalled() && migrated.isCapacitorInstalled(), "loaded machines have their capacitor");
            helper.assertValueEqual(port.getActionRange(), new ActionRange(0, false), "native zero remains zero after capacitor load");
            helper.assertValueEqual(migrated.getActionRange(), new ActionRange(0, true), "legacy zero and range visibility are restored");
            helper.startSequence().thenSucceed();
        });
    }

    private static AversionObeliskBlockEntity reloadAversion(CompoundTag saved, AversionObeliskBlockEntity original) {
        var loaded = (AversionObeliskBlockEntity) BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
        loaded.setLevel(original.getLevel());
        return loaded;
    }

    @GameTest
    public static void aversionUsesTheMigratedEntityTagsForNaturalSpawns(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(12, 3, 5)
            .set(2, 1, 2, EIOBlocks.AVERSION_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 2, 2, AversionObeliskBlockEntity.class);
            power(machine, 6);
            var named = mob(helper, EntityType.PIG, 6, 2, 2);
            named.setCustomName(Component.literal("Legacy Marker"));

            var legacyEntry = new CompoundTag();
            legacyEntry.put("Entity", named.serializeNBT());
            var entries = new ListTag();
            entries.add(legacyEntry);
            var tag = new CompoundTag();
            tag.put("EntityEntries", entries);
            tag.putBoolean("IsNbt", true);
            tag.putBoolean("IsInverted", true);
            var saved = new CompoundTag();
            saved.putString("id", "enderio:entity_filter");
            saved.putByte("Count", (byte) 1);
            saved.put("tag", tag);
            ObeliskBlockEntity.FILTER.setStackInSlot(machine, ItemStack.of(saved));

            spawn(helper, named, MobSpawnType.NATURAL);
            helper.assertTrue(helper.getLevel().addFreshEntity(named), "the migrated deny list admits the exact tagged pig");
            named.discard();
            helper.assertTrue(!accepts(helper, EntityType.PIG, 6, 2, MobSpawnType.NATURAL),
                "the same type without the saved name is still blocked");
            helper.assertTrue(!accepts(helper, EntityType.COW, 6, 2, MobSpawnType.NATURAL),
                "another unlisted entity is still blocked");
            helper.startSequence().thenSucceed();
        });
    }

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

    @GameTest
    public static void attractorReleasesPhantomsAndSlimesWhenControlsChange(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(12, 5, 11)
            .set(2, 1, 5, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 2, 5, AttractorObeliskBlockEntity.class);
            power(machine, 8);
            filter(machine, false, EntityType.PHANTOM, EntityType.SLIME);
            var phantom = mob(helper, EntityType.PHANTOM, 6, 3, 3);
            var slime = mob(helper, EntityType.SLIME, 6, 2, 7);
            var originalTarget = mob(helper, EntityType.PIG, 9, 2, 5);
            phantom.setTarget(originalTarget);
            helper.getLevel().addFreshEntity(phantom);
            helper.getLevel().addFreshEntity(slime);
            helper.getLevel().addFreshEntity(originalTarget);
            try {
                machine.serverTick();
                var decoy = phantom.getTarget();
                helper.assertTrue(decoy instanceof FakePlayer && slime.getTarget() == decoy, "both mobs target the Attractor");

                machine.setRedstoneControl(RedstoneControl.NEVER_ACTIVE);
                machine.serverTick();
                helper.assertTrue(phantom.getTarget() == originalTarget && slime.getTarget() == null,
                    "disabled Attractor restores the previous target and clears mobs with no target");

                machine.setRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
                machine.serverTick();
                filter(machine, EntityType.SLIME, false);
                machine.serverTick();
                helper.assertTrue(phantom.getTarget() == originalTarget && slime.getTarget() instanceof FakePlayer,
                    "replacing the filter releases only the unmatched mob");
                machine.onChunkUnloaded();
                helper.assertTrue(slime.getTarget() == null, "chunk unload releases the remaining decoy target");
            } finally {
                phantom.discard();
                slime.discard();
                originalTarget.discard();
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void attractorPullsGhastsOnlyWithCollisionClearance(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(13, 9, 11)
            .set(2, 2, 5, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 3, 5, AttractorObeliskBlockEntity.class);
            power(machine, 8);
            filter(machine, EntityType.GHAST, false);
            var ghast = mob(helper, EntityType.GHAST, 8, 3, 5);
            helper.getLevel().addFreshEntity(ghast);
            try {
                var toward = Vec3.atCenterOf(machine.getBlockPos()).subtract(ghast.position());
                machine.serverTick();
                helper.assertTrue(ghast.getDeltaMovement().dot(toward) > 0, "Ghast gains motion toward the Attractor");
                for (int z = 3; z <= 7; z++) {
                    for (int y = 3; y <= 7; y++) helper.setBlock(5, y, z, Blocks.STONE);
                }
                ghast.setDeltaMovement(Vec3.ZERO);
                machine.serverTick();
                helper.assertValueEqual(ghast.getDeltaMovement(), Vec3.ZERO, "a stone wall blocks the pull");
            } finally {
                ghast.discard();
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void overlappingAttractorsTransferControlWithoutKeepingAStaleDecoy(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(13, 5, 11)
            .set(2, 1, 4, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState())
            .set(4, 1, 6, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var first = helper.getBlockEntity(2, 2, 4, AttractorObeliskBlockEntity.class);
            var second = helper.getBlockEntity(4, 2, 6, AttractorObeliskBlockEntity.class);
            for (var obelisk : List.of(first, second)) {
                power(obelisk, 8);
                filter(obelisk, EntityType.SLIME, false);
            }
            var slime = mob(helper, EntityType.SLIME, 7, 2, 5);
            var newerTarget = mob(helper, EntityType.PIG, 9, 2, 5);
            helper.getLevel().addFreshEntity(slime);
            helper.getLevel().addFreshEntity(newerTarget);
            try {
                first.serverTick();
                var firstDecoy = slime.getTarget();
                second.serverTick();
                helper.assertTrue(firstDecoy instanceof FakePlayer && slime.getTarget() == firstDecoy,
                    "overlapping machines keep one owner for the native target");

                first.setActionRange(new ActionRange(1, false));
                first.serverTick();
                second.serverTick();
                helper.assertTrue(slime.getTarget() instanceof FakePlayer && slime.getTarget() != firstDecoy,
                    "leaving the first range releases the mob to the second machine");

                slime.setTarget(newerTarget);
                helper.destroyBlock(new BlockPos(4, 2, 6));
                helper.assertTrue(slime.getTarget() == newerTarget,
                    "removing the second machine preserves a newer native target");
            } finally {
                slime.discard();
                newerTarget.discard();
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void attractorOnlyTemporarilyControlsOptInWitherGoals(GameplayTest test) {
        test.registerGameTestTemplate(() -> floor(13, 7, 11)
            .set(2, 1, 5, EIOBlocks.ATTRACTOR_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(2, 2, 5, AttractorObeliskBlockEntity.class);
            power(machine, 8);
            filter(machine, EntityType.WITHER, false);
            var wither = mob(helper, EntityType.WITHER, 8, 3, 5);
            var target = mob(helper, EntityType.PIG, 9, 2, 5);
            helper.getLevel().addFreshEntity(wither);
            helper.getLevel().addFreshEntity(target);
            wither.setTarget(target);
            var look = new Goal() {
                @Override
                public boolean canUse() { return true; }
            };
            look.setFlags(EnumSet.of(Goal.Flag.LOOK));
            wither.goalSelector.addGoal(-1, look);

            var option = MachinesConfig.COMMON.ATTRACTOR_PULL_BOSSES;
            boolean previous = option.get();
            try {
                option.set(false);
                machine.serverTick();
                helper.assertTrue(wither.getTarget() == target && wither.getDeltaMovement().equals(Vec3.ZERO),
                    "the default leaves the Wither's motion and target alone");
                option.set(true);
                machine.serverTick();
                wither.goalSelector.tick();
                var toward = Vec3.atCenterOf(machine.getBlockPos()).subtract(wither.position());
                helper.assertTrue(wither.getTarget() == null && wither.getDeltaMovement().dot(toward) > 0
                    && wither.goalSelector.getRunningGoals().noneMatch(goal -> goal.getGoal() == look),
                    "boss opt-in pulls the Wither and temporarily suppresses its look goal");

                option.set(false);
                machine.serverTick();
                wither.goalSelector.tick();
                helper.assertTrue(wither.getTarget() == target && wither.goalSelector.getRunningGoals().anyMatch(goal -> goal.getGoal() == look),
                    "disabling boss attraction restores the Wither's target and control flags");
            } finally {
                option.set(previous);
                wither.discard();
                target.discard();
            }
            helper.startSequence().thenSucceed();
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
        filter(machine, denyList, type);
    }

    private static void filter(ObeliskBlockEntity<?> machine, boolean denyList, EntityType<?>... types) {
        var stack = new ItemStack(EIOItems.BASIC_SOUL_FILTER.get());
        EIODataComponents.SOUL_FILTER.set(stack, new EnderSoulFilter(java.util.Arrays.stream(types).map(Soul::of).toList(), denyList, false));
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

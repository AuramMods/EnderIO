package com.enderio.core.gametest;

import com.enderio.enderio.foundation.util.BlockEntitySpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class SpawnerEventTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void initializationPostsOnceAndExposesNativeMachineOwner(GameTestHelper helper) {
        var owner = new ChestBlockEntity(helper.absolutePos(BlockPos.ZERO), Blocks.CHEST.defaultBlockState());
        var spawner = new BlockEntitySpawner(owner);
        var pig = new ObservedPig(helper);
        var events = new AtomicInteger();
        var data = new SpawnGroupData() {};
        var tag = new CompoundTag();
        tag.putString("event", "preserved");
        Consumer<MobSpawnEvent.FinalizeSpawn> listener = event -> {
            if (event.getEntity() != pig) return;
            events.incrementAndGet();
            helper.assertTrue(event.getSpawner().getSpawnerBlockEntity() == owner, "Machine owner missing from native spawner");
            helper.assertTrue(event.getSpawnType() == MobSpawnType.SPAWNER, "Wrong spawn type");
            event.setSpawnData(data);
            event.setSpawnTag(tag);
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(spawner.prepare(pig, helper.getLevel(), owner.getBlockPos(), true), "Spawn unexpectedly rejected");
            helper.assertTrue(events.get() == 1 && pig.initializations == 1, "Initialization/event ran more than once");
            helper.assertTrue(pig.receivedData == data && pig.receivedTag == tag, "Event changes did not reach native initialization");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cancellingInitializationStillAllowsInsertion(GameTestHelper helper) {
        var spawner = spawner(helper);
        var pig = new ObservedPig(helper);
        Consumer<MobSpawnEvent.FinalizeSpawn> listener = event -> {
            if (event.getEntity() == pig) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(spawner.prepare(pig, helper.getLevel(), pig.blockPosition(), true), "Initialization cancellation blocked insertion");
            helper.assertTrue(pig.initializations == 0, "Cancelled initialization still ran");
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Native insertion rejected initialized-cancelled mob");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); pig.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cancellingSpawnBlocksBothPreparationAndNativeInsertion(GameTestHelper helper) {
        var pig = new ObservedPig(helper);
        Consumer<MobSpawnEvent.FinalizeSpawn> listener = event -> {
            if (event.getEntity() == pig) event.setSpawnCancelled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(!spawner(helper).prepare(pig, helper.getLevel(), pig.blockPosition(), true), "Denied spawn reported success");
            helper.assertTrue(pig.initializations == 0, "Denied spawn initialized");
            helper.assertTrue(!helper.getLevel().addFreshEntity(pig), "Native Forge accepted spawn-cancelled entity");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); pig.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void copiedMobsPostTheEventWithoutReinitializingState(GameTestHelper helper) {
        var pig = new ObservedPig(helper);
        pig.setNoAi(true);
        pig.setHealth(3);
        var events = new AtomicInteger();
        Consumer<MobSpawnEvent.FinalizeSpawn> listener = event -> {
            if (event.getEntity() == pig) events.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(spawner(helper).prepare(pig, helper.getLevel(), pig.blockPosition(), false), "Copy was rejected");
            helper.assertTrue(events.get() == 1 && pig.initializations == 0, "Copied mob skipped the event or reinitialized");
            helper.assertTrue(pig.isNoAi() && pig.getHealth() == 3, "Copy state changed");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }

    private static BlockEntitySpawner spawner(GameTestHelper helper) {
        return new BlockEntitySpawner(new ChestBlockEntity(helper.absolutePos(BlockPos.ZERO), Blocks.CHEST.defaultBlockState()));
    }

    private static class ObservedPig extends Pig {
        private int initializations;
        private SpawnGroupData receivedData;
        private CompoundTag receivedTag;

        ObservedPig(GameTestHelper helper) {
            super(EntityType.PIG, helper.getLevel());
            moveTo(helper.absolutePos(new BlockPos(1, 1, 1)), 0, 0);
        }

        @Override
        public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                MobSpawnType type, SpawnGroupData data, CompoundTag tag) {
            initializations++;
            receivedData = data;
            receivedTag = tag;
            return data;
        }
    }
}

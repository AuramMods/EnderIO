package com.enderio.core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.enderio.core.gametest.RegistryBlockEntityTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RegistryBlockEntityTests {
    private static CompoundTag tag(String biome, int value) {
        var tag = new CompoundTag();
        tag.putString("id", TYPE.getId().toString());
        tag.putString("Biome", biome);
        tag.putInt("Value", value);
        tag.putInt("WorldOnly", 91);
        return tag;
    }
    private static Fixture load(BlockPos pos, CompoundTag tag) {
        return (Fixture) BlockEntity.loadStatic(pos, BLOCK.get().defaultBlockState(), tag);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeLoadStaticOwnsNbtAndWaitsForWorldRegistries(GameTestHelper helper) {
        var tag = tag("minecraft:plains", 7);
        var entity = load(helper.absolutePos(BlockPos.ZERO), tag);
        helper.assertTrue(entity != null && entity.loads == 0 && entity.biome == null,
            "Native loadStatic decoded before level attachment");
        tag.putString("Biome", "minecraft:desert");
        var saved = entity.saveWithoutMetadata();
        helper.assertTrue(saved.getString("Biome").equals("minecraft:plains") && saved.getInt("WorldOnly") == 91,
            "Pending NBT was borrowed or lost when saved without a level");
        saved.putString("Biome", "minecraft:desert");
        entity.setLevel(helper.getLevel());
        var expected = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        helper.assertTrue(entity.biome == expected && entity.loads == 1 && entity.value == 7,
            "Deferred data did not resolve against the attached world's registry");
        entity.setLevel(helper.getLevel());
        helper.assertTrue(entity.loads == 1, "Repeated native level assignment decoded the same payload again");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeChunkRegistrationRestoresBeforeOnLoadAndRoundTrips(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, BLOCK.get().defaultBlockState());
        var entity = load(pos, tag("minecraft:plains", 23));
        helper.getLevel().setBlockEntity(entity);
        helper.assertTrue(entity.loads == 1 && entity.biomeSeenOnLoad == entity.biome && entity.biome != null,
            "Chunk registration did not restore registry state before onLoad");
        var saved = entity.saveWithFullMetadata();
        var restored = load(pos, saved);
        helper.getLevel().setBlockEntity(restored);
        helper.assertTrue(restored.value == 23 && restored.worldOnly == 91 && restored.biome == entity.biome,
            "Native block-entity save/load lost state or registry identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void attachedUpdateTagsDecodeImmediatelyAndKeepWorldOnlyDataOutOfSync(GameTestHelper helper) {
        var entity = new Fixture(helper.absolutePos(BlockPos.ZERO), BLOCK.get().defaultBlockState());
        entity.setLevel(helper.getLevel());
        entity.handleUpdateTag(tag("minecraft:plains", 5));
        entity.handleUpdateTag(tag("minecraft:desert", 8));
        var desert = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        helper.assertTrue(entity.biome == desert && entity.value == 8 && entity.loads == 2,
            "Native update-tag dispatch skipped or deferred an attached update");
        entity.value = 13;
        var synced = entity.getUpdateTag();
        helper.assertTrue(synced.getInt("Value") == 13 && synced.getString("Biome").equals("minecraft:desert")
            && !synced.contains("WorldOnly"), "Synced save used old pending data or leaked world-only fields");
        helper.assertTrue(entity.saveWithoutMetadata().getInt("WorldOnly") == 91, "Persistent field disappeared");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedRegistryDecodeRetainsOriginalPayloadUntilSuccessfulReplacement(GameTestHelper helper) {
        var original = tag("endercore_tests:missing_biome", 19);
        var entity = load(helper.absolutePos(BlockPos.ZERO), original);
        boolean failed = false;
        try { entity.setLevel(helper.getLevel()); }
        catch (RuntimeException expected) { failed = true; }
        helper.assertTrue(failed && entity.loads == 0, "Unknown holder was silently accepted");
        var preserved = entity.saveWithoutMetadata();
        helper.assertTrue(preserved.getString("Biome").equals("endercore_tests:missing_biome")
            && preserved.getInt("Value") == 19 && preserved.getInt("WorldOnly") == 91,
            "Failed decode destroyed the original payload");
        entity.load(tag("minecraft:desert", 29));
        helper.assertTrue(entity.loads == 1 && entity.value == 29
            && entity.saveWithoutMetadata().getString("Biome").equals("minecraft:desert"),
            "Successful replacement did not clear the failed pending payload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newerDetachedLoadReplacesQueuedDataWithoutApplyingOldState(GameTestHelper helper) {
        var entity = load(helper.absolutePos(BlockPos.ZERO), tag("minecraft:plains", 2));
        entity.load(tag("minecraft:desert", 31));
        helper.assertTrue(entity.loads == 0 && entity.getUpdateTag().getInt("Value") == 31,
            "Detached loads were decoded or retained the obsolete payload");
        entity.setLevel(helper.getLevel());
        helper.assertTrue(entity.loads == 1 && entity.value == 31
            && entity.biome.is(Biomes.DESERT), "Attachment applied stale queued state");
        helper.succeed();
    }
}

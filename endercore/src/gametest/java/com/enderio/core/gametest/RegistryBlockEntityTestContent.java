package com.enderio.core.gametest;

import com.enderio.core.common.blockentity.RegistryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RegistryBlockEntityTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "endercore_tests");
    static final RegistryObject<Block> BLOCK = BLOCKS.register("registry_block", FixtureBlock::new);
    static final RegistryObject<BlockEntityType<Fixture>> TYPE = TYPES.register("registry_block",
        () -> BlockEntityType.Builder.of(Fixture::new, BLOCK.get()).build(null));
    static void register(IEventBus bus) { BLOCKS.register(bus); TYPES.register(bus); }

    private static final class FixtureBlock extends BaseEntityBlock {
        FixtureBlock() { super(BlockBehaviour.Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Fixture(pos, state); }
    }

    public static final class Fixture extends RegistryBlockEntity {
        static final com.mojang.serialization.Codec<Holder<Biome>> BIOME = RegistryFixedCodec.create(Registries.BIOME);
        Holder<Biome> biome, biomeSeenOnLoad;
        int value, worldOnly, loads;
        Fixture(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }

        @Override protected void loadWithRegistries(CompoundTag tag, RegistryAccess registries) {
            var decoded = BIOME.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag.get("Biome"))
                .getOrThrow(false, message -> {});
            biome = decoded;
            value = tag.getInt("Value");
            worldOnly = tag.getInt("WorldOnly");
            loads++;
        }
        @Override protected void saveWithRegistries(CompoundTag tag, RegistryAccess registries) {
            tag.putInt("WorldOnly", worldOnly);
        }
        @Override protected void saveSyncedWithRegistries(CompoundTag tag, RegistryAccess registries) {
            if (biome != null) tag.put("Biome", BIOME.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), biome)
                .getOrThrow(false, message -> {}));
            tag.putInt("Value", value);
        }
        @Override public void onLoad() { super.onLoad(); biomeSeenOnLoad = biome; }
    }
}

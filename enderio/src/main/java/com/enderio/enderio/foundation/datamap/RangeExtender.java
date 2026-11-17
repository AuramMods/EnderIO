package com.enderio.enderio.foundation.datamap;

import com.enderio.core.common.datamap.RegistryDataMap;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class RangeExtender {
    public static final Codec<Map<TagKey<Block>, Integer>> CODEC = Codec.unboundedMap(
        TagKey.hashedCodec(Registries.BLOCK), Codec.INT);

    public static final RegistryDataMap<Block, Map<TagKey<Block>, Integer>> DATA_MAP = new RegistryDataMap<>(
        ResourceLocation.fromNamespaceAndPath("enderio", "range_extender"), Registries.BLOCK, CODEC, true);
}

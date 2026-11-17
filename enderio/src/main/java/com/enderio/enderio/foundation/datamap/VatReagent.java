package com.enderio.enderio.foundation.datamap;

import com.enderio.core.common.datamap.RegistryDataMap;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Map;

public class VatReagent {
    public static final Codec<Map<TagKey<Item>, Double>> CODEC = Codec.unboundedMap(
        TagKey.hashedCodec(Registries.ITEM), Codec.DOUBLE);

    public static final RegistryDataMap<Item, Map<TagKey<Item>, Double>> DATA_MAP = new RegistryDataMap<>(
        ResourceLocation.fromNamespaceAndPath("enderio", "vat_reagent"), Registries.ITEM, CODEC, true);
}

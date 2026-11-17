package com.enderio.enderio.content.paint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.level.block.Block;

public record BlockPaintData(Block paint) {
    public static final Codec<BlockPaintData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("paint").forGetter(BlockPaintData::paint)
        ).apply(instance, BlockPaintData::new));

    public static final PacketCodec<BlockPaintData> STREAM_CODEC = PacketCodecs.registry(net.minecraft.core.registries.BuiltInRegistries.BLOCK)
        .map(BlockPaintData::new, BlockPaintData::paint);

    public static BlockPaintData of(Block paint) {
        return new BlockPaintData(paint);
    }
}

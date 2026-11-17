package com.enderio.enderio.foundation.block.entity.legacy.sync;

import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.foundation.io.energy.ILargeMachineEnergyStorage;
import com.enderio.enderio.foundation.io.energy.LargeImmutableMachineEnergyStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

@Deprecated(forRemoval = true, since = "7.1")
public record LargeEnergyData(long energyStored, long maxEnergyStored) {
    public static final Codec<LargeEnergyData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.LONG.fieldOf("EnergyStored").forGetter(LargeEnergyData::energyStored),
            Codec.LONG.fieldOf("MaxEnergyStored").forGetter(LargeEnergyData::maxEnergyStored)
        ).apply(instance, LargeEnergyData::new)
    );

    public static final PacketCodec<LargeEnergyData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.VAR_LONG,
        LargeEnergyData::energyStored,
        PacketCodecs.VAR_LONG,
        LargeEnergyData::maxEnergyStored,
        LargeEnergyData::new
    );

    public static LargeEnergyData from(ILargeMachineEnergyStorage storage) {
        return new LargeEnergyData(
            storage.getLargeEnergyStored(),
            storage.getLargeMaxEnergyStored()
        );
    }

    public static final NetworkDataSlot.CodecType<LargeEnergyData> DATA_SLOT_TYPE
        = new NetworkDataSlot.CodecType<>(CODEC, STREAM_CODEC);

    public LargeImmutableMachineEnergyStorage toImmutableStorage() {
        return new LargeImmutableMachineEnergyStorage(energyStored, maxEnergyStored);
    }
}

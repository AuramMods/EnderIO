package com.enderio.enderio.foundation.block.entity.legacy.sync;

import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.foundation.io.energy.IMachineEnergyStorage;
import com.enderio.enderio.foundation.io.energy.ImmutableMachineEnergyStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

@Deprecated(forRemoval = true, since = "7.1")
public record EnergySyncData(int energyStored, int maxEnergyStored, int maxEnergyUse) {

    public static final Codec<EnergySyncData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Codec.INT.fieldOf("EnergyStored").forGetter(EnergySyncData::energyStored),
                    Codec.INT.fieldOf("MaxEnergyStored").forGetter(EnergySyncData::maxEnergyStored),
                    Codec.INT.fieldOf("MaxEnergyUse").forGetter(EnergySyncData::maxEnergyUse))
            .apply(instance, EnergySyncData::new));

    public static final PacketCodec<EnergySyncData> STREAM_CODEC = PacketCodec.composite(PacketCodecs.INT,
            EnergySyncData::energyStored, PacketCodecs.INT, EnergySyncData::maxEnergyStored, PacketCodecs.INT,
            EnergySyncData::maxEnergyUse, EnergySyncData::new);

    public static final NetworkDataSlot.CodecType<EnergySyncData> DATA_SLOT_TYPE = new NetworkDataSlot.CodecType<>(CODEC,
            STREAM_CODEC);

    public static EnergySyncData from(IMachineEnergyStorage storage) {
        return new EnergySyncData(storage.getEnergyStored(), storage.getMaxEnergyStored(), storage.getMaxEnergyUse());
    }

    public ImmutableMachineEnergyStorage toImmutableStorage() {
        return new ImmutableMachineEnergyStorage(energyStored, maxEnergyStored, maxEnergyUse);
    }
}

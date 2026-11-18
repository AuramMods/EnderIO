package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitData;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataType;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChemicalConduitData implements ConduitData<ChemicalConduitData> {

    public static final MapCodec<ChemicalConduitData> CODEC = RecordCodecBuilder
            .mapCodec(
                    instance -> instance
                            .group(Codec.BOOL.fieldOf("should_reset").forGetter(i -> i.shouldReset),
                                    new com.enderio.core.common.serialization.StrictOptionalFieldCodec<>(ChemicalResource.OPTIONAL_CODEC, "locked_fluid")
                                            .xmap(value -> value.orElse(ChemicalResource.EMPTY), java.util.Optional::of)
                                            .forGetter(i -> i.lockedChemical))
                            .apply(instance, ChemicalConduitData::new));

    public static final PacketCodec<ChemicalConduitData> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.BOOL, i -> i.shouldReset, ChemicalResource.OPTIONAL_STREAM_CODEC, i -> i.lockedChemical,
            ChemicalConduitData::new);

    private ChemicalResource lockedChemical = ChemicalResource.EMPTY;
    private boolean shouldReset = false;

    public ChemicalConduitData() {
    }

    public ChemicalConduitData(boolean shouldReset, ChemicalResource lockedChemical) {
        this.shouldReset = shouldReset;
        this.lockedChemical = lockedChemical.copy();
    }

    public boolean shouldReset() {
        return shouldReset;
    }

    public void setShouldReset(boolean shouldReset) {
        this.shouldReset = shouldReset;
    }

    public ChemicalResource lockedChemical() {
        return lockedChemical;
    }

    public void setLockedChemical(ChemicalResource lockedChemical) {
        this.lockedChemical = lockedChemical.copy();
    }

    @Override
    public ConduitDataType<ChemicalConduitData> type() {
        return MekanismModule.CHEMICAL_DATA_TYPE.get();
    }

    @Override
    public ChemicalConduitData withClientChanges(ChemicalConduitData guiData) {
        this.shouldReset = guiData.shouldReset;

        // TODO: Soon we will swap to records which will mean this will be a new
        // instance.
        // This API has been designed with this pending change in mind.
        return this;
    }

    @Override
    public @Nullable NodeData toNodeData() {
        return null;
    }

    @Override
    public ChemicalConduitData deepCopy() {
        return new ChemicalConduitData(shouldReset, lockedChemical);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shouldReset, lockedChemical);
    }
}

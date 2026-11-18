package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import com.enderio.core.common.serialization.StrictOptionalFieldCodec;
import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mekanism.api.chemical.merged.BoxedChemical;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Owns chemicals until a destination accepts them, and carries over any historical extraction lock. */
public final class ChemicalConduitNodeData implements NodeData {
    private record Pending(Direction side, ChemicalResource resource) {
        private static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Direction.CODEC.fieldOf("side").forGetter(Pending::side),
            ChemicalResource.OPTIONAL_CODEC.fieldOf("chemical").forGetter(Pending::resource))
            .apply(instance, Pending::new));

        private Pending {
            resource = resource.copy();
        }
    }

    public static final MapCodec<ChemicalConduitNodeData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Pending.CODEC.listOf().fieldOf("pending").forGetter(data -> List.copyOf(data.pending)),
        new StrictOptionalFieldCodec<>(ChemicalResource.OPTIONAL_CODEC, "legacy_extraction_lock")
            .forGetter(data -> data.legacyExtractionLock.isEmpty() ? Optional.empty() : Optional.of(data.legacyExtractionLock)))
        .apply(instance, ChemicalConduitNodeData::new));
    public static final NodeDataType<ChemicalConduitNodeData> TYPE =
        new NodeDataType<>(CODEC, ChemicalConduitNodeData::new);

    private final List<Pending> pending = new ArrayList<>();
    private final ChemicalResource legacyExtractionLock;

    private ChemicalConduitNodeData() {
        this(List.of(), Optional.empty());
    }

    private ChemicalConduitNodeData(List<Pending> saved, Optional<ChemicalResource> legacyExtractionLock) {
        saved.forEach(entry -> retain(entry.side(), entry.resource()));
        this.legacyExtractionLock = legacyExtractionLock.map(ChemicalResource::copy).orElse(ChemicalResource.EMPTY);
    }

    static @Nullable ChemicalConduitNodeData readForge6(CompoundTag data) {
        if (!data.contains("LockedFluid", Tag.TAG_COMPOUND)) return null;
        var boxed = BoxedChemical.read(data.getCompound("LockedFluid"));
        // The old lock names a chemical and does not own any quantity.
        return boxed.isEmpty() ? null : new ChemicalConduitNodeData(List.of(),
            Optional.of(new ChemicalResource(boxed.getChemical(), 1)));
    }

    boolean acceptsExtraction(ChemicalResource stack) {
        return legacyExtractionLock.isEmpty() || ChemicalResource.isSameChemical(legacyExtractionLock, stack);
    }

    @Override
    public NodeDataType<?> type() {
        return MekanismModule.CHEMICAL_NODE_DATA.get();
    }

    public ChemicalResource first(Direction side) {
        for (var entry : pending) {
            if (entry.side() == side) return entry.resource().copy();
        }
        return ChemicalResource.EMPTY;
    }

    void retain(Direction side, ChemicalResource resource) {
        if (!resource.isEmpty()) pending.add(new Pending(side, resource));
    }

    void consume(Direction side, long amount) {
        if (amount == 0) return;
        for (int i = 0; i < pending.size(); i++) {
            var entry = pending.get(i);
            if (entry.side() != side) continue;
            long remaining = entry.resource().getAmount() - amount;
            if (amount < 0 || remaining < 0) throw new IllegalArgumentException("Invalid retained chemical amount");
            if (remaining == 0) pending.remove(i);
            else entry.resource().setAmount(remaining);
            return;
        }
        throw new IllegalStateException("No retained chemical on side " + side);
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    void addAll(ChemicalConduitNodeData other) {
        other.pending.forEach(entry -> retain(entry.side(), entry.resource()));
    }
}

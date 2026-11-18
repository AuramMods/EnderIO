package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Owns chemicals already extracted from a source until a destination accepts them. */
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
        Pending.CODEC.listOf().fieldOf("pending").forGetter(data -> List.copyOf(data.pending)))
        .apply(instance, ChemicalConduitNodeData::new));
    public static final NodeDataType<ChemicalConduitNodeData> TYPE =
        new NodeDataType<>(CODEC, ChemicalConduitNodeData::new);

    private final List<Pending> pending = new ArrayList<>();

    private ChemicalConduitNodeData() {}

    private ChemicalConduitNodeData(List<Pending> saved) {
        saved.forEach(entry -> retain(entry.side(), entry.resource()));
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

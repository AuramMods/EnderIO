package com.enderio.enderio.content.conduits.type.fluid;

import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.enderio.enderio.init.EIOConduitTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Fluid already extracted but rejected by an execution-time destination change. */
public final class FluidConduitNodeData implements NodeData {
    public record Pending(Direction side, FluidStack fluid) {
        private static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Direction.CODEC.fieldOf("side").forGetter(Pending::side),
            FluidStack.CODEC.fieldOf("fluid").forGetter(Pending::fluid)).apply(instance, Pending::new));
        public Pending { fluid = fluid.copy(); }
    }

    public static final MapCodec<FluidConduitNodeData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Pending.CODEC.listOf().fieldOf("pending").forGetter(data -> List.copyOf(data.pending)))
        .apply(instance, FluidConduitNodeData::new));
    public static final NodeDataType<FluidConduitNodeData> TYPE = new NodeDataType<>(CODEC, FluidConduitNodeData::new);
    private final List<Pending> pending = new ArrayList<>();

    public FluidConduitNodeData() { }
    private FluidConduitNodeData(List<Pending> saved) { saved.forEach(entry -> retain(entry.side(), entry.fluid())); }
    @Override public NodeDataType<?> type() { return EIOConduitTypes.NodeData.FLUID.get(); }

    /** Defensive copy; callers must explicitly consume only the amount actually inserted. */
    public FluidStack first(Direction side) {
        return pending.stream().filter(entry -> entry.side() == side).findFirst()
            .map(entry -> entry.fluid().copy()).orElse(FluidStack.EMPTY);
    }
    public void retain(Direction side, FluidStack fluid) {
        if (!fluid.isEmpty()) pending.add(new Pending(side, fluid));
    }
    public void consume(Direction side, int amount) {
        for (int i = 0; i < pending.size(); i++) {
            var entry = pending.get(i);
            if (entry.side() != side) continue;
            if (amount < 0 || amount > entry.fluid().getAmount()) throw new IllegalArgumentException("Invalid retained-fluid consumption");
            entry.fluid().shrink(amount);
            if (entry.fluid().isEmpty()) pending.remove(i);
            return;
        }
        if (amount != 0) throw new IllegalStateException("No retained fluid on side " + side);
    }
    public boolean isEmpty() { return pending.isEmpty(); }
    public long amount() { return pending.stream().mapToLong(entry -> entry.fluid().getAmount()).sum(); }
    public void addAll(FluidConduitNodeData other) { other.pending.forEach(entry -> retain(entry.side(), entry.fluid())); }
}

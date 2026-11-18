package com.enderio.modded_conduits.common.modules.mekanism.chemical_filter;

import com.enderio.core.common.serialization.OrderedListCodec;
import com.enderio.core.common.util.EqualityUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import net.minecraft.core.NonNullList;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record EnderChemicalFilter(NonNullList<ChemicalResource> matches, boolean isDenyList)
    implements ChemicalFilter, TooltipComponent {

    public static final EnderChemicalFilter EMPTY = new EnderChemicalFilter(0);

    public static final Codec<EnderChemicalFilter> CODEC = RecordCodecBuilder
        .create(componentInstance -> componentInstance
            .group(
                OrderedListCodec.create(256, ChemicalResource.OPTIONAL_CODEC, ChemicalResource.EMPTY)
                    .fieldOf("chemicals")
                    .forGetter(EnderChemicalFilter::matches),
                Codec.BOOL.fieldOf("isInvert").forGetter(EnderChemicalFilter::isDenyList))
            .apply(componentInstance, EnderChemicalFilter::new));

    // @formatter:off
    public static final PacketCodec<EnderChemicalFilter> STREAM_CODEC = PacketCodec.composite(
        ChemicalResource.OPTIONAL_STREAM_CODEC.list(256),
        EnderChemicalFilter::matches,
        PacketCodecs.BOOL,
        EnderChemicalFilter::isDenyList,
        EnderChemicalFilter::new);
    // @formatter:on

    public EnderChemicalFilter {
        if (matches.size() > 256) throw new IllegalArgumentException("Too many chemical filter entries");
        var copy = NonNullList.withSize(matches.size(), ChemicalResource.EMPTY);
        for (int i = 0; i < matches.size(); i++) copy.set(i, matches.get(i).copy());
        matches = copy;
    }

    public EnderChemicalFilter(int size) {
        this(NonNullList.withSize(size, ChemicalResource.EMPTY), false);
    }

    public EnderChemicalFilter(List<ChemicalResource> matches, boolean isDenyList) {
        this(NonNullList.withSize(matches.size(), ChemicalResource.EMPTY), isDenyList);

        for (int i = 0; i < matches.size(); i++) {
            this.matches.set(i, matches.get(i).copy());
        }
    }
    
    @Override
    public ChemicalResource test(@Nullable ChemicalHandlerAdapter target, ChemicalResource stack) {
        for (var match : matches) {
            if (match.isEmpty()) {
                continue;
            }

            if (ChemicalResource.isSameChemical(match, stack)) {
                return isDenyList ? ChemicalResource.EMPTY : stack;
            }
        }

        return isDenyList ? stack : ChemicalResource.EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EnderChemicalFilter other = (EnderChemicalFilter) o;
        return isDenyList == other.isDenyList &&
            Objects.equals(matches, other.matches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matches, isDenyList);
    }
}

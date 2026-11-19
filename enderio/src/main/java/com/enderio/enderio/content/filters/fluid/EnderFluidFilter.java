package com.enderio.enderio.content.filters.fluid;

import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.core.common.serialization.OrderedListCodec;
import com.enderio.core.common.util.EqualityUtil;
import com.enderio.enderio.api.filter.FluidFilter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record EnderFluidFilter(NonNullList<FluidStack> matches, boolean isDenyList, boolean shouldCompareComponents,
        boolean legacyMatching)
        implements FluidFilter, TooltipComponent {

    public static final EnderFluidFilter EMPTY = new EnderFluidFilter(0);

    // TODO: 1.22: Rename fields.
    public static final Codec<EnderFluidFilter> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(OrderedListCodec.create(256, StackDataCodecs.OPTIONAL_FLUID, FluidStack.EMPTY)
                    .fieldOf("fluids")
                    .forGetter(EnderFluidFilter::matches),
                    Codec.BOOL.fieldOf("isInvert").forGetter(EnderFluidFilter::isDenyList),
                    Codec.BOOL.fieldOf("isNbt").forGetter(EnderFluidFilter::shouldCompareComponents),
                    Codec.BOOL.optionalFieldOf("legacyMatching", false).forGetter(EnderFluidFilter::legacyMatching))
            .apply(inst, EnderFluidFilter::new));

    // @formatter:off
    public static final PacketCodec<EnderFluidFilter> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.FLUID_STACK.list(256),
        EnderFluidFilter::matches,
        PacketCodecs.BOOL,
        EnderFluidFilter::isDenyList,
        PacketCodecs.BOOL,
        EnderFluidFilter::shouldCompareComponents,
        PacketCodecs.BOOL,
        EnderFluidFilter::legacyMatching,
        EnderFluidFilter::new);
    // @formatter:on

    public EnderFluidFilter(int size) {
        this(NonNullList.withSize(size, FluidStack.EMPTY), false, false);
    }

    public EnderFluidFilter(NonNullList<FluidStack> matches, boolean isDenyList, boolean shouldCompareComponents) {
        this(matches, isDenyList, shouldCompareComponents, false);
    }

    public EnderFluidFilter(List<FluidStack> matches, boolean isDenyList, boolean shouldCompareComponents) {
        this(matches, isDenyList, shouldCompareComponents, false);
    }

    public EnderFluidFilter(List<FluidStack> matches, boolean isDenyList, boolean shouldCompareComponents,
            boolean legacyMatching) {
        this(NonNullList.withSize(matches.size(), FluidStack.EMPTY), isDenyList, shouldCompareComponents, legacyMatching);

        for (int i = 0; i < matches.size(); i++) {
            this.matches.set(i, matches.get(i));
        }
    }

    @Override
    public FluidStack test(@Nullable IFluidHandler target, FluidStack stack) {
        for (var match : matches) {
            if (matches(match, stack)) {
                return isDenyList ? FluidStack.EMPTY : stack;
            }
        }

        return isDenyList ? stack : FluidStack.EMPTY;
    }

    private boolean matches(FluidStack sample, FluidStack stack) {
        if (legacyMatching) {
            // Forge 6's tag mode compared tags alone. Its other mode compared both ID and tags.
            return shouldCompareComponents ? FluidStack.areFluidStackTagsEqual(sample, stack) : sample.isFluidEqual(stack);
        }
        return !sample.isEmpty()
            && (shouldCompareComponents ? sample.isFluidEqual(stack) : sample.getFluid() == stack.getFluid());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EnderFluidFilter other = (EnderFluidFilter) o;
        return isDenyList == other.isDenyList &&
                shouldCompareComponents == other.shouldCompareComponents &&
                legacyMatching == other.legacyMatching &&
                EqualityUtil.areListsEqual(matches, other.matches, StackDataCodecs::fluidsEqual);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EqualityUtil.hashListContents(matches, StackDataCodecs::hashFluid), isDenyList,
            shouldCompareComponents, legacyMatching);
    }
}

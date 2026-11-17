package com.enderio.core.common.util;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record NamedFluidContents(ImmutableMap<String, FluidStack> fluidMap) {
    public static final Codec<NamedFluidContents> CODEC = Codec.unboundedMap(Codec.STRING, FluidStack.CODEC)
            .xmap(contents -> new NamedFluidContents(ImmutableMap.copyOf(contents)), NamedFluidContents::fluidMap);

    // @formatter:off
    public static final PacketCodec<NamedFluidContents> STREAM_CODEC = PacketCodec.map( PacketCodecs.STRING_UTF8, PacketCodecs.FLUID_STACK)
        .map(contents -> new NamedFluidContents(ImmutableMap.copyOf(contents)), i -> new HashMap<>(i.fluidMap()));
    // @formatter:on

    public static NamedFluidContents copyOf(Map<String, FluidStack> fluidMap) {
        var copies = fluidMap.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, i -> i.getValue().copy()));

        return new NamedFluidContents(ImmutableMap.copyOf(copies));
    }

    public FluidStack copy(String key) {
        return fluidMap.getOrDefault(key, FluidStack.EMPTY).copy();
    }
}

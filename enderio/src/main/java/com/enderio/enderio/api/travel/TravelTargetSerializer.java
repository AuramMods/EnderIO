package com.enderio.enderio.api.travel;

import com.mojang.serialization.MapCodec;
import com.enderio.core.common.network.codec.PacketCodec;

public interface TravelTargetSerializer<T extends TravelTarget> {
    MapCodec<T> codec();
    PacketCodec<T> streamCodec();
}

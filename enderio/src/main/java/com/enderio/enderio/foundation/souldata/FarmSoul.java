package com.enderio.enderio.foundation.souldata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class FarmSoul {

    public record SoulData(ResourceLocation entitytype, float bonemeal, int seeds, float power)
            implements com.enderio.enderio.foundation.souldata.SoulData {
        @Override
        public ResourceLocation getKey() {
            return entitytype();
        }
    }

    public static final Codec<FarmSoul.SoulData> CODEC = RecordCodecBuilder.create(soulDataInstance -> soulDataInstance
            .group(ResourceLocation.CODEC.fieldOf("entity").forGetter(FarmSoul.SoulData::entitytype),
                    Codec.FLOAT.optionalFieldOf("bonemeal", 1f).forGetter(FarmSoul.SoulData::bonemeal),
                    Codec.INT.optionalFieldOf("seeds", 0).forGetter(FarmSoul.SoulData::seeds),
                    Codec.FLOAT.optionalFieldOf("power", 1f).forGetter(FarmSoul.SoulData::power))
            .apply(soulDataInstance, FarmSoul.SoulData::new));

    public static final PacketCodec<FarmSoul.SoulData> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.RESOURCE_LOCATION, FarmSoul.SoulData::entitytype, PacketCodecs.FLOAT,
            FarmSoul.SoulData::bonemeal, PacketCodecs.INT, FarmSoul.SoulData::seeds, PacketCodecs.FLOAT,
            FarmSoul.SoulData::power, FarmSoul.SoulData::new);

    public static final String NAME = "farm";
    public static final SoulDataReloadListener<FarmSoul.SoulData> FARM = new SoulDataReloadListener<>(NAME, CODEC);

    @SubscribeEvent
    public static void addResource(AddReloadListenerEvent event) {
        FARM.addReloadListener(event);
    }
}

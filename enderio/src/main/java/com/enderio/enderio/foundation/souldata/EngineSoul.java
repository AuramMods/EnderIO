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
public class EngineSoul {

    // TODO: 20.6: May be able to use FluidIngredient
    public record SoulData(ResourceLocation entitytype, String fluid, int powerpermb, int tickpermb) implements com.enderio.enderio.foundation.souldata.SoulData {
        @Override
        public ResourceLocation getKey() {
            return entitytype();
        }
    }

    public static final Codec<SoulData> CODEC = RecordCodecBuilder.create(soulDataInstance ->
       soulDataInstance.group(
           ResourceLocation.CODEC.fieldOf("entity").forGetter(SoulData::entitytype),
           Codec.STRING.fieldOf("fluid").forGetter(SoulData::fluid),
           Codec.INT.fieldOf("power/mb").forGetter(SoulData::powerpermb),
           Codec.INT.fieldOf("tick/mb").forGetter(SoulData::tickpermb))
           .apply(soulDataInstance, SoulData::new));

    public static final PacketCodec<SoulData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.RESOURCE_LOCATION,
        SoulData::entitytype,
        PacketCodecs.STRING_UTF8,
        SoulData::fluid,
        PacketCodecs.INT,
        SoulData::powerpermb,
        PacketCodecs.INT,
        SoulData::tickpermb,
        SoulData::new
    );

    public static final String NAME = "engine";
    public static final SoulDataReloadListener<SoulData> ENGINE = new SoulDataReloadListener<>(NAME, CODEC);

    @SubscribeEvent
    public static void addResource(AddReloadListenerEvent event) {
        event.addListener(ENGINE);
    }
}

package com.enderio.enderio.foundation.souldata;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.util.Optional;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class SolarSoul {

    public record SoulData(ResourceLocation entitytype, boolean daytime, boolean nighttime, Optional<ResourceKey<Level>> level) implements com.enderio.enderio.foundation.souldata.SoulData {

        @Override
        public ResourceLocation getKey() {
            return entitytype;
        }
    }

    public static final Codec<SoulData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(SoulData::entitytype),
            Codec.BOOL.fieldOf("daytime").forGetter(SoulData::daytime),
            Codec.BOOL.fieldOf("nighttime").forGetter(SoulData::nighttime),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("level").forGetter(SoulData::level)
            ).apply(instance, SoulData::new));

    public static final PacketCodec<SoulData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.RESOURCE_LOCATION,
        SoulData::entitytype,
        PacketCodecs.BOOL,
        SoulData::daytime,
        PacketCodecs.BOOL,
        SoulData::nighttime,
        PacketCodecs.resourceKey(Registries.DIMENSION).optional(),
        SoulData::level,
        SoulData::new
    );

    public static final String NAME = "solar";
    public static final SoulDataReloadListener<SoulData> SOLAR = new SoulDataReloadListener<>(NAME, CODEC);

    @SubscribeEvent
    public static void addResource(AddReloadListenerEvent event) {
        event.addListener(SOLAR);
    }
}

package com.enderio.enderio.content.filters.redstone;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.filter.RedstoneFilter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public class DoubleRedstoneChannel implements RedstoneFilter {

    public static final Component INSTANCE = new Component(DyeColor.GREEN, DyeColor.BROWN);

    private final ItemDataKey<Component> componentType;
    private final ItemStack stack;

    public DoubleRedstoneChannel(ItemStack stack, ItemDataKey<Component> componentType) {
        this.stack = stack;
        this.componentType = componentType;
    }

    public DyeColor getFirstChannel() {
        return componentType.get(stack).channel1();
    }

    public DyeColor getSecondChannel() {
        return componentType.get(stack).channel2();
    }

    public void setFirstChannel(DyeColor channel1) {
        componentType.set(stack, new Component(channel1, getSecondChannel()));
    }

    public void setSecondChannel(DyeColor channel2) {
        componentType.set(stack, new Component(getFirstChannel(), channel2));
    }

    public void setChannels(DyeColor channel1, DyeColor channel2) {
        componentType.set(stack, new Component(channel1, channel2));
    }

    @Override
    public boolean isConfigured() {
        Component current = componentType.get(stack);
        return current != null && !current.equals(INSTANCE);
    }

    public record Component(DyeColor channel1, DyeColor channel2) {

        public static final Codec<Component> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(DyeColor.CODEC.fieldOf("channel1").forGetter(Component::channel1),
                    DyeColor.CODEC.fieldOf("channel2").forGetter(Component::channel2))
                .apply(instance, Component::new)
        );

        public static final PacketCodec<Component> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.DYE_COLOR,
            Component::channel1,
            PacketCodecs.DYE_COLOR,
            Component::channel2,
            Component::new
        );

    }
}

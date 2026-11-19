package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.api.filter.RedstoneOutputFilterContext;
import com.enderio.enderio.foundation.network.packets.ServerboundCountFilterPacket;
import com.enderio.enderio.init.EIODataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public class RedstoneCountFilter implements RedstoneOutputFilter {

    public static final Component INSTANCE = new Component(DyeColor.GREEN, 8, 0, false);
    private final ItemStack stack;

    public RedstoneCountFilter(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int getOutputSignal(RedstoneOutputFilterContext context, DyeColor control) {
        var state = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        return state.count() == state.maxCount() ? 15 : 0;
    }

    @Override
    public void updateOutput(RedstoneOutputFilterContext context, DyeColor control) {
        var state = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        boolean deactivated = !context.isActive(state.channel1());
        int count = state.count();
        if (!deactivated && state.deactivated()) {
            count = count >= state.maxCount() ? 1 : count + 1;
        }
        if (count > state.maxCount()) {
            count = 1;
        }
        if (count != state.count() || deactivated != state.deactivated()) {
            EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(state.channel1(), state.maxCount(), count, deactivated));
        }
    }

    public DyeColor getChannel() {
        return EIODataComponents.REDSTONE_COUNT_FILTER.get(stack).channel1();
    }

    public int getMaxCount() {
        return EIODataComponents.REDSTONE_COUNT_FILTER.get(stack).maxCount();
    }

    public void setMaxCount(int maxCount) {
        var component = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(component.channel1, maxCount, component.count, component.deactivated));
    }

    public int getCount() {
        return EIODataComponents.REDSTONE_COUNT_FILTER.get(stack).count();
    }

    public void setCount(int count) {
        var component = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(component.channel1, component.maxCount, count, component.deactivated));
    }

    public boolean isDeactivated() {
        return EIODataComponents.REDSTONE_COUNT_FILTER.get(stack).deactivated();
    }

    public void setDeactivated(boolean lastActive) {
        var component = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(component.channel1, component.maxCount, component.count, lastActive));
    }

    public void setState(ServerboundCountFilterPacket packet) {
        EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(packet.channel1(), packet.maxCount(), packet.count(), packet.active()));
    }

    public void setChannel(DyeColor channel) {
        var component = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        EIODataComponents.REDSTONE_COUNT_FILTER.set(stack,
                new Component(channel, component.maxCount, component.count, component.deactivated));
    }

    @Override
    public boolean isConfigured() {
        Component current = EIODataComponents.REDSTONE_COUNT_FILTER.get(stack);
        return current != null && !current.equals(INSTANCE);
    }

    public record Component(DyeColor channel1, int maxCount, int count, boolean deactivated) {
        public static final Codec<Component> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(DyeColor.CODEC.fieldOf("channel1").forGetter(Component::channel1),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("maxCount").forGetter(Component::maxCount),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks").forGetter(Component::count),
                        Codec.BOOL.fieldOf("deactivated").forGetter(Component::deactivated))
                .apply(instance, Component::new));

        public static final PacketCodec<Component> STREAM_CODEC = PacketCodec.composite(PacketCodecs.DYE_COLOR,
                Component::channel1, PacketCodecs.VAR_INT, Component::maxCount, PacketCodecs.VAR_INT,
                Component::count, PacketCodecs.BOOL, Component::deactivated, Component::new);
    }
}

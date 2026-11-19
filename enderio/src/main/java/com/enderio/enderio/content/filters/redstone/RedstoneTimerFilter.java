package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.filter.RedstoneInputFilter;
import com.enderio.enderio.init.EIODataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RedstoneTimerFilter implements RedstoneInputFilter {

    public static final Component INSTANCE = new Component(0, 20);
    private final ItemStack stack;

    public RedstoneTimerFilter(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int getInputSignal(Level level, BlockPos pos, Direction direction) {
        int ticks = getTicks();
        int maxTicks = getMaxTicks();
        // The redstone network runs every two game ticks.
        if (ticks >= maxTicks - 2) {
            setTimer(0, maxTicks);
            return 15;
        }
        setTimer(ticks + 2, maxTicks);
        return 0;
    }

    public int getMaxTicks() {
        return EIODataComponents.REDSTONE_TIMER_FILTER.get(stack).maxTicks();
    }

    public int getTicks() {
        return EIODataComponents.REDSTONE_TIMER_FILTER.get(stack).ticks();
    }

    public void setTimer(int ticks, int maxTicks) {
        EIODataComponents.REDSTONE_TIMER_FILTER.set(stack, new Component(ticks, maxTicks));
    }

    public void setMaxTicks(int maxTicks) {
        EIODataComponents.REDSTONE_TIMER_FILTER.set(stack, new Component(0, maxTicks));
    }

    @Override
    public boolean isConfigured() {
        Component current = EIODataComponents.REDSTONE_TIMER_FILTER.get(stack);
        return current != null && !current.equals(INSTANCE);
    }

    public record Component(int ticks, int maxTicks) {
        public static final Codec<Component> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks").forGetter(Component::ticks),
                    ExtraCodecs.POSITIVE_INT.fieldOf("maxTicks").forGetter(Component::maxTicks))
                // Older port builds encoded maxTicks as the phase. That elapsed time is unrecoverable.
                .apply(instance, (ticks, maxTicks) -> new Component(ticks.equals(maxTicks) ? 0 : ticks, maxTicks))
        );

        public static final PacketCodec<Component> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.VAR_INT,
            Component::ticks,
            PacketCodecs.VAR_INT,
            Component::maxTicks,
            Component::new
        );
    }
}

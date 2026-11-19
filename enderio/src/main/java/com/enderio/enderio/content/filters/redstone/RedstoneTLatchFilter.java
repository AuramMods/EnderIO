package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.api.filter.RedstoneOutputFilterContext;
import com.enderio.enderio.init.EIODataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public class RedstoneTLatchFilter implements RedstoneOutputFilter {

    public static final Component INSTANCE = new Component(false, true);
    private final ItemStack stack;

    public RedstoneTLatchFilter(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int getOutputSignal(RedstoneOutputFilterContext context, DyeColor control) {
        return isActive() ? 15 : 0;
    }

    @Override
    public void updateOutput(RedstoneOutputFilterContext context, DyeColor control) {
        var state = EIODataComponents.REDSTONE_TLATCH_FILTER.get(stack);
        boolean activeInput = context.isActive(control);
        if (activeInput && state.deactivated()) {
            setState(!state.active(), false);
        } else if (!activeInput && !state.deactivated()) {
            setState(state.active(), true);
        }
    }

    public boolean isActive() {
        return EIODataComponents.REDSTONE_TLATCH_FILTER.get(stack).active();
    }

    public boolean isDeactivated() {
        return EIODataComponents.REDSTONE_TLATCH_FILTER.get(stack).deactivated();
    }

    public void setState(boolean active, boolean deactivated) {
        EIODataComponents.REDSTONE_TLATCH_FILTER.set(stack, new Component(active, deactivated));
    }

    @Override
    public boolean isConfigured() {
        // TLATCH filter has no user configuration - it maintains internal state
        return false;
    }

    public record Component(boolean active, boolean deactivated) {
        public static final Codec<Component> CODEC = RecordCodecBuilder.create(instance -> instance
                // Older port builds saved only the input state. Start their missing output low.
                .group(Codec.BOOL.optionalFieldOf("active", false).forGetter(Component::active),
                        Codec.BOOL.fieldOf("deactivated").forGetter(Component::deactivated))
                .apply(instance, Component::new));

        public static final PacketCodec<Component> STREAM_CODEC = PacketCodec.composite(PacketCodecs.BOOL,
                Component::active, PacketCodecs.BOOL, Component::deactivated, Component::new);
    }
}

package com.enderio.enderio.compat.laserio;

import net.minecraft.core.Direction;
import com.direwolf20.laserio.common.items.cards.BaseCard;
import com.direwolf20.laserio.setup.Registration;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.FluidFilter;
import com.enderio.enderio.api.filter.ItemFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import java.util.function.BiFunction;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class LaserIOCompat {
    public static final BiFunction<ItemStack, Direction, ItemFilter> CARD_ITEM_FILTER_PROVIDER = (stack,
        v) -> new LaserItemFilter(BaseCard.getFilter(stack));

    public static final BiFunction<ItemStack, Direction, ItemFilter> ITEM_FILTER_PROVIDER = (stack,
        v) -> new LaserItemFilter(stack);

    public static final BiFunction<ItemStack, Direction, FluidFilter> CARD_FLUID_FILTER_PROVIDER = (stack,
        v) -> new LaserFluidFilter(BaseCard.getFilter(stack));

    public static final BiFunction<ItemStack, Direction, FluidFilter> FLUID_FILTER_PROVIDER = (stack,
        v) -> new LaserFluidFilter(stack);

    public static void init(IEventBus eventBus) {
        eventBus.addListener(LaserIOCompat::registerCapEvent);
    }

    private static void registerCapEvent(RegisterCapabilitiesEvent event) {
        // Register cards
        event.registerItem(EnderIOCapabilities.ITEM_FILTER, CARD_ITEM_FILTER_PROVIDER, Registration.Card_Item.get());
        event.registerItem(EnderIOCapabilities.FLUID_FILTER, CARD_FLUID_FILTER_PROVIDER, Registration.Card_Fluid.get());

        // Register raw filters
        event.registerItem(EnderIOCapabilities.ITEM_FILTER, ITEM_FILTER_PROVIDER, Registration.Filter_Basic.get());
        event.registerItem(EnderIOCapabilities.FLUID_FILTER, FLUID_FILTER_PROVIDER, Registration.Filter_Basic.get());
    }
}

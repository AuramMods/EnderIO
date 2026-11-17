package com.enderio.enderio.foundation.soul;

import net.minecraft.core.Direction;
import com.enderio.enderio.api.soul.binding.ComponentSoulBindable;
import com.enderio.enderio.api.soul.binding.ReadOnlyComponentSoulBindable;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.api.soul.storage.SingleComponentSoulHandler;
import com.enderio.enderio.api.soul.storage.SoulHandler;
import com.enderio.enderio.api.soul.Soul;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiFunction;

public class SoulCapabilityProviders {

    public static final BiFunction<ItemStack, Direction, SoulBindable> COMPONENT_SOUL_BINDABLE_PROVIDER = (stack,
        ctx) -> new ComponentSoulBindable(stack, Soul.ITEM_DATA);

    public static final BiFunction<ItemStack, Direction, SoulBindable> READ_ONLY_COMPONENT_SOUL_BINDABLE_PROVIDER = (stack,
        ctx) -> new ReadOnlyComponentSoulBindable(stack, Soul.ITEM_DATA);

    // Only allows access to the capability if one storage is in the stack.
    public static final BiFunction<ItemStack, Direction, SoulHandler> SINGLE_COMPONENT_SOUL_HANDLER_PROVIDER = (stack,
        ctx) -> new SingleComponentSoulHandler(stack, Soul.ITEM_DATA);
}

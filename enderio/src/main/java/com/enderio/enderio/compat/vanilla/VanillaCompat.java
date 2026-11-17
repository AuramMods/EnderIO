package com.enderio.enderio.compat.vanilla;

import net.minecraft.core.Direction;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.api.soul.storage.SoulHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import java.util.function.BiFunction;
import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.core.common.capability.BoundCapabilityProvider;
import com.enderio.enderio.EnderIO;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import java.util.List;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class VanillaCompat {

    public static final BiFunction<ItemStack, Direction, SoulBindable> SPAWN_EGG_BINDABLE_PROVIDER =
        (stack, v) -> new SpawnEggSoulBindable(stack);

    public static final BiFunction<ItemStack, Direction, SoulHandler> SPAWN_EGG_HANDLER_PROVIDER =
        (stack, v) -> new SpawnEggSoulHandler(stack);

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        if (event.getObject().getItem() instanceof SpawnEggItem) {
            ItemCapabilityRegistry.attach(event, EnderIO.rl("spawn_egg_soul"), List.of(
                new BoundCapabilityProvider.Binding<>(EnderIOCapabilities.SOUL_BINDABLE_ITEM, SPAWN_EGG_BINDABLE_PROVIDER),
                new BoundCapabilityProvider.Binding<>(EnderIOCapabilities.SOUL_HANDLER_ITEM, SPAWN_EGG_HANDLER_PROVIDER)));
        }
    }
}

package com.enderio.core.common.capability;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Attaches stack-owned views of item data through Forge's real attachment event. */
public final class ItemCapabilityRegistry {
    private final ResourceLocation attachmentId;
    private final Map<ResourceLocation, List<BoundCapabilityProvider.Binding<ItemStack, ?>>> items = new HashMap<>();

    public ItemCapabilityRegistry(ResourceLocation attachmentId) {
        this.attachmentId = attachmentId;
    }

    public <C> void register(Capability<C> capability,
            BiFunction<? super ItemStack, @Nullable Direction, @Nullable C> factory, ResourceLocation... itemIds) {
        var binding = new BoundCapabilityProvider.Binding<ItemStack, C>(capability, factory);
        for (var id : itemIds) items.computeIfAbsent(id, ignored -> new ArrayList<>()).add(binding);
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, this::attachRegisteredItem);
    }

    private void attachRegisteredItem(AttachCapabilitiesEvent<ItemStack> event) {
        var bindings = items.get(ForgeRegistries.ITEMS.getKey(event.getObject().getItem()));
        if (bindings != null) attach(event, attachmentId, bindings);
    }

    public static void attach(AttachCapabilitiesEvent<ItemStack> event, ResourceLocation id,
            List<BoundCapabilityProvider.Binding<ItemStack, ?>> bindings) {
        var stack = event.getObject();
        var provider = new BoundCapabilityProvider<>(stack, bindings);
        event.addCapability(id, provider);
        event.addListener(provider::invalidate);
    }

    /** Call after replacing item data; other mods' attached providers retain their own lifetimes. */
    public static void refresh(ItemStack stack) {
        BoundCapabilityProvider.refresh(stack);
    }
}

package com.enderio.modded_conduits.common.modules.mekanism.laserio;

import com.direwolf20.laserio.common.items.cards.BaseCard;
import com.direwolf20.laserio.setup.Registration;
import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

public final class MekanismLaserIOCompat {
    public static void init(IEventBus eventBus) {
        var registry = new ItemCapabilityRegistry(ResourceLocation.parse("enderio:laserio_chemical_filters"));
        registry.register(MekanismModule.Capabilities.CHEMICAL_FILTER,
            (stack, side) -> new LaserChemicalFilter(stack), Registration.Filter_Basic.getId());
        // LaserIO 1.20.1 has no built-in chemical card. Bind by ID if an extension supplies it.
        registry.register(MekanismModule.Capabilities.CHEMICAL_FILTER,
            (stack, side) -> new LaserChemicalFilter(() -> BaseCard.getFilter(stack)),
            ResourceLocation.parse("laserio:card_chemical"));
        registry.register();
    }
}

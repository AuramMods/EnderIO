package com.enderio.enderio.foundation.io.fluid;

import com.enderio.core.CoreNBTKeys;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Restores fluids stored by Forge 6's item fluid handler. */
public final class Forge6ItemFluidData {
    private Forge6ItemFluidData() {}

    public static void restore(ItemStack stack) {
        var saved = stack.getTagElement(CoreNBTKeys.FLUID);
        if (saved == null) return;
        if (EIODataComponents.ITEM_FLUID_CONTENT.has(stack)) {
            stack.removeTagKey(CoreNBTKeys.FLUID);
            return;
        }

        var legacyId = ResourceLocation.tryParse(saved.getString("FluidName"));
        if (legacyId == null) return;

        var fuel = saved.copy();
        var currentId = EIOFluids.legacyFluidId(legacyId);
        if (currentId != null) fuel.putString("FluidName", currentId.toString());
        var fluid = FluidStack.loadFluidStackFromNBT(fuel);
        if (fluid.isEmpty()) return;

        EIODataComponents.ITEM_FLUID_CONTENT.set(stack, fluid);
        stack.removeTagKey(CoreNBTKeys.FLUID);
    }
}

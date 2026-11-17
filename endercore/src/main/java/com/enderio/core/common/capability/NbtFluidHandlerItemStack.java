package com.enderio.core.common.capability;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

/** Native Forge transfer rules with fluid state shared by the item's typed NBT key. */
public class NbtFluidHandlerItemStack extends FluidHandlerItemStack {
    private final ItemDataKey<FluidStack> key;

    public NbtFluidHandlerItemStack(ItemDataKey<FluidStack> key, ItemStack container, int capacity) {
        super(container, capacity);
        this.key = key;
    }

    @Override
    public FluidStack getFluid() {
        return key.getOrDefault(container, FluidStack.EMPTY);
    }

    @Override
    public void setFluid(FluidStack fluid) {
        if (fluid.isEmpty()) key.remove(container);
        else key.set(container, fluid);
    }

    @Override
    protected void setContainerToEmpty() {
        key.remove(container);
    }
}

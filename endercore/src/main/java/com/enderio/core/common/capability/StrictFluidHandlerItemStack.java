package com.enderio.core.common.capability;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Predicate;

/**
 * FluidHandler for Items which only accept specific fluids
 */
public class StrictFluidHandlerItemStack extends NbtFluidHandlerItemStack {

    private final Predicate<Fluid> fluidPredicate;

    public StrictFluidHandlerItemStack(ItemDataKey<FluidStack> key, ItemStack container, int capacity, Fluid validFluid) {

        this(key, container, capacity, fluid -> fluid == validFluid);
    }

    public StrictFluidHandlerItemStack(ItemDataKey<FluidStack> key, ItemStack container, int capacity, TagKey<Fluid> validFluid) {

        this(key, container, capacity, fluid -> fluid.is(validFluid));
    }

    public StrictFluidHandlerItemStack(ItemDataKey<FluidStack> key, ItemStack container, int capacity, Predicate<Fluid> isFluidValid) {

        super(key, container, capacity);
        fluidPredicate = isFluidValid;
    }

    @Override
    public void setFluid(FluidStack fluid) {
        super.setFluid(fluid);
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid) {
        return fluidPredicate.test(fluid.getFluid());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return fluidPredicate.test(stack.getFluid());
    }

    //Convert to present fluid if needed
    @Override
    public int fill(FluidStack resource, FluidAction doFill) {
        if (!getFluid().isEmpty() && fluidPredicate.test(resource.getFluid())) {
            int amount = resource.getAmount();
            resource = getFluid().copy();
            resource.setAmount(amount);
        }
        return super.fill(resource, doFill);
    }
}

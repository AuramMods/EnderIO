package com.enderio.enderio.content.storage.fluid_tank;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class InternalTankTasks {

    public static <T extends MachineBlockEntity & FluidTankUser> void fillInternal(
        T blockEntity,
        TankAccess tank,
        SingleSlotAccess fluidFillInput,
        SingleSlotAccess fluidFillOutput) {

        ItemStack inputItem = fluidFillInput.getItemStack(blockEntity);
        if (inputItem.isEmpty()) {
            return;
        }

        // Only ever handle a single item at a time.
        ItemStack singleInputItem = inputItem.copyWithCount(1);
        IFluidHandlerItem fluidHandlerItem = singleInputItem.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandlerItem == null) {
            return;
        }

        // Discover the fluid without changing the copied container yet.
        FluidStack availableFluid = fluidHandlerItem.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);

        // See if we can insert this fluid into the block's tank.
        int filled = tank.fill(blockEntity, availableFluid, IFluidHandler.FluidAction.SIMULATE);
        if (filled <= 0) {
            return;
        }

        // Drain only what the tank can accept, on the disposable single-item copy.
        // Buckets reject a partial drain; larger containers retain their remainder.
        // The simulated fluid alone must never be deposited while retaining a full item.
        availableFluid = fluidHandlerItem.drain(EnderFluidUtil.copyWithAmount(availableFluid, filled),
            IFluidHandler.FluidAction.EXECUTE);
        if (availableFluid.isEmpty()
            || tank.fill(blockEntity, availableFluid, IFluidHandler.FluidAction.SIMULATE) != availableFluid.getAmount()) {
            return;
        }

        // Get the resulting emptied container
        ItemStack resultStack = fluidHandlerItem.getContainer();

        // If this is the only input, and it's not fully empty we will retain it in the input slot, so long as the
        //  destination tank is not full
        if (inputItem.getCount() == 1 && !EnderFluidUtil.isEmpty(fluidHandlerItem) && !tank.isFull(blockEntity)) {
            tank.fill(blockEntity, availableFluid, IFluidHandler.FluidAction.EXECUTE);
            fluidFillInput.setStackInSlot(blockEntity, resultStack);
        } else if (!fluidFillInput.extractItem(blockEntity, 1, true).isEmpty() &&
            fluidFillOutput.insertItem(blockEntity, resultStack, true).isEmpty()) {

            tank.fill(blockEntity, availableFluid, IFluidHandler.FluidAction.EXECUTE);
            fluidFillInput.extractItem(blockEntity, 1, false);
            fluidFillOutput.insertItem(blockEntity, resultStack, false);
        }
    }

    // TODO: enable fluid tanks to receive stackable fluid containers
    public static <T extends MachineBlockEntity & FluidTankUser> void drainInternal(
        T blockEntity,
        TankAccess tank,
        SingleSlotAccess fluidDrainInput,
        SingleSlotAccess fluidDrainOutput
    ) {
        ItemStack inputItem = fluidDrainInput.getItemStack(blockEntity);
        if (inputItem.isEmpty()) {
            return;
        }

        // Only ever handle a single item at a time.
        ItemStack singleInputItem = inputItem.copyWithCount(1);
        IFluidHandlerItem fluidHandlerItem = singleInputItem.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandlerItem == null) {
            return;
        }

        // See what fluid the tank can drain into the item
        FluidStack availableFluid = tank.drain(blockEntity, Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);

        // See if we can insert this fluid into the container
        int filled = fluidHandlerItem.fill(availableFluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return;
        }

        // Get the resulting filled item
        ItemStack resultStack = fluidHandlerItem.getContainer();

        // If this is the only input, and it isn't completely full, and the destination still has fluid, we will retain it
        if (inputItem.getCount() == 1 && !EnderFluidUtil.isFull(fluidHandlerItem) && !tank.isEmpty(blockEntity)) {
            tank.drain(blockEntity, EnderFluidUtil.copyWithAmount(availableFluid, filled), IFluidHandler.FluidAction.EXECUTE);
            fluidDrainInput.setStackInSlot(blockEntity, resultStack);
        } else if (!fluidDrainInput.extractItem(blockEntity, 1, true).isEmpty() &&
            fluidDrainOutput.insertItem(blockEntity, resultStack, true).isEmpty()) {

            tank.drain(blockEntity, EnderFluidUtil.copyWithAmount(availableFluid, filled), IFluidHandler.FluidAction.EXECUTE);
            fluidDrainInput.extractItem(blockEntity, 1, false);
            fluidDrainOutput.insertItem(blockEntity, resultStack, false);
        }
    }

    public static <T extends MachineBlockEntity & FluidTankUser> void tryMendTool(
        T blockEntity,
        TankAccess tank,
        SingleSlotAccess fluidDrainInput,
        SingleSlotAccess fluidDrainOutput
    ) {
        FluidStack fluid = tank.getFluid(blockEntity);

        if (!fluid.isEmpty() && fluid.getFluid().is(com.enderio.enderio.foundation.tag.EIOTags.Fluids.EXPERIENCE)
            && fluidDrainOutput.getItemStack(blockEntity).isEmpty()) {

            ItemStack tool = fluidDrainInput.getItemStack(blockEntity);

            var mendingEnchantment = Enchantments.MENDING;

            if (tool.isDamageableItem() && tool.getEnchantmentLevel(mendingEnchantment) > 0) {

                ItemStack repairedTool = tool.copy();

                int damage = tool.getDamageValue();
                int xpAmount = (int) Math.floor(damage / tool.getXpRepairRatio());
                int fluidAmount = xpAmount * ExperienceUtil.EXP_TO_FLUID;

                FluidStack drainedXp = tank.drain(blockEntity, fluidAmount, IFluidHandler.FluidAction.EXECUTE);
                int repairAmount = (int) Math.floor(drainedXp.getAmount() * tool.getXpRepairRatio() / ExperienceUtil.EXP_TO_FLUID);
                repairedTool.setDamageValue(Math.max(0, damage - repairAmount));

                fluidDrainInput.setStackInSlot(blockEntity, ItemStack.EMPTY);
                fluidDrainOutput.setStackInSlot(blockEntity, repairedTool);
            }
        }
    }
}

package com.enderio.enderio.content.tools;

import com.enderio.core.common.capability.StrictFluidHandlerItemStack;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.foundation.io.fluid.Forge6ItemFluidData;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.function.BiFunction;

public class LevitationStaffItem extends PoweredToggledItem {
    private static final String LEGACY_TOGGLE = "ToggleState";

    public static final BiFunction<ItemStack, Direction, IFluidHandlerItem> FLUID_HANDLER_PROVIDER = (stack,
            v) -> new StrictFluidHandlerItemStack(EIODataComponents.ITEM_FLUID_CONTENT, stack, 1000,
                    EIOTags.Fluids.STAFF_OF_LEVITY_FUEL);

    public LevitationStaffItem(Properties properties) {
        super(properties);
    }

    public static void restoreForge6State(ItemStack stack) {
        var saved = stack.getTag();
        if (saved == null) return;
        if (saved.contains(LEGACY_TOGGLE, Tag.TAG_BYTE)) {
            if (saved.getBoolean(LEGACY_TOGGLE)) EIODataComponents.TOGGLED.set(stack, true);
            stack.removeTagKey(LEGACY_TOGGLE);
        }
        Forge6ItemFluidData.restore(stack);
    }

    @Override
    protected int getEnergyUse() {
        return BaseConfig.COMMON.ITEMS.LEVITATION_STAFF_ENERGY_USE.get();
    }

    @Override
    public int getMaxEnergy() {
        return BaseConfig.COMMON.ITEMS.LEVITATION_STAFF_MAX_ENERGY.get();
    }

    @Override
    protected boolean hasCharge(ItemStack stack) {
        if (!ItemStackEnergy.hasEnergy(stack, getEnergyUse())) {
            return false;
        }

        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            // TODO: Config for consumption amount
            return !fluidHandler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
        }

        return false;
    }

    @Override
    protected void consumeCharge(ItemStack stack) {
        super.consumeCharge(stack);

        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            // TODO: Config for consumption amount
            fluidHandler.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    protected void setFullCharge(ItemStack stack) {
        super.setFullCharge(stack);

        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            if (fluidHandler instanceof StrictFluidHandlerItemStack strictFluidHandlerItemStack) {
                strictFluidHandlerItemStack.setFluid(
                        new FluidStack(EIOFluids.VAPOR_OF_LEVITY.source().get(), fluidHandler.getTankCapacity(0)));
            }
        }
    }

    @Override
    protected void onTickWhenActive(Player player, ItemStack stack, Level level, Entity entity, int slotId,
            boolean isSelected) {
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return level.isClientSide() ? InteractionResultHolder.pass(player.getItemInHand(hand)) : super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // The dedicated server owns charge and flight. The client receives the normal item and effect packets.
        if (!level.isClientSide()) super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }
}

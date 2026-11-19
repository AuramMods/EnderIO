package com.enderio.enderio.content.tools.vials;

import com.enderio.core.common.capability.StrictFluidHandlerItemStack;
import com.enderio.core.common.item.ICustomCreativeTabEntries;
import com.enderio.core.common.util.TooltipUtil;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.tools.ToolsLang;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.google.common.primitives.Ints;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.List;
import java.util.function.BiFunction;

public class VoidVialItem extends Item implements ICustomCreativeTabEntries {

    public static final ResourceLocation FILLED_MODEL_PROPERTY = EnderIO.rl("void_vial_filled");

    public static final BiFunction<ItemStack, Direction, IFluidHandlerItem> FLUID_HANDLER_PROVIDER = (stack, v) -> new StrictFluidHandlerItemStack(
        EIODataComponents.ITEM_FLUID_CONTENT, stack, Ints.saturatedCast(ExperienceUtil.getFluidFromLevel(10)), EIOTags.Fluids.EXPERIENCE);

    public VoidVialItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void addAdditionalCreativeTabEntries(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        var filledStack = new ItemStack(this);
        var fluidHandler = filledStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        fluidHandler.fill(new FluidStack(EIOFluids.XP_JUICE.source().get(), Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
        output.accept(filledStack);
    }

    public static boolean isFilled(ItemStack stack) {
        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return fluidHandler != null && !fluidHandler.getFluidInTank(0).isEmpty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        IFluidHandlerItem capability = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (capability == null || capability.getFluidInTank(0).getAmount() < ExperienceUtil.EXP_TO_FLUID) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(usedHand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (level.isClientSide || !(livingEntity instanceof Player player)) {
            return stack;
        }
        IFluidHandlerItem capability = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (capability == null) {
            return stack;
        }

        int amount = capability.getFluidInTank(0).getAmount();
        if (amount < ExperienceUtil.EXP_TO_FLUID) {
            return stack;
        }

        FluidStack result = capability.drain((amount / ExperienceUtil.EXP_TO_FLUID) * ExperienceUtil.EXP_TO_FLUID, IFluidHandler.FluidAction.EXECUTE);
        player.giveExperiencePoints(result.getAmount() / ExperienceUtil.EXP_TO_FLUID);
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isFilled(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Color from XP Juice texture
        return 0xFF66FF03;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            return Math.round(fluidHandler.getFluidInTank(0).getAmount() * 13f / fluidHandler.getTankCapacity(0));
        }

        return 0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFilled(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(ToolsLang.VOID_VIAL_HINT);

        var tankCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (tankCap != null) {
            FluidStack storedFluid = tankCap.getFluidInTank(0);
            int amount = tankCap.getFluidInTank(0).getAmount();
            int capacity = tankCap.getTankCapacity(0);

            if (amount > 0) {
                tooltipComponents.add(TooltipUtil.styledWithArgs(EIOCommonLang.FLUID_TANK_TOOLTIP, amount, capacity, storedFluid.getDisplayName()));

                var xp = ExperienceUtil.getLevelFromFluidWithLeftover(amount);
                tooltipComponents.add(TooltipUtil.styledWithArgs(ToolsLang.VOID_VIAL_STORED_EXPERIENCE, xp.level(), xp.experience()));
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return (int) (1.6F * 20.0F); // default food time
    }

    /** Stores whole points from one accepted pickup and leaves the rest for vanilla Mending and XP. */
    public static int captureExperience(Player player, int points) {
        ItemStack stack = player.getOffhandItem();
        if (!(stack.getItem() instanceof VoidVialItem)) {
            stack = player.getMainHandItem();
        }
        if (!(stack.getItem() instanceof VoidVialItem)) {
            return points;
        }
        var vial = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (vial == null) {
            return points;
        }
        int pointVolume = ExperienceUtil.EXP_TO_FLUID;
        int space = vial.getTankCapacity(0) - vial.getFluidInTank(0).getAmount();
        int offeredPoints = Math.min(points, space / pointVolume);
        if (offeredPoints <= 0) {
            return points;
        }
        var fluid = new FluidStack(EIOFluids.XP_JUICE.source().get(), offeredPoints * pointVolume);
        return points - vial.fill(fluid, IFluidHandler.FluidAction.EXECUTE) / pointVolume;
    }
}

package com.enderio.enderio.content.cold_fire;

import net.minecraft.core.Direction;
import com.enderio.core.common.capability.StrictFluidHandlerItemStack;
import com.enderio.core.common.item.ICustomCreativeTabEntries;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.function.BiFunction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.apache.logging.log4j.LogManager;

import java.util.List;

public class ColdFireIgniter extends Item implements ICustomCreativeTabEntries {
    private static final int FUEL_PER_IGNITION = 10;

    public static final BiFunction<ItemStack, Direction, IFluidHandlerItem> FLUID_HANDLER_PROVIDER =
        (stack, v) -> new StrictFluidHandlerItemStack(EIODataComponents.ITEM_FLUID_CONTENT, stack, 1000, EIOTags.Fluids.COLD_FIRE_IGNITER_FUEL);

    public ColdFireIgniter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        var fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler == null) {
            LogManager.getLogger().warn("could not find FluidCapability on ColdFireIgniter");
            return super.useOn(context);
        }

        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        BlockState fire = EIOBlocks.COLD_FIRE.get().getStateWithAge(level, pos, 0);
        if (!level.isEmptyBlock(pos) || !fire.canSurvive(level, pos)
            || fluidHandler.drain(FUEL_PER_IGNITION, IFluidHandler.FluidAction.SIMULATE).getAmount() != FUEL_PER_IGNITION) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            if (!level.setBlock(pos, fire, Block.UPDATE_ALL_IMMEDIATE)) return InteractionResult.FAIL;
            fluidHandler.drain(FUEL_PER_IGNITION, IFluidHandler.FluidAction.EXECUTE);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
            }
        }
        level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
            level.getRandom().nextFloat() * 0.4F + 0.8F);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null) != null;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x99BD42;
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
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level tooltipContext, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, tooltipContext, components, flag);

        var tankCap = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (tankCap != null) {
            boolean isOneTank = tankCap.getTanks() == 1;
            if (!isOneTank) {
                components.add(Component.literal("Fluids:"));
            }
            for (int i = 0; i < tankCap.getTanks(); i++) {
                String prefix = isOneTank ? "" : i + ": ";
                Component postFix = tankCap.getFluidInTank(i).isEmpty() ? Component.literal("") : tankCap.getFluidInTank(i).getDisplayName();
                components.add(Component.literal(prefix + tankCap.getFluidInTank(i).getAmount() + " / " + tankCap.getTankCapacity(i) + " ").append(postFix));
            }
        }
    }

    @Override
    public void addAdditionalCreativeTabEntries(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        ItemStack is = new ItemStack(this);

        var fluidHandler = is.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            if (fluidHandler instanceof StrictFluidHandlerItemStack strictFluidHandlerItemStack) {
                strictFluidHandlerItemStack.setFluid(new FluidStack(EIOFluids.VAPOR_OF_LEVITY.source().get(), fluidHandler.getTankCapacity(0)));
                output.accept(is);
            }
        }
    }
}

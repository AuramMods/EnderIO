package com.enderio.enderio.content.storage.fluid_tank;

import net.minecraft.core.Direction;
import com.enderio.enderio.client.content.fluid_tank.FluidTankBEWLR;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.enderio.core.client.item.AdvancedTooltipProvider;
import com.enderio.core.common.util.TooltipUtil;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import com.enderio.core.common.capability.NbtFluidHandlerItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FluidTankBlockItem extends BlockItem implements AdvancedTooltipProvider {

    public static final BiFunction<ItemStack, Direction, IFluidHandlerItem> FLUID_HANDLER_PROVIDER = (stack,
            v) -> new NbtFluidHandlerItemStack(EIODataComponents.ITEM_FLUID_CONTENT, stack,
                    ((FluidTankBlockItem) stack.getItem()).capacity);

    protected final int capacity;

    public FluidTankBlockItem(FluidTankBlock block, Properties properties, int capacity) {
        super(block, properties);
        this.capacity = capacity;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                // Resolve only when rendering; Minecraft is unavailable during datagen.
                return FluidTankBEWLR.INSTANCE;
            }
        });
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;// TODO: when fluid tank entity accepts item stacks of more than 1 in the
                 // internalDrain/fill. Remove this method to allow fluid tank items to
                 // originalStack to
                 // 64.
    }

    @Override
    public void addCommonTooltips(ItemStack itemStack, @Nullable Player player, List<Component> tooltips) {
        var fluidHandler = itemStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandler != null) {
            if (fluidHandler instanceof FluidHandlerItemStack itemFluidHandler) {
                if (itemFluidHandler.getFluid().isEmpty()) {
                    tooltips.add(TooltipUtil.style(EIOCommonLang.TANK_EMPTY_STRING));
                } else {
                    tooltips.add(TooltipUtil.styledWithArgs(EIOCommonLang.FLUID_TANK_TOOLTIP,
                            itemFluidHandler.getFluid().getAmount(), capacity,
                            itemFluidHandler.getFluid().getFluid().getFluidType().getDescription().getString()));
                }
            }
        }
    }
}

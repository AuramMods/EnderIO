package com.enderio.enderio.content.storage.fluid_tank;

import com.enderio.core.CoreNBTKeys;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.LegacyMachineData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.enderio.enderio.client.content.fluid_tank.FluidTankBEWLR;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.enderio.core.client.item.AdvancedTooltipProvider;
import com.enderio.core.common.util.TooltipUtil;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import com.enderio.core.common.capability.NbtFluidHandlerItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FluidTankBlockItem extends BlockItem implements AdvancedTooltipProvider {

    public static final BiFunction<ItemStack, Direction, IFluidHandlerItem> FLUID_HANDLER_PROVIDER = (stack,
            v) -> new TankItemStorage(stack, ((FluidTankBlockItem) stack.getItem()).capacity);

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
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                  BlockState state) {
        var blockData = BlockItem.getBlockEntityData(stack);
        if (blockData != null && (blockData.contains(CoreNBTKeys.FLUID) || blockData.contains("IoConfig"))) {
            // Vanilla merges the carried tag into a freshly serialized block entity.
            // Normalize first so the fresh machine's empty fields cannot mask the old data.
            var normalized = LegacyMachineData.withNativeFields(blockData);
            if (blockData.contains(CoreNBTKeys.FLUID) && !blockData.contains(CoreNBTKeys.FLUIDS)) {
                normalized.put(CoreNBTKeys.FLUIDS,
                    MachineFluidHandler.savedStorageWithTankList(MachineFluidHandler.savedStorage(blockData)));
            }
            if (normalized.contains(MachineNBTKeys.IO_CONFIG, Tag.TAG_COMPOUND)) {
                normalized.put(MachineNBTKeys.IO_CONFIG, IOConfig.parseOptional(normalized.getCompound(MachineNBTKeys.IO_CONFIG)).save());
            }
            var carried = stack.copy();
            carried.addTagElement(BLOCK_ENTITY_TAG, normalized);
            return super.updateCustomBlockEntityTag(pos, level, player, carried, state);
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
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

    private static final class TankItemStorage extends NbtFluidHandlerItemStack {
        private TankItemStorage(ItemStack stack, int capacity) {
            super(EIODataComponents.ITEM_FLUID_CONTENT, stack, capacity);
        }

        @Override
        public FluidStack getFluid() {
            var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(container);
            if (fluid != null) return fluid;
            var blockData = BlockItem.getBlockEntityData(container);
            return blockData == null ? FluidStack.EMPTY : MachineFluidHandler.readSavedTank(MachineFluidHandler.savedStorage(blockData), 0);
        }

        @Override
        public void setFluid(FluidStack fluid) {
            super.setFluid(fluid);
            // Vanilla first applies BlockEntityTag when placing. Retire only its old tank
            // entry so fluid drained from the item can't be restored from that tag.
            var blockData = BlockItem.getBlockEntityData(container);
            if (blockData != null) {
                clearStoredTank(blockData, CoreNBTKeys.FLUID);
                clearStoredTank(blockData, CoreNBTKeys.FLUIDS);
            }
        }

        @Override
        protected void setContainerToEmpty() {
            setFluid(FluidStack.EMPTY);
        }

        private static void clearStoredTank(CompoundTag blockData, String key) {
            var saved = blockData.getCompound(key);
            if (saved.contains(CoreNBTKeys.TANKS, Tag.TAG_LIST)) {
                var entries = saved.getList(CoreNBTKeys.TANKS, Tag.TAG_COMPOUND);
                entries.removeIf(tag -> ((CompoundTag) tag).getInt(MachineFluidHandler.TANK_INDEX) == 0);
                if (entries.isEmpty()) blockData.remove(key);
            } else if (saved.contains("FluidName", Tag.TAG_STRING)) {
                blockData.remove(key);
            }
        }
    }
}

package com.enderio.enderio.content.machines.vacuum.chest;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.api.filter.ItemFilterContext;
import com.enderio.enderio.api.filter.ItemFilterDirection;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.machines.vacuum.VacuumMachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout.Builder;
import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class VacuumChestBlockEntity extends VacuumMachineBlockEntity<ItemEntity> {
    private final RangedWrapper storage = new RangedWrapper(getInventory(), 0, FILTER.getIndex());
    private final ItemFilterContext insertion = new ItemFilterContext(storage, ItemFilterDirection.INSERT);

    public VacuumChestBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.VACUUM_CHEST.get(), worldPosition, blockState, ItemEntity.class);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VacuumChestMenu(containerId, inventory, this);
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return extractableGUISlot(MachineInventoryLayout.builder(), 27)
                .slot(slot -> slot.guiInsert()
                        .guiExtract()
                        .filter((i, s) -> s.getCapability(EnderIOCapabilities.ITEM_FILTER).orElse(null) != null))
                .slotAccess(FILTER)
                .build();
    }

    @Override
    public void handleEntity(ItemEntity entity) {
        ItemStack offered = entity.getItem();
        ItemFilter filter = installedFilter();
        int allowed = filter == null ? offered.getCount()
            : Math.min(offered.getCount(), filter.test(offered.copy(), insertion).getCount());
        if (allowed == 0) {
            return;
        }

        ItemStack remainder = ItemHandlerHelper.insertItem(storage, offered.copyWithCount(allowed), false);
        int remaining = offered.getCount() - allowed + remainder.getCount();
        if (remaining == 0) {
            entity.discard();
        } else if (remaining < offered.getCount()) {
            entity.setItem(offered.copyWithCount(remaining));
        }
    }

    @Override
    public String getColor() {
        return MachinesConfig.CLIENT.BLOCKS.VACUUM_CHEST_RANGE_COLOR.get();
    }

    @Override
    public Predicate<ItemEntity> getFilter() {
        ItemFilter filter = installedFilter();
        return filter == null ? super.getFilter() : entity -> !filter.test(entity.getItem().copy(), insertion).isEmpty();
    }

    @Nullable
    private ItemFilter installedFilter() {
        return FILTER.getItemStack(this).getCapability(EnderIOCapabilities.ITEM_FILTER).orElse(null);
    }

    // Slot config

    public Builder extractableGUISlot(Builder builder, int count) {
        for (int i = 0; i < count; i++) {
            builder.slot(slot -> slot.guiInsert().guiExtract().extract());
        }
        return builder;
    }
}

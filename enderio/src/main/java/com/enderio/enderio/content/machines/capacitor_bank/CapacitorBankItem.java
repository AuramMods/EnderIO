package com.enderio.enderio.content.machines.capacitor_bank;

import net.minecraft.core.Direction;
import com.enderio.enderio.foundation.block.entity.multienergy.CapacityTier;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiFunction;
import com.enderio.core.common.energy.StackedNbtEnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

public class CapacitorBankItem extends BlockItem {

    public static final BiFunction<ItemStack, Direction, IEnergyStorage> ENERGY_STORAGE_PROVIDER =
        (stack, v) -> new StackedNbtEnergyStorage(stack, EIODataComponents.ENERGY,
            ((CapacitorBankItem) stack.getItem()).tier.getStorageCapacity());

    private final CapacityTier tier;

    public CapacitorBankItem(CapacitorBankBlock block, Item.Properties properties) {
        super(block, properties);
        this.tier = block.getTier();
    }
}

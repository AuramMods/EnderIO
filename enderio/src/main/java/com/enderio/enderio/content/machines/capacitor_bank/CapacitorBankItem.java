package com.enderio.enderio.content.machines.capacitor_bank;

import net.minecraft.core.Direction;
import com.enderio.enderio.foundation.block.entity.multienergy.CapacityTier;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiFunction;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

public class CapacitorBankItem extends BlockItem {

    public static final BiFunction<ItemStack, Direction, IEnergyStorage> ENERGY_STORAGE_PROVIDER =
        (stack, v) -> new ComponentEnergyStorage(stack, EIODataComponents.ENERGY, ((CapacitorBankItem)stack.getItem()).tier.getStorageCapacity()) {
            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                int extract = toExtract / stack.getCount();
                return super.extractEnergy(extract, simulate) * stack.getCount();
            }

            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                int receive = toReceive / stack.getCount();
                return super.receiveEnergy(receive, simulate) * stack.getCount();
            }
        };

    private final CapacityTier tier;

    public CapacitorBankItem(CapacitorBankBlock block, Item.Properties properties) {
        super(block, properties);
        this.tier = block.getTier();
    }
}

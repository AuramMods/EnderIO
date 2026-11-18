package com.enderio.enderio.foundation.soul;

import com.enderio.core.CoreNBTKeys;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.energy.PoweredMachineEnergyStorage;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Reads entity payloads and portable soul machine state written by Forge Ender IO 6.x. */
public final class Forge6SoulData {
    private Forge6SoulData() {}

    public static boolean isLegacyStorage(CompoundTag storage) {
        return storage.contains("Entity", Tag.TAG_COMPOUND);
    }

    public static Soul read(CompoundTag storage) {
        var entity = storage.getCompound("Entity");
        var id = ResourceLocation.tryParse(entity.getString(Entity.ID_TAG));
        var type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        return type == null ? Soul.EMPTY : new Soul(type, entity.copy());
    }

    public static void restoreItem(ItemStack stack, boolean soulMachine) {
        var oldData = BlockItem.getBlockEntityData(stack);
        if (oldData == null) return;
        var remaining = oldData.copy();

        var storage = remaining.getCompound(MachineNBTKeys.ENTITY_STORAGE);
        if (isLegacyStorage(storage)) {
            var soul = read(storage);
            var modern = Soul.ITEM_DATA.get(stack);
            boolean vial = stack.getItem() instanceof SoulVialItem;
            // Soul machines have an empty registered default. A vial's explicit data wins.
            if (!soul.isEmpty() && (modern == null || soulMachine && modern.isEmpty())) {
                Soul.ITEM_DATA.set(stack, soul);
                if (vial) restoreVialHealth(stack, storage);
                remaining.remove(MachineNBTKeys.ENTITY_STORAGE);
            }
        }
        if (soulMachine) restoreMachineProperties(stack, remaining);

        if (!oldData.equals(remaining)) {
            if (remaining.isEmpty()) stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
            else stack.addTagElement(BlockItem.BLOCK_ENTITY_TAG, remaining);
        }
    }

    private static void restoreVialHealth(ItemStack stack, CompoundTag storage) {
        float health = storage.getFloat("MaxHealth");
        if (health > 0 && Float.isFinite(health) && !SoulVialItem.ENTITY_MAX_HEALTH.has(stack)) {
            SoulVialItem.ENTITY_MAX_HEALTH.set(stack, health);
        }
    }

    private static void restoreMachineProperties(ItemStack stack, CompoundTag remaining) {
        if (remaining.contains(CoreNBTKeys.FLUID, Tag.TAG_COMPOUND) && !EIODataComponents.ITEM_FLUID_CONTENT.has(stack)) {
            // Native placement merges a canonical empty tank with the old BlockEntityTag.
            // Install the old fuel as item state so the placement applies it after that merge.
            var fuel = MachineFluidHandler.readSavedTank(remaining.getCompound(CoreNBTKeys.FLUID), 0);
            if (!fuel.isEmpty()) EIODataComponents.ITEM_FLUID_CONTENT.set(stack, fuel);
        }
        var legacyEnergy = remaining.getCompound(MachineNBTKeys.ENERGY);
        if (legacyEnergy.contains(MachineNBTKeys.ENERGY_STORED, Tag.TAG_INT)) {
            if (!PoweredMachineEnergyStorage.ITEM_DATA.has(stack)) {
                PoweredMachineEnergyStorage.ITEM_DATA.set(stack, Math.max(0, legacyEnergy.getInt(MachineNBTKeys.ENERGY_STORED)));
            }
            legacyEnergy.remove(MachineNBTKeys.ENERGY_STORED);
            if (legacyEnergy.isEmpty()) remaining.remove(MachineNBTKeys.ENERGY);
        }
        if (remaining.contains("IoConfig", Tag.TAG_COMPOUND)) {
            if (!IOConfig.ITEM_DATA.has(stack)) {
                IOConfig.ITEM_DATA.set(stack, IOConfig.parseOptional(remaining.getCompound("IoConfig")));
            }
            remaining.remove("IoConfig");
        }
        if (remaining.contains(MachineNBTKeys.REDSTONE_CONTROL, Tag.TAG_ANY_NUMERIC)) {
            int mode = remaining.getInt(MachineNBTKeys.REDSTONE_CONTROL);
            if (mode < 0 || mode >= RedstoneControl.values().length) return;
            if (!RedstoneControl.ITEM_DATA.has(stack)) RedstoneControl.ITEM_DATA.set(stack, RedstoneControl.BY_ID.apply(mode));
            remaining.remove(MachineNBTKeys.REDSTONE_CONTROL);
        }
    }
}

package com.enderio.enderio.foundation.io;

import com.enderio.core.CoreNBTKeys;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Moves the Forge 6.x portable machine fields into their item data. */
public final class Forge6MachineItemData {
    private Forge6MachineItemData() {}

    public static void restoreFluidMachineItem(ItemStack stack) {
        var saved = BlockItem.getBlockEntityData(stack);
        if (saved == null) return;
        var remaining = saved.copy();
        restoreCarriedProperties(stack, remaining);
        if (EIODataComponents.ITEM_FLUID_CONTENT.has(stack)) remaining.remove(CoreNBTKeys.FLUID);
        if (!remaining.equals(saved)) {
            if (remaining.isEmpty()) stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
            else stack.addTagElement(BlockItem.BLOCK_ENTITY_TAG, remaining);
        }
    }

    public static void restoreCarriedProperties(ItemStack stack, CompoundTag remaining) {
        // Placement merges canonical defaults into legacy BlockEntityTag. Item data applies afterward.
        if (remaining.contains(CoreNBTKeys.FLUID, Tag.TAG_COMPOUND) && !EIODataComponents.ITEM_FLUID_CONTENT.has(stack)) {
            var savedFluid = MachineFluidHandler.savedTankData(remaining.getCompound(CoreNBTKeys.FLUID), 0).copy();
            var id = ResourceLocation.tryParse(savedFluid.getString("FluidName"));
            var modernId = id == null ? null : EIOFluids.legacyFluidId(id);
            if (modernId != null) savedFluid.putString("FluidName", modernId.toString());
            StackDataCodecs.OPTIONAL_FLUID.parse(NbtOps.INSTANCE, savedFluid).result()
                .filter(contents -> !contents.isEmpty()).ifPresent(contents -> EIODataComponents.ITEM_FLUID_CONTENT.set(stack, contents));
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

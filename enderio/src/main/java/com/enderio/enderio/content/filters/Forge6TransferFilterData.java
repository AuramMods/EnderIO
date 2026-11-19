package com.enderio.enderio.content.filters;

import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Imports the original Forge filter payload wherever Minecraft loads its item stack. */
public final class Forge6TransferFilterData {
    private static final String ITEM_ENTRIES = "ItemEntries";
    private static final String FLUID_ENTRIES = "FluidEntries";
    private static final String INVERTED = "IsInverted";
    private static final String NBT = "IsNbt";

    private Forge6TransferFilterData() {}

    public static void restoreItemFilter(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_ENTRIES) || EIODataComponents.ITEM_FILTER.has(stack)) return;
        var entries = readEntries(tag.get(ITEM_ENTRIES), StackDataCodecs.OPTIONAL_ITEM, UnaryOperator.identity());
        if (entries == null) return;
        boolean compareTags = tag.getBoolean(NBT);
        EIODataComponents.ITEM_FILTER.set(stack,
            new EnderItemFilter(entries, tag.getBoolean(INVERTED), compareTags, DamageFilterMode.IGNORE, compareTags));
        clearLegacyFields(stack, ITEM_ENTRIES);
    }

    public static void restoreFluidFilter(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(FLUID_ENTRIES) || EIODataComponents.FLUID_FILTER.has(stack)) return;
        var entries = readEntries(tag.get(FLUID_ENTRIES), StackDataCodecs.OPTIONAL_FLUID, Forge6TransferFilterData::remapFluid);
        if (entries == null) return;
        EIODataComponents.FLUID_FILTER.set(stack,
            new EnderFluidFilter(entries, tag.getBoolean(INVERTED), tag.getBoolean(NBT), true));
        clearLegacyFields(stack, FLUID_ENTRIES);
    }

    private static CompoundTag remapFluid(CompoundTag entry) {
        var id = ResourceLocation.tryParse(entry.getString("FluidName"));
        var replacement = id == null ? null : EIOFluids.legacyFluidId(id);
        if (replacement == null) return entry;
        var remapped = entry.copy();
        remapped.putString("FluidName", replacement.toString());
        return remapped;
    }

    @Nullable
    private static <T> List<T> readEntries(@Nullable Tag saved, Codec<T> codec, UnaryOperator<CompoundTag> remap) {
        if (!(saved instanceof ListTag list) || list.size() > 256) return null;
        var entries = new ArrayList<T>(list.size());
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag compound)) return null;
            var decoded = codec.parse(NbtOps.INSTANCE, remap.apply(compound)).result();
            // Keep the complete original payload when a sample cannot be restored.
            if (decoded.isEmpty()) return null;
            entries.add(decoded.get());
        }
        return entries;
    }

    private static void clearLegacyFields(ItemStack stack, String entries) {
        stack.removeTagKey(entries);
        stack.removeTagKey(INVERTED);
        stack.removeTagKey(NBT);
    }
}

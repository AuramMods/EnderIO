package com.enderio.core.common.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;

/** Native saved stack data, including Forge capabilities and integer counts used by stock filters. */
public final class StackDataCodecs {
    private static final String[] NBT_FIELDS = {"tag", "Tag", "ForgeCaps"};
    private static final Codec<CompoundTag> SAVED_STACK = new Codec<>() {
        @Override
        public <T> DataResult<Pair<CompoundTag, T>> decode(DynamicOps<T> ops, T input) {
            return CompoundTag.CODEC.decode(ops, input).flatMap(pair -> {
                var tag = pair.getFirst().copy();
                for (String key : NBT_FIELDS) {
                    if (tag.contains(key, Tag.TAG_STRING)) {
                        try { tag.put(key, TagParser.parseTag(tag.getString(key))); }
                        catch (CommandSyntaxException error) { return DataResult.error(() -> "Invalid stack NBT: " + error.getMessage()); }
                    } else if (tag.contains(key) && !tag.contains(key, Tag.TAG_COMPOUND)) {
                        return DataResult.error(() -> "Expected compound stack NBT in " + key);
                    }
                }
                return DataResult.success(Pair.of(tag, pair.getSecond()));
            });
        }

        @Override
        public <T> DataResult<T> encode(CompoundTag input, DynamicOps<T> ops, T prefix) {
            var tag = input.copy();
            // JSON numbers/arrays cannot retain NBT types. SNBT retains capability IntTags,
            // typed arrays and all item/fluid tag types; native NBT keeps native compounds.
            if (!(ops.empty() instanceof Tag)) {
                for (String key : NBT_FIELDS) {
                    if (tag.contains(key, Tag.TAG_COMPOUND)) tag.putString(key, tag.getCompound(key).toString());
                }
            }
            return CompoundTag.CODEC.encode(tag, ops, prefix);
        }
    };

    public static final Codec<ItemStack> OPTIONAL_ITEM = SAVED_STACK.flatXmap(StackDataCodecs::readItem, stack -> {
        if (stack.isEmpty()) return DataResult.success(new CompoundTag());
        CompoundTag tag = stack.save(new CompoundTag());
        // Vanilla's item save writes a byte. Filter stock targets can exceed a physical stack.
        tag.putInt("Count", stack.getCount());
        return DataResult.success(tag);
    });
    public static final Codec<FluidStack> OPTIONAL_FLUID = SAVED_STACK.flatXmap(StackDataCodecs::readFluid,
        stack -> DataResult.success(stack.isEmpty() ? new CompoundTag() : stack.writeToNBT(new CompoundTag())));

    private StackDataCodecs() {}

    private static DataResult<ItemStack> readItem(CompoundTag tag) {
        if (tag.isEmpty()) return DataResult.success(ItemStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return DataResult.error(() -> "Unknown item ID in saved stack");
        if (!tag.contains("Count", Tag.TAG_ANY_NUMERIC) || tag.getInt("Count") < 0) return DataResult.error(() -> "Invalid saved item count");
        int count = tag.getInt("Count");
        if (count == 0) return DataResult.success(ItemStack.EMPTY);
        CompoundTag nativeTag = tag.copy();
        nativeTag.putByte("Count", (byte) 1);
        ItemStack stack = ItemStack.of(nativeTag);
        if (stack.isEmpty()) return DataResult.error(() -> "Invalid nonempty saved item stack");
        stack.setCount(count);
        return DataResult.success(stack);
    }

    private static DataResult<FluidStack> readFluid(CompoundTag tag) {
        if (tag.isEmpty()) return DataResult.success(FluidStack.EMPTY);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("FluidName"));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return DataResult.error(() -> "Unknown fluid ID in saved stack");
        if (!tag.contains("Amount", Tag.TAG_ANY_NUMERIC) || tag.getInt("Amount") < 0) return DataResult.error(() -> "Invalid saved fluid amount");
        if (tag.getInt("Amount") == 0) return DataResult.success(FluidStack.EMPTY);
        FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.copy());
        return stack.isEmpty() ? DataResult.error(() -> "Invalid nonempty saved fluid stack") : DataResult.success(stack);
    }

    public static int hashItem(ItemStack stack) {
        return stack.isEmpty() ? 0 : Objects.hash(stack.getItem(), stack.getCount(), stack.getTag());
    }

    public static boolean fluidsEqual(FluidStack first, FluidStack second) {
        return first.isEmpty() && second.isEmpty() || first.isFluidStackIdentical(second);
    }

    public static int hashFluid(FluidStack stack) {
        return stack.isEmpty() ? 0 : Objects.hash(stack.getFluid(), stack.getAmount(), stack.getTag());
    }
}

package com.enderio.enderio.api.soul.binding.ingredients;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.foundation.util.EntityCaptureUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.crafting.AbstractIngredient;
import com.enderio.core.common.serialization.CodecIngredientSerializer;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.google.gson.JsonElement;

import java.util.Optional;
import java.util.stream.Stream;

public class AnySoulBindableIngredient extends AbstractIngredient {

    public static final MapCodec<AnySoulBindableIngredient> CODEC = RecordCodecBuilder.mapCodec(
        inst -> inst.group(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(i -> i.item)).apply(inst, AnySoulBindableIngredient::new));

    public static final CodecIngredientSerializer<AnySoulBindableIngredient> TYPE = new CodecIngredientSerializer<>(CODEC.codec(),
        PacketCodecs.registry(BuiltInRegistries.ITEM).map(AnySoulBindableIngredient::new, ingredient -> ingredient.item));

    private final Item item;
    private final ItemStack[] itemStacks;

    public static Ingredient of(ItemLike item) {
        return new AnySoulBindableIngredient(item.asItem());
    }

    public AnySoulBindableIngredient(Item item) {
        this.item = item;

        // Pre-compute
        var unboundStack = item.getDefaultInstance();
        var unboundName = unboundStack.getHoverName();

        if (!SoulBoundUtils.canBindSoul(unboundStack)) {
            var errorStack = new ItemStack(Blocks.BARRIER);
            errorStack.setHoverName(Component.literal("Item cannot be bound: " + unboundStack.getHoverName()));
            itemStacks = new ItemStack[] {unboundStack};
            return;
        }

        // Attempt to get the unbound item.
        if (!SoulBoundUtils.tryBindSoul(unboundStack, Soul.EMPTY)) {
            unboundStack = new ItemStack(Blocks.BARRIER);
            unboundStack.setHoverName(Component.literal("Unable to empty binding of " + unboundName));
        }

        // Pre-compute all stacks
        itemStacks = Stream.concat(
            Stream.of(unboundStack), EntityCaptureUtils.getCapturableEntityTypes().stream().map(entityType -> {
                var stack = item.getDefaultInstance();
                if (SoulBoundUtils.tryBindSoul(stack, Soul.of(entityType))) {
                    return Optional.of(stack);
                }

                return Optional.<ItemStack>empty();
            }).flatMap(Optional::stream)
        ).toArray(ItemStack[]::new);
    }

    @Override
    public boolean test(ItemStack itemStack) {
        return itemStack != null && itemStack.is(item);
    }

    @Override
    public ItemStack[] getItems() {
        return itemStacks;
    }

    @Override
    public boolean isSimple() {
        return true;
    }

    @Override
    public CodecIngredientSerializer<AnySoulBindableIngredient> getSerializer() {
        return TYPE;
    }

    @Override
    public boolean isEmpty() { return false; }

    @Override
    public JsonElement toJson() { return TYPE.toJson(this); }
}

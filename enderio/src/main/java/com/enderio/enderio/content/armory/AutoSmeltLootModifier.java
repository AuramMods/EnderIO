package com.enderio.enderio.content.armory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public final class AutoSmeltLootModifier extends LootModifier {
    public static final Codec<AutoSmeltLootModifier> CODEC =
        RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, AutoSmeltLootModifier::new));

    public AutoSmeltLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        var level = context.getLevel();
        var recipes = level.getRecipeManager();
        var loot = new ObjectArrayList<ItemStack>(generatedLoot.size());
        for (var stack : generatedLoot) {
            var input = new SimpleContainer(stack);
            var output = recipes.getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
            if (output.isEmpty()) {
                loot.add(stack);
                continue;
            }

            long remaining = (long) stack.getCount() * output.getCount();
            int stackLimit = output.getMaxStackSize();
            while (remaining > 0) {
                int count = (int) Math.min(stackLimit, remaining);
                loot.add(output.copyWithCount(count));
                remaining -= count;
            }
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}

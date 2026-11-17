package com.enderio.enderio.datagen.common.recipes;

import com.enderio.core.data.recipe.SubRecipeProvider;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import com.enderio.core.data.recipe.RecipeOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.Tags;
import com.enderio.core.common.recipes.SizedIngredient;

public class EnchanterRecipeProvider extends SubRecipeProvider {

    @Override
    public void buildRecipes(RecipeOutput recipeOutput, HolderLookup.Provider registries) {
        // We know that the registries are now available.
        HolderGetter<Enchantment> enchantmentRegistry = registries.lookupOrThrow(Registries.ENCHANTMENT);

        // vanilla
        build(enchantmentRegistry, Enchantments.ALL_DAMAGE_PROTECTION, SizedIngredient.of(EIOTags.Items.INGOTS_DARK_STEEL, 16), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.FIRE_PROTECTION, SizedIngredient.of(Items.MAGMA_CREAM, 16), 1,
                recipeOutput); // TODO
        build(enchantmentRegistry, Enchantments.FALL_PROTECTION, SizedIngredient.of(Tags.Items.FEATHERS, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.BLAST_PROTECTION, SizedIngredient.of(Items.GUNPOWDER, 16), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.PROJECTILE_PROTECTION, SizedIngredient.of(Tags.Items.LEATHER, 16), 1,
                recipeOutput);// change arrow->leather?
        build(enchantmentRegistry, Enchantments.RESPIRATION, SizedIngredient.of(Items.GLASS_BOTTLE, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.AQUA_AFFINITY, SizedIngredient.of(Items.LILY_PAD, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.DEPTH_STRIDER, SizedIngredient.of(Items.PRISMARINE_SHARD, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.FROST_WALKER, SizedIngredient.of(Items.ICE, 16), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.THORNS, SizedIngredient.of(Items.ROSE_BUSH, 4), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.SHARPNESS, SizedIngredient.of(Tags.Items.GEMS_QUARTZ, 12), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.SMITE, SizedIngredient.of(Items.ROTTEN_FLESH, 12), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.BANE_OF_ARTHROPODS, SizedIngredient.of(Items.SPIDER_EYE, 12), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.KNOCKBACK, SizedIngredient.of(Items.PISTON, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.FIRE_ASPECT, SizedIngredient.of(Items.BLAZE_ROD, 8), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.MOB_LOOTING, SizedIngredient.of(Items.SKELETON_SKULL, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.BLOCK_EFFICIENCY, SizedIngredient.of(Tags.Items.DUSTS_REDSTONE, 12), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.SILK_TOUCH, SizedIngredient.of(Tags.Items.SLIMEBALLS, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.UNBREAKING, SizedIngredient.of(Tags.Items.OBSIDIAN, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.MENDING, SizedIngredient.of(EIOItems.VOID_VIAL.get(), 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.BLOCK_FORTUNE, SizedIngredient.of(Tags.Items.GEMS_EMERALD, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.POWER_ARROWS, SizedIngredient.of(Items.FLINT, 12), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.PUNCH_ARROWS, SizedIngredient.of(Tags.Items.STRING, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.FLAMING_ARROWS, SizedIngredient.of(Tags.Items.NETHERRACK, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.INFINITY_ARROWS, SizedIngredient.of(EIOTags.Items.DUSTS_GRAINS_OF_INFINITY, 1),
                1, recipeOutput);
        build(enchantmentRegistry, Enchantments.FISHING_LUCK, SizedIngredient.of(Tags.Items.GEMS_LAPIS, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.FISHING_SPEED, SizedIngredient.of(ItemTags.FISHES, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.SWEEPING_EDGE, SizedIngredient.of(Tags.Items.INGOTS_IRON, 8), 1,
                recipeOutput);
        // new
        build(enchantmentRegistry, Enchantments.CHANNELING, SizedIngredient.of(Items.LIGHTNING_ROD, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.IMPALING, SizedIngredient.of(Tags.Items.STORAGE_BLOCKS_IRON, 1), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.LOYALTY, SizedIngredient.of(Items.LEAD, 1), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.MULTISHOT, SizedIngredient.of(ItemTags.ARROWS, 16), 1, recipeOutput);// TODO
        build(enchantmentRegistry, Enchantments.PIERCING, SizedIngredient.of(Tags.Items.GEMS_PRISMARINE, 8), 1,
                recipeOutput);
        build(enchantmentRegistry, Enchantments.QUICK_CHARGE, SizedIngredient.of(Items.SUGAR, 16), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.RIPTIDE, SizedIngredient.of(Items.FIREWORK_ROCKET, 8), 1, recipeOutput);
        build(enchantmentRegistry, Enchantments.SOUL_SPEED, SizedIngredient.of(Items.SOUL_SOIL, 16), 1, recipeOutput);
    }

    protected void build(HolderGetter<Enchantment> enchantmentRegistry, Enchantment enchantment,
            SizedIngredient input, int levelModifier, RecipeOutput recipeOutput) {
        build(enchantmentRegistry.getOrThrow(net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getResourceKey(enchantment).orElseThrow()), input, levelModifier, recipeOutput);
    }

    protected void build(Holder<Enchantment> enchantment, SizedIngredient input, int levelModifier,
            RecipeOutput recipeOutput) {
        recipeOutput.accept(EnderIO.rl("enchanting/" + enchantment.unwrapKey().orElseThrow().location().getPath()),
                new EnchanterRecipe(enchantment, levelModifier, input), null);
    }

}

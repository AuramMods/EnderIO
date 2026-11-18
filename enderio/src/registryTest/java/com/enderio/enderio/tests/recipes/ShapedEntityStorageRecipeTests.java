package com.enderio.enderio.tests.recipes;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.soul.ShapedEntityStorageRecipe;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import com.enderio.enderio.registrytests.ForgeServerExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(ForgeServerExtension.class)
public class ShapedEntityStorageRecipeTests {
    @Test
    public void testSoulTransferredToResult(MinecraftServer server) {
        var shapedRecipe = new ShapedRecipe(ResourceLocation.fromNamespaceAndPath("enderio_registry_tests", "soul_recipe"), "no", CraftingBookCategory.MISC,
            2, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(EIOItems.SOUL_VIAL.get()), Ingredient.of(Items.SAND)),
            EIOItems.BROKEN_SPAWNER.toStack());

        var input = craftingInput(List.of(
            SoulVialItem.forSoul(Soul.of(EntityType.ALLAY)), Items.SAND.getDefaultInstance(), ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        var wrappedRecipe = new ShapedEntityStorageRecipe(shapedRecipe);
        var result = wrappedRecipe.assemble(input, server.registryAccess());

        Assertions.assertEquals(EIOItems.BROKEN_SPAWNER.get(), result.getItem());
        Assertions.assertEquals(Soul.of(EntityType.ALLAY), SoulBoundUtils.getBoundSoul(result));
    }

    @Test
    public void testSoulNotTransferredToReadOnlyBindableResult(MinecraftServer server) {
        var shapedRecipe = new ShapedRecipe(ResourceLocation.fromNamespaceAndPath("enderio_registry_tests", "soul_recipe"), "no", CraftingBookCategory.MISC,
            2, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(EIOItems.SOUL_VIAL.get()), Ingredient.of(Items.SAND)),
            EIOItems.SOUL_VIAL.toStack());

        var input = craftingInput(List.of(
            SoulVialItem.forSoul(Soul.of(EntityType.ALLAY)), Items.SAND.getDefaultInstance(), ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        var wrappedRecipe = new ShapedEntityStorageRecipe(shapedRecipe);
        var result = wrappedRecipe.assemble(input, server.registryAccess());

        Assertions.assertEquals(EIOItems.SOUL_VIAL.get(), result.getItem());
        Assertions.assertEquals(Soul.EMPTY, SoulBoundUtils.getBoundSoul(result));
    }

    @Test
    public void testSoulNotTransferredToNonBindableResult(MinecraftServer server) {
        var shapedRecipe = new ShapedRecipe(ResourceLocation.fromNamespaceAndPath("enderio_registry_tests", "soul_recipe"), "no", CraftingBookCategory.MISC,
            2, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(EIOItems.SOUL_VIAL.get()), Ingredient.of(Items.SAND)),
            Items.OAK_BUTTON.getDefaultInstance());

        var input = craftingInput(List.of(
            SoulVialItem.forSoul(Soul.of(EntityType.ALLAY)), Items.SAND.getDefaultInstance(), ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));

        var wrappedRecipe = new ShapedEntityStorageRecipe(shapedRecipe);
        var result = wrappedRecipe.assemble(input, server.registryAccess());

        Assertions.assertEquals(Items.OAK_BUTTON, result.getItem());
        Assertions.assertEquals(Soul.EMPTY, SoulBoundUtils.getBoundSoul(result));
    }

    private static TransientCraftingContainer craftingInput(List<ItemStack> items) {
        var menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var input = new TransientCraftingContainer(menu, 3, 3);
        for (int slot = 0; slot < items.size(); slot++) input.setItem(slot, items.get(slot));
        return input;
    }
}

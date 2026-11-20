package com.enderio.enderio.compat.jei_machines_to_merge.transfer;

import com.enderio.enderio.compat.jei_machines_to_merge.category.VATCategory;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundTransferItemsPacket;
import com.enderio.enderio.init.EIOMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VATTransferHelper implements IRecipeTransferHandler<VatMenu, FermentingRecipe> {

    private final IRecipeTransferHandlerHelper handlerHelper;

    public VATTransferHelper(IRecipeTransferHandlerHelper handlerHelper) {
        this.handlerHelper = handlerHelper;
    }

    @Override
    public Class<? extends VatMenu> getContainerClass() {
        return VatMenu.class;
    }

    @Override
    public Optional<MenuType<VatMenu>> getMenuType() {
        return Optional.of(EIOMenus.VAT.get());
    }

    @Override
    public RecipeType<FermentingRecipe> getRecipeType() {
        return VATCategory.TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(VatMenu container, FermentingRecipe recipe, IRecipeSlotsView recipeSlots, Player player,
        boolean maxTransfer, boolean doTransfer) {
        var tank = container.getInputTank();
        boolean missingFluid = tank == null || !recipe.input().test(tank.contents());
        var first = Ingredient.of(recipe.firstReagent());
        var second = Ingredient.of(recipe.secondReagent());
        var left = container.getSlot(VatMenu.INPUTS_INDEX).getItem();
        var right = container.getSlot(VatMenu.INPUTS_INDEX + 1).getItem();
        boolean reversed = !(fits(left, first) && fits(right, second)) && fits(left, second) && fits(right, first);
        var firstInput = new Reagent(first, reversed ? right : left, player.getInventory().items.stream().anyMatch(first::test));
        var secondInput = new Reagent(second, reversed ? left : right, player.getInventory().items.stream().anyMatch(second::test));
        boolean missingFirst = firstInput.isMissing();
        boolean missingSecond = secondInput.isMissing();

        if (missingFirst || missingSecond || missingFluid) {
            var inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> missing = new ArrayList<>(3);
            if (missingFirst) {
                missing.add(inputs.get(0));
            }
            if (missingSecond) {
                missing.add(inputs.get(1));
            }
            if (missingFluid) {
                missing.add(inputs.get(2));
            }
            return handlerHelper.createUserErrorForMissingSlots(
                Component.translatable("jei.tooltip.error.recipe.transfer.missing"), missing);
        }

        if (doTransfer) {
            var firstTransfer = firstInput.toTransfer(maxTransfer);
            var secondTransfer = secondInput.toTransfer(maxTransfer);
            if (!firstTransfer.isEmpty() || !secondTransfer.isEmpty()) {
                var inputs = reversed ? List.of(secondTransfer, firstTransfer) : List.of(firstTransfer, secondTransfer);
                EIONetwork.sendToServer(new ServerboundTransferItemsPacket(
                    container.containerId, inputs, VatMenu.INPUTS_INDEX, VatMenu.LAST_INDEX + 1, maxTransfer));
            }
        }
        return null;
    }

    private static boolean fits(ItemStack current, Ingredient ingredient) {
        return current.isEmpty() || ingredient.test(current);
    }

    private record Reagent(Ingredient ingredient, ItemStack current, boolean available) {
        boolean isMissing() {
            return current.isEmpty() ? !available : !ingredient.test(current);
        }

        Ingredient toTransfer(boolean maximum) {
            return available && (current.isEmpty() || maximum) ? ingredient : Ingredient.EMPTY;
        }
    }
}

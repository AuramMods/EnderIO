package com.enderio.enderio.compat.jei_machines_to_merge.transfer;

import com.enderio.enderio.compat.jei_machines_to_merge.category.VATCategory;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.content.machines.vat.VatRecipeTransfer;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundVatRecipeTransferPacket;
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
        var missingReagents = VatRecipeTransfer.missingReagents(container, player, recipe);

        if (missingReagents.first() || missingReagents.second() || missingFluid) {
            var inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> missing = new ArrayList<>(3);
            if (missingReagents.first()) {
                missing.add(inputs.get(0));
            }
            if (missingReagents.second()) {
                missing.add(inputs.get(1));
            }
            if (missingFluid) {
                missing.add(inputs.get(2));
            }
            return handlerHelper.createUserErrorForMissingSlots(
                Component.translatable("jei.tooltip.error.recipe.transfer.missing"), missing);
        }

        if (doTransfer) {
            EIONetwork.sendToServer(new ServerboundVatRecipeTransferPacket(container.containerId, recipe.getId(), maxTransfer));
        }
        return null;
    }
}

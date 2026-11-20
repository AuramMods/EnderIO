package com.enderio.enderio.compat.jei_machines_to_merge.transfer;

import com.enderio.enderio.compat.jei_machines_to_merge.category.TankCategory;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.content.storage.fluid_tank.TankRecipe;
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
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FluidTankTransferHelper implements IRecipeTransferHandler<FluidTankMenu, TankRecipe> {

    private final IRecipeTransferHandlerHelper handlerHelper;

    public FluidTankTransferHelper(IRecipeTransferHandlerHelper handlerHelper) {
        this.handlerHelper = handlerHelper;
    }

    @Override
    public Class<? extends FluidTankMenu> getContainerClass() {
        return FluidTankMenu.class;
    }

    @Override
    public Optional<MenuType<FluidTankMenu>> getMenuType() {
        return Optional.of(EIOMenus.FLUID_TANK.get());
    }

    @Override
    public RecipeType<TankRecipe> getRecipeType() {
        return TankCategory.TYPE;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(FluidTankMenu container, TankRecipe recipe, IRecipeSlotsView recipeSlots, Player player,
        boolean maxTransfer, boolean doTransfer) {
        boolean fillsItem = recipe.mode() == TankRecipe.Mode.FILL;
        int inputIndex = fillsItem ? FluidTankMenu.FILL_INPUT_INDEX : FluidTankMenu.EMPTY_INPUT_INDEX;
        var target = container.getSlot(inputIndex);
        var fluid = container.getFluidTank();
        var ingredient = recipe.input();
        boolean needsItem = !ingredient.test(target.getItem());
        boolean hasInventoryItem = player.getInventory().items.stream().anyMatch(ingredient::test);
        boolean missingItem = needsItem && (target.hasItem() || !hasInventoryItem);
        boolean missingFluid = fillsItem && (fluid == null || !recipe.fluid().test(fluid.contents()));

        if (missingItem || missingFluid) {
            var inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> missing = new ArrayList<>(2);
            if (missingItem) {
                missing.add(inputs.get(0));
            }
            if (missingFluid) {
                missing.add(inputs.get(1));
            }
            return handlerHelper.createUserErrorForMissingSlots(
                Component.translatable("jei.tooltip.error.recipe.transfer.missing"), missing);
        }

        if (doTransfer && hasInventoryItem && (needsItem || maxTransfer)) {
            List<Ingredient> toSend = NonNullList.withSize(FluidTankMenu.MACHINE_SLOT_COUNT, Ingredient.EMPTY);
            toSend.set(inputIndex, ingredient);
            EIONetwork.sendToServer(new ServerboundTransferItemsPacket(
                container.containerId, toSend, 0, FluidTankMenu.MACHINE_SLOT_COUNT, maxTransfer));
        }
        return null;
    }
}

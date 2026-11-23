package com.enderio.enderio.compat.jei_machines_to_merge.transfer;

import com.enderio.enderio.compat.jei_machines_to_merge.category.AlloySmeltingCategory;
import com.enderio.enderio.content.machines.alloy.AlloyRecipeTransfer;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMenu;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundAlloyRecipeTransferPacket;
import com.enderio.enderio.init.EIOMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class AlloySmelterTransferHelper implements IRecipeTransferHandler<AlloySmelterMenu, AlloySmeltingRecipe> {
    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeTransferHandler<AlloySmelterMenu, AlloySmeltingRecipe> basic;

    public AlloySmelterTransferHelper(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
        basic = helper.createUnregisteredRecipeTransferHandler(helper.createBasicRecipeTransferInfo(
            AlloySmelterMenu.class, EIOMenus.ALLOY_SMELTER.get(), AlloySmeltingCategory.TYPE,
            AlloySmelterMenu.INPUTS_INDEX, AlloySmelterMenu.INPUT_COUNT, AlloySmelterMenu.LAST_INDEX + 1, 36));
    }

    @Override
    public Class<? extends AlloySmelterMenu> getContainerClass() { return AlloySmelterMenu.class; }

    @Override
    public Optional<MenuType<AlloySmelterMenu>> getMenuType() { return Optional.of(EIOMenus.ALLOY_SMELTER.get()); }

    @Override
    public RecipeType<AlloySmeltingRecipe> getRecipeType() { return AlloySmeltingCategory.TYPE; }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(AlloySmelterMenu menu, AlloySmeltingRecipe recipe,
            IRecipeSlotsView slots, Player player, boolean maximum, boolean transfer) {
        if (recipe.inputs().stream().noneMatch(input -> input.count() > 1)) {
            return basic.transferRecipe(menu, recipe, slots, player, maximum, transfer);
        }
        if (!AlloyRecipeTransfer.canLoad(menu, player, recipe)) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.missing"));
        }
        if (transfer) {
            EIONetwork.sendToServer(new ServerboundAlloyRecipeTransferPacket(menu.containerId, recipe.getId(), maximum));
        }
        return null;
    }
}

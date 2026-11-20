package com.enderio.enderio.compat.jei_machines_to_merge.category;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.compat.jei.JEILang;
import com.enderio.enderio.compat.jei.JEIUtils;
import com.enderio.enderio.compat.jei_machines_to_merge.util.MachineRecipeCategory;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.machines.vat.VatBlockEntity;
import com.enderio.enderio.foundation.datamap.VatReagent;
import com.enderio.enderio.foundation.util.SizedFluidIngredientHelper;
import com.enderio.enderio.init.EIOBlocks;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public class VATCategory extends MachineRecipeCategory<FermentingRecipe> {
    public static final RecipeType<FermentingRecipe> TYPE = JEIUtils.createRecipeType(EnderIO.MOD_ID,
            "vat_fermenting", FermentingRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public VATCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(VatScreen.VAT_BG, 28, 10, 120, 53);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(EIOBlocks.VAT.get()));
    }

    @Override
    public RecipeType<FermentingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return JEILang.VAT_TITLE;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FermentingRecipe recipe, IFocusGroup focuses) {

        builder.addSlot(RecipeIngredientRole.INPUT, 28, 2)
                .addIngredients(Ingredient.of(recipe.firstReagent()))
                .addTooltipCallback((recipeSlotView, tooltip) -> tooltip.add(Component.literal("x"
                        + getModifier(recipeSlotView.getDisplayedItemStack().get(), recipe.firstReagent()))));

        builder.addSlot(RecipeIngredientRole.INPUT, 77, 2)
                .addIngredients(Ingredient.of(recipe.secondReagent()))
                .addTooltipCallback((recipeSlotView, tooltip) -> tooltip.add(Component.literal("x"
                        + getModifier(recipeSlotView.getDisplayedItemStack().get(), recipe.secondReagent()))));

        var fluidChoices = SizedFluidIngredientHelper.getFluidStacksInPreferredOrder(recipe.input()).stream()
                .filter(stack -> !(stack.getFluid() instanceof FlowingFluid flowing) || stack.getFluid() == flowing.getSource()
                        || !recipe.input().test(new FluidStack(flowing.getSource(), stack.getAmount())))
                .toList();
        builder.addSlot(RecipeIngredientRole.INPUT, 2, 2)
                .addIngredients(ForgeTypes.FLUID_STACK, fluidChoices)
                .setFluidRenderer(VatBlockEntity.TANK_CAPACITY, false, 15, 47);

        builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 2)
                .addIngredient(ForgeTypes.FLUID_STACK, recipe.output().copy())
                .setFluidRenderer(VatBlockEntity.TANK_CAPACITY, false, 15, 47);
    }

    public static double getModifier(ItemStack stack, TagKey<Item> reagent) {
        var map = com.enderio.core.common.datamap.ClientDataMaps.get(VatReagent.DATA_MAP, stack.getItem());
        if (map != null) {
            return map.getOrDefault(reagent, 1D);
        }
        return 1;
    }

    @Override
    public void draw(FermentingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        // left modifier
        ItemStack item = recipeSlotsView.getSlotViews().get(0).getDisplayedItemStack().get();
        double modifier = FermentingRecipe.getModifier(Minecraft.getInstance().level, item, recipe.firstReagent());
        String text = "x" + modifier;
        Font font = Minecraft.getInstance().font;
        int x = 28 + 8 - font.width(text) / 2;
        guiGraphics.drawString(font, text, x, 22, 4210752, false);

        // right modifier
        item = recipeSlotsView.getSlotViews().get(1).getDisplayedItemStack().get();
        modifier = FermentingRecipe.getModifier(Minecraft.getInstance().level, item, recipe.secondReagent());
        text = "x" + modifier;
        x = 77 + 8 - font.width(text) / 2;
        guiGraphics.drawString(font, text, x, 22, 4210752, false);
    }
}

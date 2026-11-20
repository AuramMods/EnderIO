package com.enderio.enderio.compat.jei_machines_to_merge.category;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.compat.jei.JEILang;
import com.enderio.enderio.compat.jei.JEIUtils;
import com.enderio.enderio.compat.jei_machines_to_merge.util.MachineRecipeCategory;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static mezz.jei.api.recipe.RecipeIngredientRole.INPUT;
import static mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;

public class SoulBindingCategory extends MachineRecipeCategory<SoulBindingRecipe> {
    public static final RecipeType<SoulBindingRecipe> TYPE = JEIUtils.createRecipeType(EnderIO.MOD_ID,
            "soul_binding", SoulBindingRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public SoulBindingCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(SoulBinderScreen.BG_TEXTURE, 35, 30, 118, 44);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(EIOBlocks.SOUL_BINDER.get()));
    }

    @Override
    public RecipeType<SoulBindingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return JEILang.SOUL_BINDING_TITLE;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SoulBindingRecipe recipe, IFocusGroup focuses) {
        var inputs = focuses.getItemStackFocuses(INPUT)
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> stack.is(EIOItems.SOUL_VIAL.get()));
        var outputs = focuses.getItemStackFocuses(OUTPUT)
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(stack -> stack.is(recipe.output().getItem()));
        var focusedSoul = Stream.concat(inputs, outputs)
                .map(SoulBoundUtils::getBoundSoul)
                .filter(recipe::acceptsSoul)
                .findFirst();
        List<ItemStack> vials = focusedSoul
                .map(soul -> List.of(SoulVialItem.forSoul(soul.copy())))
                .orElseGet(() -> getRecipeVials(recipe));

        builder.addSlot(INPUT, 3, 4).addItemStacks(vials);

        var material = recipe.copyInputComponents() ? focuses.getItemStackFocuses(INPUT)
                .map(focus -> focus.getTypedValue().getIngredient())
                .filter(recipe.getInput()::test)
                .findFirst().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        var materialSlot = builder.addSlot(INPUT, 24, 4);
        if (material.isEmpty()) {
            materialSlot.addIngredients(recipe.getInput());
        } else {
            materialSlot.addItemStack(material.copyWithCount(1));
        }

        var resultStack = recipe.createOutput(material);
        var results = new ArrayList<ItemStack>();

        // If the output can take an entity type, then we add it
        if (SoulBoundUtils.canBindSoul(resultStack)) {
            for (ItemStack vial : vials) {
                var soul = SoulBoundUtils.getBoundSoul(vial);
                if (!soul.isEmpty()) {
                    var result = resultStack.copy();
                    if (SoulBoundUtils.tryBindSoul(result, soul.copy())) {
                        results.add(result);
                    }
                }
            }
        }

        // Fallback :(
        if (results.isEmpty()) {
            results.add(resultStack);
        }

        builder.addSlot(OUTPUT, 77, 4).addItemStack(new ItemStack(EIOItems.SOUL_VIAL.get()));

        builder.addSlot(OUTPUT, 99, 4).addItemStacks(results);
    }

    private static List<ItemStack> getRecipeVials(SoulBindingRecipe recipe) {
        if (recipe.entityType().isPresent()) {
            return List.of(SoulVialItem.forSoul(Soul.of(recipe.entityType().get())));
        }
        if (recipe.mobCategory().isPresent() || recipe.soulData().isPresent()) {
            return BuiltInRegistries.ENTITY_TYPE.stream()
                    .map(Soul::of)
                    .filter(recipe::acceptsSoul)
                    .map(SoulVialItem::forSoul)
                    .toList();
        }
        return SoulVialItem.getAllFilled();
    }

    @Override
    public void draw(SoulBindingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        int cost = recipe.experience();
        String costText = cost < 0 ? "err" : Integer.toString(cost);
        String text = I18n.get("container.repair.cost", costText);

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // Show red if the player doesn't have enough levels
        int mainColor = playerHasEnoughLevels(player, cost) ? 0xFF80FF20 : 0xFFFF6060;
        guiGraphics.drawString(minecraft.font, text, 5, 24, mainColor);

        guiGraphics.drawString(Minecraft.getInstance().font, getBasicEnergyString(recipe), 5, 34, 0xff808080, false);
    }

    @Override
    public List<Component> getTooltipStrings(SoulBindingRecipe recipe, IRecipeSlotsView recipeSlotsView,
            double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mouseX > 5 && mouseY > 34 && mouseX < 5 + mc.font.width(getBasicEnergyString(recipe))
                && mouseY < 34 + mc.font.lineHeight) {
            return List.of(EIOCommonLang.TOOLTIP_ENERGY_EQUIVALENCE);
        }

        return List.of();
    }
}

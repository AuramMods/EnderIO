package com.enderio.port.clientqa;

import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

final class JeiLayoutAccess {
    private static final Field LAYOUTS = field(RecipesGui.class, "layouts");
    private static final Field RECIPES = field(RecipeGuiLayouts.class, "recipeLayoutsWithButtons");

    private JeiLayoutAccess() {
    }

    static Optional<?> underMouse(IRecipesGui gui, int x, int y) {
        try {
            var layouts = (List<?>) RECIPES.get(LAYOUTS.get(gui));
            return layouts.stream()
                .map(layout -> (RecipeLayoutWithButtons<?>) layout)
                .filter(layout -> layout.recipeLayout().isMouseOver(x, y))
                .findFirst();
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read the active JEI recipe layouts", failure);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("Cannot find JEI field " + name, failure);
        }
    }
}

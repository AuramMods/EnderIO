package com.enderio.core.common.recipes;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks whether occupied machine slots can participate in at least one recipe. */
public final class RecipeIngredientIndex {
    private final List<List<Ingredient>> recipes = new ArrayList<>();
    private final Map<Item, Set<Integer>> simpleItems = new HashMap<>();
    private final Set<Integer> customRecipes = new HashSet<>();

    public RecipeIngredientIndex(List<? extends List<Ingredient>> ingredients) {
        for (var recipe : ingredients) {
            int index = recipes.size();
            recipes.add(recipe.stream().filter(ingredient -> !ingredient.isEmpty()).toList());
            for (var ingredient : recipe) {
                if (!ingredient.isSimple()) {
                    // Custom ingredient display stacks need not exhaust all matching items.
                    customRecipes.add(index);
                } else {
                    for (var item : ingredient.getItems()) {
                        simpleItems.computeIfAbsent(item.getItem(), key -> new HashSet<>()).add(index);
                    }
                }
            }
        }
    }

    public boolean hasRecipe(List<ItemStack> stacks) {
        var inputs = stacks.stream().filter(stack -> !stack.isEmpty()).toList();
        if (inputs.isEmpty()) {
            return true;
        }
        Set<Integer> candidates = null;
        for (var input : inputs) {
            var matches = new HashSet<>(simpleItems.getOrDefault(input.getItem(), Set.of()));
            matches.addAll(customRecipes);
            if (candidates == null) {
                candidates = matches;
            } else {
                candidates.retainAll(matches);
            }
            if (candidates.isEmpty()) {
                return false;
            }
        }
        for (int candidate : candidates) {
            if (matchesDistinctIngredients(inputs, recipes.get(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDistinctIngredients(List<ItemStack> inputs, List<Ingredient> ingredients) {
        if (inputs.size() > ingredients.size()) {
            return false;
        }
        int[] assignedInputs = new int[ingredients.size()];
        Arrays.fill(assignedInputs, -1);
        for (int input = 0; input < inputs.size(); input++) {
            if (!assign(input, inputs, ingredients, assignedInputs, new boolean[ingredients.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean assign(int input, List<ItemStack> inputs, List<Ingredient> ingredients,
            int[] assignedInputs, boolean[] visited) {
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            if (visited[ingredient] || !ingredients.get(ingredient).test(inputs.get(input))) {
                continue;
            }
            visited[ingredient] = true;
            if (assignedInputs[ingredient] == -1 || assign(assignedInputs[ingredient], inputs, ingredients, assignedInputs, visited)) {
                assignedInputs[ingredient] = input;
                return true;
            }
        }
        return false;
    }
}

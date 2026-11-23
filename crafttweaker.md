# Ender IO CraftTweaker integration

Target versions are Minecraft 1.20.1, Forge 47.4.10, Java 17, and CraftTweaker 14.0.59. The Auram integration target also includes GameStages 15.0.2, Bookshelf 20.2.15, RecipeStages 8.0.0.2, ItemStages 8.0.3, and JEI 15.20.0.129.

Install CraftTweaker and put ZenScript files under `minecraft/scripts/` in your instance. Use the same scripts on the dedicated server that owns the world. A normal world can run Ender IO without CraftTweaker.

## Alloy smelting

`<recipetype:enderio:alloy_smelting>` is the powered Alloy Smelter. `<recipetype:enderio:primitive_alloy_smelting>` is the fuel-fired Primitive Alloy Smelter. Their recipe lists are independent.

```zenscript
val alloy = <recipetype:enderio:alloy_smelting>;
alloy.addRecipe("amethyst_alloy", <item:minecraft:amethyst_shard> * 2, 1500, 0.25f,
    <item:minecraft:lapis_lazuli> * 2, <item:minecraft:quartz>);
alloy.replaceRecipe("crafttweaker:amethyst_alloy", <item:minecraft:amethyst_shard> * 3, 1800, 0.5f,
    <item:minecraft:lapis_lazuli> * 2, <item:minecraft:quartz>);

val primitive = <recipetype:enderio:primitive_alloy_smelting>;
primitive.addRecipe("glowing_alloy", <item:minecraft:glow_ink_sac> * 2, 80, 0.0f,
    <item:minecraft:bamboo> * 2, <item:minecraft:slime_ball>);

// Optional ways to remove these recipes:
// alloy.removeByName("crafttweaker:amethyst_alloy");
// primitive.remove(<item:minecraft:glow_ink_sac>);
```

| Method | Parameters and behavior |
| --- | --- |
| `addRecipe(name, output, energy, experience, inputs...)` | A CraftTweaker recipe name, one output stack, positive energy, finite nonnegative XP, and one to three sized ingredients in any order. Multiply an ingredient by its item count. The powered machine spends FE. The primitive machine spends fuel burn ticks according to the recipe's work and the machine's work rate. |
| `replaceRecipe(id, output, energy, experience, inputs...)` | The same recipe parameters under an existing, fully qualified ID. Both the namespace and the path are retained. |
| `getRecipeByName(id)`, `allRecipes`, `getRecipesByOutput(ingredient)` | Look up one exact ID, read the current list, or list recipes whose item output matches the ingredient. |
| `removeByName(ids...)`, `remove(ingredient)` | Remove exact IDs or recipes whose item output matches the ingredient. |
| `addJsonRecipe(name, data)`, `addRecipeJson(name, json)` | Add a recipe using a CraftTweaker data map or a JSON string. Either uses the existing datapack format and provides the manager's serializer ID by default. |
| `replaceRecipeJson(id, json)` | Decode a JSON definition with the manager's serializer and replace an existing, fully qualified ID. |

CraftTweaker puts new recipe names in the `crafttweaker` namespace. Scripts run in order on each load. To replace a recipe created earlier in the same script, use its `crafttweaker:` ID.

## Implementation coverage

| Processing path | Current cycle |
| --- | --- |
| Powered and primitive alloy smelting | Packaged Forge passed ZenScript lookup, add, replacement, removal, and exact machine input, output, and FE or fuel checks across server restarts. The packaged mod also booted without CraftTweaker. Client recipe-dump and JEI checks are pending. |
| Inherited furnace recipes | Inventory complete. Live CraftTweaker changes and inheritance order need verification. |
| SAG milling and legacy grinding ball recipes | Inventory complete. Modern modifiers take precedence over the legacy grinding ball recipe. |
| Slicing, Soul Binder, VAT fermentation, and tank processing | Inventory complete. The VAT uses positioned reagent groups and the tank supports both filling and emptying. |
| Painting, Enchanter, weather, and fire | Inventory complete. Painting uses the supplied paint block. Enchanter uses the selected enchantment and amount. Weather and fire have world effects. |
| Crafter and shaped entity storage | Both use the vanilla crafting recipe manager. The shaped entity storage serializer adds soul-storage output behavior. |
| Ingredients | Forge defines `empty_soul_storage`, `filled_soul_storage`, `any_soul_storage`, and `conduit` serializers. Typed script helpers and editable selector groups are pending. |
| Processing data | The three native data maps cover grinding balls, VAT reagents, and range extenders. Separate loaders supply farming, solar, powered spawner, and Soul Engine rules. Script editors and reload tests are pending. |
| Addon inputs | Modded conduits transport optional resources such as Mekanism chemicals. Audit which resources are consumed by processing before exposing selectors. |
| GameStages | Rule API, recipe checks, action checks, and tests with RecipeStages and ItemStages are pending. |

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

### Inherited furnace recipes

The powered Alloy Smelter in Furnace or All mode inherits the current `minecraft:smelting` recipes. It generates IDs in the form `enderio:smelting/<source namespace>/<source path>` and uses the smelter's `vanillaItemEnergy` setting, which defaults to 1,500 FE per input. Furnace cooking time stays a furnace setting.

```zenscript
val furnace = <recipetype:minecraft:smelting>;
val alloy = <recipetype:enderio:alloy_smelting>;
furnace.addRecipe("wheat_to_amethyst", <item:minecraft:amethyst_shard>, <item:minecraft:wheat>, 0.25f, 30);
furnace.addRecipe("fern_to_diamond", <item:minecraft:diamond>, <item:minecraft:fern>, 1.0f, 30);
alloy.excludeFurnaceRecipe("crafttweaker:fern_to_diamond");
alloy.removeByName("enderio:smelting/minecraft/gold_ingot_from_smelting_raw_gold");
```

`excludeFurnaceRecipe(furnaceId)` uses the source furnace ID and skips the generated alloy recipe, while the furnace recipe stays available. `removeByName(id)` can remove an inherited alloy recipe that has already been generated. Ender IO prepares inherited recipes from datapacks before scripts run, then reconciles them with the script-edited furnace list. A datapack recipe or explicit script replacement at the derived ID takes precedence. Removing the script and running `/reload` restores the datapack and stock definitions.

## SAG Mill

```zenscript
import mods.enderio.SagOutput;

val sag = <recipetype:enderio:sag_milling>;
sag.addRecipe("qa_sag", <item:minecraft:paper>, 800, "none",
    SagOutput.item(<item:minecraft:amethyst_shard> * 2, 1.0f, false),
    SagOutput.tag(<tag:items:forge:ingots/copper>, 1, 1.0f, false),
    SagOutput.item(<item:minecraft:glow_ink_sac>, 0.0001f, true));
```

- `addRecipe(name, input, energy, bonus, outputs...)` consumes one item matching `input`. Pass the base FE cost and one or more `SagOutput` values.
- `replaceRecipe(id, input, energy, bonus, outputs...)` takes the same arguments under an existing recipe ID.
- `bonus` is `none` to skip grinding balls, `chance_only` to apply their chance modifier, or `multiply_output` to apply both yield and chance modifiers. The machine applies its native grinding-ball FE rules.
- `SagOutput.item(stack, chance, optional)` specifies a stack. `SagOutput.tag(tag, count, chance, optional)` resolves the item using Ender IO's material preference rules. Use a chance from `0.0f` to `1.0f` and supply the native `optional` flag for serialization.
- `remove(ingredient)` and `getRecipesByOutput(ingredient)` match all possible item outputs with nonzero chances. For tag outputs, they use the tag membership from the current CraftTweaker reload. The machine chooses the actual result using Ender IO's material preference rules.

## Slice'N'Splice

```zenscript
val slice = <recipetype:enderio:slicing>;
slice.addRecipe("qa_slice", <item:minecraft:echo_shard> * 2, 600,
    <item:minecraft:dandelion>, <item:minecraft:air>, <item:minecraft:poppy>,
    <item:minecraft:air>, <item:minecraft:air>, <item:minecraft:cornflower>);
```

`addRecipe(name, output, energy, inputs...)` accepts exactly six slots, left to right across the top row, then left to right across the bottom row. Use `<item:minecraft:air>` for a slot that must be empty. Each occupied slot consumes one item. `energy` is in FE. The machine handles axe and shears wear. `replaceRecipe(id, output, energy, inputs...)` keeps the existing ID.

## Implementation coverage

| Processing path | Current cycle |
| --- | --- |
| Powered and primitive alloy smelting | Packaged Forge passed ZenScript lookup, add, replacement, removal, and exact machine input, output, and FE or fuel checks across server restarts. The packaged mod also booted without CraftTweaker. Client recipe-dump and JEI checks are pending. |
| Inherited furnace recipes | Packaged Forge verified a scripted furnace addition and replacement, a source removal, two alloy-only exclusions, exact FE, and restoration after live `/reload`. |
| SAG milling and slicing | Packaged Forge verified scripted additions, replacements, removal by tagged SAG output, exact FE and item counts, positional slicer inputs, and tool wear. |
| Legacy grinding ball recipes | Inventory complete. Modern modifiers take precedence over the legacy grinding ball recipe. |
| Soul Binder, VAT fermentation, and tank processing | Inventory complete. The VAT accepts its two reagent tags in either slot order, and the tank supports both filling and emptying. |
| Painting, Enchanter, weather, and fire | Inventory complete. Painting uses the supplied paint block. Enchanter uses the selected enchantment and amount. Weather and fire have world effects. |
| Crafter and shaped entity storage | Both use the vanilla crafting recipe manager. The shaped entity storage serializer adds soul-storage output behavior. |
| Ingredients | Forge defines `empty_soul_storage`, `filled_soul_storage`, `any_soul_storage`, and `conduit` serializers. Typed script helpers and editable selector groups are pending. |
| Processing data | The three native data maps cover grinding balls, VAT reagents, and range extenders. Separate loaders supply farming, solar, powered spawner, and Soul Engine rules. Script editors and reload tests are pending. |
| Addon inputs | Modded conduits transport optional resources such as Mekanism chemicals. Audit which resources are consumed by processing before exposing selectors. |
| GameStages | Rule API, recipe checks, action checks, and tests with RecipeStages and ItemStages are pending. |

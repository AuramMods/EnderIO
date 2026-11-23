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
- `bonus` is `none` for unmodified chances and yields, `chance_only` to apply the chance modifier, or `multiply_output` to apply both yield and chance modifiers. The machine applies its native grinding-ball FE rules.
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

## VAT, fluid tanks, and Weather Obelisk

```zenscript
val vat = <recipetype:enderio:vat_fermenting>;
vat.addRecipe("qa_vat", <tag:fluids:minecraft:water> * 250, <tag:items:forge:ingots/gold>,
    <tag:items:forge:ingots/copper>, <fluid:minecraft:lava> * 425, 30);

val tank = <recipetype:enderio:tank>;
tank.addRecipe("qa_tank_fill", "fill", <item:minecraft:paper>, <item:minecraft:feather> * 2,
    <fluid:minecraft:water> * 100);
tank.addRecipe("qa_tank_empty", "empty", <item:minecraft:rabbit_hide>, <item:minecraft:leather>,
    <tag:fluids:minecraft:water> * 225);

val weather = <recipetype:enderio:weather_change>;
weather.addRecipe("qa_weather", <fluid:minecraft:lava> * 200, "rain");
```

| Manager | `addRecipe` parameters |
| --- | --- |
| VAT | `name, input, firstReagent, secondReagent, output, ticks`. The input specifies mB. Pass an item tag for each reagent group, a fluid stack for the base output, and the job duration in ticks. The two reagent tags may match either slot order. The VAT applies each reagent's native yield multiplier. |
| Tank | `name, mode, input, output, fluid`. Use `fill` to consume tank fluid while processing the item, or `empty` to put fluid into the tank while processing the item. The fluid determines candidates and mB. |
| Weather Obelisk | `name, fluid, mode`. Specify the required fluid stack and `clear`, `rain`, or `lightning`. Each completed job spends the given mB and one firework rocket, and the obelisk requires open sky and 600 ticks of eligible work. There is no output item or fluid. |

Each manager has a `replaceRecipe(id, ...)` method with the same parameters after the exact ID. VAT and tank fluid inputs accept CraftTweaker fluid stacks, sized fluid tags, and alternatives with a common amount. Their native fluid ingredients match by type or tag membership.

`getRecipesByFluidOutput(ingredient)` and `removeFluidOutput(ingredient)` are available for the VAT and tanks. Match with CraftTweaker fluid ingredients, which can specify a minimum amount. The VAT compares base outputs before yield multipliers. Tank output matching considers all candidate fluids for `empty` recipes. Use the weather recipe's ID to find, remove, or replace it.

## Painting, enchanting, soul binding, fire, and legacy grinding balls

```zenscript
import mods.enderio.FireOutput;
import mods.enderio.SoulSelector;

val painter = <recipetype:enderio:painting>;
painter.addRecipe("qa_paint", <item:minecraft:brick>, <item:enderio:painted_fence>);

val enchanter = <recipetype:enderio:enchanting>;
enchanter.addRecipe("qa_enchanter", <item:minecraft:amethyst_shard> * 2,
    <enchantment:minecraft:aqua_affinity>, 1);

val binder = <recipetype:enderio:soul_binding>;
binder.addRecipe("qa_binder", <item:minecraft:quartz>, <item:minecraft:amethyst_shard>,
    1600, 1, SoulSelector.entity(<entitytype:minecraft:cow>), true);

val fire = <recipetype:enderio:fire_crafting>;
fire.addRecipe("qa_fire", [<block:minecraft:emerald_block>], [], [<resource:minecraft:overworld>],
    <block:minecraft:diamond_block>, FireOutput.item(<item:minecraft:rabbit_hide>, 2, 2, 1.0f));

val balls = <recipetype:enderio:grinding_ball>;
balls.addRecipe("qa_ball", <item:minecraft:feather>, 2.0f, 1.0f, 0.5f, 5000);
```

| Manager | `addRecipe` parameters |
| --- | --- |
| Painting | `name, template, output`. A successful job consumes the template and reads the paint block from the separate paint slot. Ender IO stores the paint on the output. The common config controls FE. |
| Enchanting | `name, catalyst, enchantment, costMultiplier`. Use a sized catalyst ingredient, an enchantment bracket, and a positive XP level cost multiplier. The Enchanter uses the catalyst amount to compute the enchantment level and applies its normal book, lapis, and XP rules. |
| Soul binding | `name, material, output, energy, xpLevels, selector, copyInputNbt`. Enter FE and the XP level used to calculate the fluid cost. The Soul Binder empties one bound soul vial and uses its soul when the output supports it. Enable `copyInputNbt` to merge the material's NBT into the output before binding. |
| Fire crafting | `name, bases, baseTags, dimensions, replacement, results...`. Give an array of blocks, an array of block tags, at least one dimension ID, and an optional replacement block. Pass `null` to keep the base. `FireOutput.item(stack, minCount, maxCount, chance)` uses an inclusive range and a chance from 0 to 1. A successful drop triggers the replacement. |
| Legacy grinding ball | `name, item, grinding, chance, power, durability`. Use an item type, three multipliers in that order, and durability measured in FE spent. The modern grinding-ball data map takes priority for an item. The SAG Mill loads a ball from its slot at the end of its first eligible job. |

Each manager accepts the same arguments in `replaceRecipe(id, ...)` after the existing recipe ID. For the Enchanter, `getRecipesByEnchantment(enchantment)` and `removeByEnchantment(enchantment)` match the exact enchantment. Item output lookup uses the level-one preview book. Fire output lookups consider every nonzero-chance drop at its maximum count. Legacy grinding-ball definitions are managed by exact ID.

`SoulSelector.any()` accepts any bound soul. `SoulSelector.entity(entityType)` selects a single entity type. `SoulSelector.category(name)` selects a native mob category such as `creature`. `SoulSelector.soulRule(domain)` selects the souls accepted by a named Ender IO data loader.

## Ingredients and selector groups

Use ordinary CraftTweaker item, fluid, and block tags where a manager accepts them. For a list of souls that can change with pack progression, define an entity-type tag in a separate tag script.

```zenscript
#loader tags
<tag:entity_types:port_qa:binding_souls>.addId(<resource:minecraft:cow>);
```

```zenscript
import mods.enderio.EnderIOIngredients;
import mods.enderio.SagOutput;
import mods.enderio.SelectorGroup;
import mods.enderio.SoulSelector;

val group = SelectorGroup.conduits(<resource:port_qa:processing_conduits>);
group.replace(<resource:enderio:energy>, <resource:enderio:fluid>);
group.add(<resource:enderio:item>);
group.remove(<resource:enderio:fluid>);

val sag = <recipetype:enderio:sag_milling>;
sag.addRecipe("qa_conduit_group", group.asIngredient(), 400, "none",
    SagOutput.item(<item:minecraft:rabbit_hide>, 1.0f, false));
<recipetype:enderio:soul_binding>.addRecipe("qa_tagged_binder", <item:minecraft:quartz>, <item:minecraft:emerald>,
    1600, 1, SoulSelector.tag(<tag:entity_types:port_qa:binding_souls>), false);
```

- `EnderIOIngredients.emptySoulStorage(stack)`, `filledSoulStorage(stack)`, and `anySoulStorage(stack)` match the specified item type and its corresponding soul state. Use `<item:enderio:soul_vial>` for vials.
- `EnderIOIngredients.conduit(variant)` matches one registered conduit variant. The selector distinguishes variants that share `enderio:conduit`.
- `SelectorGroup.conduits(id)` and `SelectorGroup.dimensions(id)` open named groups. Use `add(ids...)`, `remove(ids...)`, and `replace(ids...)` to edit them and `members` to read their IDs. A conduit group's `asIngredient()` creates a recipe ingredient from the current members. A dimension group's `members` can be passed as the dimension argument in fire crafting. Declare memberships before adding or replacing the recipe. Both forms are included in normal recipe sync.
- `SoulSelector.tag(tag)` refers to a live entity-type tag. Edit its membership with normal CraftTweaker tag operations. Datapacks can also specify the optional `entity_tag` field on the native Soul Binder recipe, using the tag ID without a leading `#`.

## Processing data

```zenscript
import mods.enderio.ProcessingData;

ProcessingData.setGrindingBall(<item:minecraft:feather>, 2.0f, 1.0f, 0.5f, 5000);
ProcessingData.setVatYield(<item:minecraft:gold_ingot>, <tag:items:forge:ingots/gold>, 3.0);
ProcessingData.setRangeExtension(<block:minecraft:amethyst_block>, <tag:blocks:enderio:range_extender>, 24);
```

| Methods | Behavior |
| --- | --- |
| `setGrindingBall(item, grinding, chance, power, durability)`, `getGrindingBall(item)`, `removeGrindingBall(item)` | Edit the modern data map with the same units as the legacy recipe. The getter returns a record with `grinding`, `chance`, `power`, and `durability`, or `null`. Removing it allows the matching legacy recipe to supply the ball. |
| `setVatYield(item, reagentTag, multiplier)`, `getVatYield(item, reagentTag)`, `removeVatYield(item, reagentTag)` | The multiplier applies when that item matches the specified reagent group in an active VAT recipe. The default is 1. The two reagents' multipliers multiply together. |
| `setRangeExtension(block, targetTag, distance)`, `getRangeExtension(block, targetTag)`, `removeRangeExtension(block, targetTag)` | Set the range adjustment in blocks. The default is zero. The wireless charger reads the `enderio:range_extender` key when the block above it belongs to that block tag. Add new extender blocks to the tag in the tags loader. |

Ender IO loads stock data and datapacks first, then applies the script edits in order. Edits modify the active resource snapshot. Removing an edit script and running `/reload` restores the underlying datapack values. Already installed grinding balls and queued machine outputs follow the machine's saved state rules.

### Soul rules

```zenscript
import mods.enderio.SoulRules;

val cow = <entitytype:minecraft:cow>;
SoulRules.setSpawner(cow, 3600, "copy");
SoulRules.setEngine(cow, <fluid:minecraft:water>, 197, 5);
SoulRules.setEngineTag(cow, <tag:fluids:minecraft:water>, 197, 5);
SoulRules.setFarming(<entitytype:minecraft:bee>, 1.0f, 0, 1.0f);
SoulRules.setSolar(cow, false, true, <resource:minecraft:overworld>);
```

| Method | Parameters and behavior |
| --- | --- |
| `setSpawner(entity, energy, mode)` | FE cost and `entity_type` for a fresh entity or `copy` to spawn from the stored soul data. |
| `setEngine(entity, fuel, powerPerMb, ticksPerMb)` | A fluid stack identifying the fuel, base FE per mB, and base ticks per mB. Amounts in the fluid argument are ignored. |
| `setEngineTag(entity, fuelTag, powerPerMb, ticksPerMb)` | Uses a fluid tag in place of the single fuel. The Soul Engine still applies its native machine modifiers. |
| `setFarming(entity, bonemeal, seeds, power)` | Bonemeal consumption multiplier, native seed and Fortune adjustment, and FE multiplier. |
| `setSolar(entity, daytime, nighttime, dimension)` | Toggles soul-assisted generation by time of day. Pass a dimension ID or `null` for every dimension. |
| `getJson(domain, entity)`, `remove(domain, entity)` | Inspect the effective definition as native JSON, or remove it from that resource snapshot. Domains are `spawner`, `engine`, `farm`, and `solar`. Getters return `null` for a missing definition. |

Each setter replaces the domain's effective rule for that entity. Stock and datapack rules load first, and a fresh reload without the script restores them. The Soul Binder's `SoulSelector.soulRule(domain)` uses the effective rules. A removed spawner rule falls back to the native default for a soul already in a powered spawner. Other capture and device restrictions still apply.

## GameStages

Install GameStages and its Bookshelf dependency to opt in. Stage rules belong to the loaded server resources. Each target may require several stages, and the principal must have all stages required by the applicable rules. Removing a script and running `/reload` clears its rules.

```zenscript
import mods.enderio.EnderIOStages;

EnderIOStages.requireRecipe(<resource:enderio:alloy_smelting/redstone_alloy_ingot>, "parts");
EnderIOStages.requireRecipeType(<resource:enderio:primitive_alloy_smelting>, "machining");
EnderIOStages.requireAction(<resource:enderio:fire_crafting>, "fire_works");
EnderIOStages.setWorldStage("fire_works", true);
```

| Rule | Scope |
| --- | --- |
| `requireRecipe(id, stage)`, `clearRecipe(id)` | Exact recipe ID, including inherited smelting recipes and the Crafter's vanilla crafting recipes. |
| `requireRecipeType(type, stage)`, `clearRecipeType(type)` | Every recipe of the given type. |
| `requireBlock(block, stage)`, `clearBlock(block)` | Placement and block interaction. Ender IO machines also use this rule for automatic work. |
| `requireItem(item, stage)`, `clearItem(item)` | Interactive item use, including tool-assisted travel. |
| `requireAction(action, stage)`, `clearAction(action)` | A documented Ender IO action, identified with a resource bracket such as `<resource:enderio:machine_placement>`. |
| `setWorldStage(stage, unlocked)` | Grant or revoke a pack-wide stage for actorless processing. |

Each `clear` method removes all requirements for its target. Stage names follow GameStages validation. An exact recipe rule can be declared before that recipe exists.

| Action ID in the `enderio` namespace | Where Ender IO checks it |
| --- | --- |
| `machine_placement`, `machine_use` | Forge block placement and machine interaction, using the acting player. |
| `crafter_automation` | Automatic crafting, using the Crafter's owner. |
| `soul_capture`, `soul_spawning` | Soul Vial capture and release or spawner assignment use the acting player. Powered Spawner capture and spawn modes use the machine owner. |
| `soul_device` | Soul Engine and Farming Station operation use their owners. Solar generation uses world stages. |
| `obelisk` | Obelisk interactions use the acting player. Autonomous operation uses the owner. |
| `travel`, `mobility` | Travel Staff and anchor teleports, levitation tool use, and glider operation use the acting player. |
| `conduit_placement`, `conduit_configuration` | Placement and extension of bundles, and opening or updating conduit settings, use the acting player. |
| `fire_crafting` | Fire crafting uses world stages. The world stage must also satisfy any exact fire recipe or family requirement. |

The player taking a book from the Enchanter supplies that recipe's stage context. A machine uses the owner recorded at placement for unattended recipes. A disconnected or missing owner and a Forge fake player cannot satisfy personal stage rules. A shared machine keeps its recorded owner when another player interacts with it. No stage requirement applies until a script configures one.

If the required stage is lost mid-job, the recipe check pauses work before further charges or item consumption. The machine retains prior work and saved outputs under its existing recipe and output rules. A recipe lock leaves normal inventory access available. The Crafter also honors RecipeStages crafting wrappers and uses the owner's stages to evaluate them. ItemStages still handles its usual player item restrictions.

## Crafting and addon inputs

The Crafter uses `<recipetype:minecraft:crafting>` or the equivalent `craftingTable` global. Use the normal shaped and shapeless methods, or load Ender IO's shaped entity-storage serializer with `addJsonRecipe`.

```zenscript
craftingTable.addJsonRecipe("qa_entity_storage", {
    "type": "enderio:shaped_entity_storage",
    "pattern": ["V"],
    "key": {"V": {"item": "enderio:soul_vial"}},
    "result": {"item": "enderio:broken_spawner"}
});
```

The serializer requires a bound soul in the crafting grid and transfers that soul to a compatible output. To replace an existing result or pattern, call `craftingTable.removeByName("crafttweaker:qa_entity_storage")` before adding the new definition under the same path.

The entity-type tags `enderio:soul_vial_whitelist`, `enderio:soul_vial_blacklist`, `c:capturing_not_supported`, and `c:bosses` control native capture eligibility. The powered spawner uses `enderio:spawner_whitelist` and `enderio:spawner_blacklist` alongside its normal rules. Modify these with CraftTweaker's entity-type tag manager. Soul rules control how a device uses a stored soul after capture or binding.

Use Forge fluid brackets and tags for addon fluids consumed by the VAT, tanks, or Soul Engine. Mekanism gases, infuse types, pigments, and slurries use the addon’s chemical conduit transport. Recipes involving an addon conduit can select its registered variant, such as `enderio:chemical` when Mekanism is installed.

## Implementation coverage

| Processing path | Current cycle |
| --- | --- |
| Powered and primitive alloy smelting | Packaged Forge passed ZenScript lookup, add, replacement, removal, and exact machine input, output, and FE or fuel checks across server restarts. The packaged mod also booted without CraftTweaker. Client recipe-dump and JEI checks are pending. |
| Inherited furnace recipes | Packaged Forge verified a scripted furnace addition and replacement, a source removal, two alloy-only exclusions, exact FE, and restoration after live `/reload`. |
| SAG milling and slicing | Packaged Forge verified scripted additions, replacements, removal by tagged SAG output, exact FE and item counts, positional slicer inputs, and tool wear. |
| Legacy grinding ball recipes | Typed manager and packaged test coverage for yield, FE, and durability. |
| VAT fermentation, tanks, and weather | Packaged Forge verified add, replace, and remove through both tank modes, reversed VAT reagent order, exact fluid volumes and rocket use, and native weather changes. |
| Soul Binder, painting, Enchanter, and fire | Packaged Forge verified add, replace, and remove through each gameplay path. Guarded Enchanter clients verified normal pickup, shift-click, exact XP and ingredient costs, and zero charges when a player's XP or inventory space was insufficient. |
| Crafter and shaped entity storage | Packaged Forge verified add, replacement, removal, exact FE, and a soul transfer to each scripted output. |
| Ingredients | Packaged Forge verified exact conduit variants, distinct soul-vial states, and changes to conduit, entity-type, and dimension membership. |
| Processing data | Set, get, and remove ran through the grinding ball, VAT, and range extender maps. Gameplay confirmed the edited VAT yield and its removal, plus scripted farming, solar, powered spawner, and Soul Engine behavior. Modern grinding-ball and wireless charger gameplay and client sync remain pending. |
| Addon inputs | Source audit confirmed that native processing uses Forge item and fluid inputs, with registered addon conduit variants available through the same conduit selector. The full Auram compatibility run remains pending. |
| GameStages | Packaged Forge with the pinned companion mods verified exact and family lockout, saved inputs and fuel, rule clearing, and a world-stage fire grant. Multiplayer owner, manual feature, staged Crafter, and JEI checks remain pending. |

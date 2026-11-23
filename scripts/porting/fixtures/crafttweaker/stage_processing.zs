import mods.enderio.EnderIOStages;
import mods.recipestages.Recipes;

val powered = <recipetype:enderio:alloy_smelting>;
val primitive = <recipetype:enderio:primitive_alloy_smelting>;
powered.addRecipe("qa_stage_alloy", <item:minecraft:amethyst_shard>, 1500, 0.0f,
    <item:minecraft:lapis_lazuli> * 2, <item:minecraft:quartz>);
primitive.addRecipe("qa_stage_primitive", <item:minecraft:glow_ink_sac>, 80, 0.0f,
    <item:minecraft:bamboo> * 2, <item:minecraft:slime_ball>);

craftingTable.addShapeless("qa_stage_crafter", <item:minecraft:emerald>, [<item:minecraft:feather>]);
Recipes.setRecipeStage("machining", <resource:crafttweaker:qa_stage_crafter>);

EnderIOStages.requireRecipe(<resource:crafttweaker:qa_stage_alloy>, "parts");
EnderIOStages.requireRecipeType(<resource:enderio:primitive_alloy_smelting>, "machining");
EnderIOStages.requireAction(<resource:enderio:fire_crafting>, "fire_works");
println("PORT_CT_STAGES_CONFIGURED");

val powered = <recipetype:enderio:alloy_smelting>;
val primitive = <recipetype:enderio:primitive_alloy_smelting>;

powered.addRecipe("qa_powered_alloy", <item:minecraft:amethyst_shard> * 2, 1500, 0.25f,
    <item:minecraft:lapis_lazuli> * 2, <item:minecraft:quartz>);
primitive.addRecipe("qa_primitive_alloy", <item:minecraft:glow_ink_sac> * 2, 80, 0.5f,
    <item:minecraft:bamboo> * 2, <item:minecraft:slime_ball>);

if powered.getRecipeByName("crafttweaker:qa_powered_alloy") != null
        && primitive.getRecipesByOutput(<item:minecraft:glow_ink_sac>).length == 1
        && powered.allRecipes.length > 1 {
    println("PORT_CT_ALLOY_LOOKUP_OK");
}

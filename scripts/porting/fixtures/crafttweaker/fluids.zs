val vat = <recipetype:enderio:vat_fermenting>;
val tank = <recipetype:enderio:tank>;
val weather = <recipetype:enderio:weather_change>;

vat.addRecipe("qa_vat", <tag:fluids:minecraft:water> * 250, <tag:items:forge:ingots/gold>,
    <tag:items:forge:ingots/copper>, <fluid:minecraft:lava> * 425, 30);
tank.addRecipe("qa_tank_fill", "fill", <item:minecraft:paper>, <item:minecraft:feather> * 2,
    <fluid:minecraft:water> * 100);
tank.addRecipe("qa_tank_empty", "empty", <item:minecraft:rabbit_hide>, <item:minecraft:leather>,
    <tag:fluids:minecraft:water> * 225);
weather.addRecipe("qa_weather", <fluid:minecraft:lava> * 200, "rain");

if vat.getRecipesByFluidOutput(<fluid:minecraft:lava> * 425).length == 1
        && tank.getRecipesByFluidOutput(<fluid:minecraft:water> * 225).length > 0
        && weather.getRecipeByName("crafttweaker:qa_weather") != null {
    println("PORT_CT_FLUID_ADD_OK");
}

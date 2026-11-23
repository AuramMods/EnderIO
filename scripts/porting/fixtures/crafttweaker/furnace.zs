val furnace = <recipetype:minecraft:smelting>;
val alloy = <recipetype:enderio:alloy_smelting>;

furnace.removeByName("crafttweaker:qa_furnace_override", "minecraft:baked_potato");
furnace.addRecipe("qa_furnace_override", <item:minecraft:emerald>, <item:minecraft:feather>, 0.75f, 30);
furnace.addRecipe("qa_furnace_added", <item:minecraft:amethyst_shard>, <item:minecraft:wheat>, 0.25f, 30);
furnace.addRecipe("qa_furnace_excluded", <item:minecraft:diamond>, <item:minecraft:fern>, 1.0f, 30);

alloy.excludeFurnaceRecipe("crafttweaker:qa_furnace_excluded");
alloy.removeByName("enderio:smelting/minecraft/gold_ingot_from_smelting_raw_gold");
println("PORT_CT_FURNACE_RULES_OK");

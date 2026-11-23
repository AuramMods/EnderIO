val enchanter = <recipetype:enderio:enchanting>;
enchanter.removeByName("enderio:enchanting/sharpness");
enchanter.addRecipe("qa_sharpness", <tag:items:forge:gems/quartz> * 12, <enchantment:minecraft:sharpness>, 1);
if enchanter.getRecipeByName("crafttweaker:qa_sharpness") != null
        && enchanter.getRecipeByName("enderio:enchanting/sharpness") == null {
    println("PORT_CT_ENCHANTER_CONFIGURED");
}

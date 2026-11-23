craftingTable.addJsonRecipe("qa_entity_storage", {
    "type": "enderio:shaped_entity_storage",
    "pattern": ["V"],
    "key": {
        "V": {"item": "enderio:soul_vial"}
    },
    "result": {"item": "enderio:broken_spawner"}
});
if craftingTable.getRecipeByName("crafttweaker:qa_entity_storage") != null {
    println("PORT_CT_SHAPED_SOUL_OK");
}

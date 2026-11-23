import mods.enderio.SagOutput;

val sag = <recipetype:enderio:sag_milling>;
val slice = <recipetype:enderio:slicing>;
sag.addRecipe("qa_sag", <item:minecraft:paper>, 800, "none",
    SagOutput.item(<item:minecraft:amethyst_shard> * 2, 1.0f, false),
    SagOutput.tag(<tag:items:forge:ingots/copper>, 1, 1.0f, false),
    SagOutput.item(<item:minecraft:glow_ink_sac>, 0.0001f, true));
slice.addRecipe("qa_slice", <item:minecraft:echo_shard> * 2, 600,
    <item:minecraft:dandelion>, <item:minecraft:air>, <item:minecraft:poppy>,
    <item:minecraft:air>, <item:minecraft:air>, <item:minecraft:cornflower>);

if sag.getRecipesByOutput(<item:minecraft:glow_ink_sac>).length == 1
        && slice.getRecipeByName("crafttweaker:qa_slice") != null {
    println("PORT_CT_SAG_SLICE_ADD_OK");
}

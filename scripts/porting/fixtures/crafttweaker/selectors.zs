import mods.enderio.EnderIOIngredients;
import mods.enderio.FireOutput;
import mods.enderio.ProcessingData;
import mods.enderio.SagOutput;
import mods.enderio.SelectorGroup;
import mods.enderio.SoulSelector;

val variants = SelectorGroup.conduits(<resource:port_qa:processing_conduits>);
variants.replace(<resource:enderio:energy>, <resource:enderio:fluid>);
variants.add(<resource:enderio:item>);
variants.remove(<resource:enderio:fluid>);

val dimensions = SelectorGroup.dimensions(<resource:port_qa:fire_worlds>);
dimensions.add(<resource:minecraft:overworld>, <resource:minecraft:the_nether>);
dimensions.remove(<resource:minecraft:the_nether>);

val sag = <recipetype:enderio:sag_milling>;
sag.addRecipe("qa_conduit_group", variants.asIngredient(), 400, "none",
    SagOutput.item(<item:minecraft:rabbit_hide>, 1.0f, false));
sag.addRecipe("qa_single_conduit", EnderIOIngredients.conduit(<resource:enderio:enhanced_energy>), 400, "none",
    SagOutput.item(<item:minecraft:glow_ink_sac>, 1.0f, false));
sag.addRecipe("qa_empty_vial", EnderIOIngredients.emptySoulStorage(<item:enderio:soul_vial>), 400, "none",
    SagOutput.item(<item:minecraft:feather>, 1.0f, false));
sag.addRecipe("qa_filled_vial", EnderIOIngredients.filledSoulStorage(<item:enderio:soul_vial>), 400, "none",
    SagOutput.item(<item:minecraft:blaze_powder>, 1.0f, false));
sag.addRecipe("qa_any_vial", EnderIOIngredients.anySoulStorage(<item:enderio:soul_vial>), 400, "none",
    SagOutput.item(<item:minecraft:glowstone_dust>, 1.0f, false));
sag.removeByName("crafttweaker:qa_any_vial");

<recipetype:enderio:soul_binding>.addRecipe("qa_tagged_binder", <item:minecraft:quartz>, <item:minecraft:emerald>,
    1600, 1, SoulSelector.tag(<tag:entity_types:port_qa:binding_souls>), false);
<recipetype:enderio:fire_crafting>.addRecipe("qa_fire_group", [<block:minecraft:emerald_block>], [], dimensions.members,
    <block:minecraft:diamond_block>, FireOutput.item(<item:minecraft:rabbit_hide>, 2, 2, 1.0f));
<recipetype:enderio:vat_fermenting>.addRecipe("qa_yield", <fluid:minecraft:water> * 250,
    <tag:items:forge:ingots/gold>, <tag:items:forge:ingots/copper>, <fluid:minecraft:lava> * 425, 30);

ProcessingData.setGrindingBall(<item:minecraft:feather>, 2.0f, 1.0f, 0.5f, 5000);
ProcessingData.setVatYield(<item:minecraft:gold_ingot>, <tag:items:forge:ingots/gold>, 3.0);
ProcessingData.setRangeExtension(<block:minecraft:amethyst_block>, <tag:blocks:enderio:range_extender>, 24);
val grindingBall = ProcessingData.getGrindingBall(<item:minecraft:feather>);
if grindingBall != null && grindingBall.durability == 5000
        && ProcessingData.getVatYield(<item:minecraft:gold_ingot>, <tag:items:forge:ingots/gold>) == 3.0
        && ProcessingData.getRangeExtension(<block:minecraft:amethyst_block>, <tag:blocks:enderio:range_extender>) == 24
        && variants.members.length == 2 && dimensions.members.length == 1 {
    println("PORT_CT_SELECTORS_OK");
}

import mods.enderio.FireOutput;
import mods.enderio.SagOutput;
import mods.enderio.SoulSelector;

val painter = <recipetype:enderio:painting>;
val enchanter = <recipetype:enderio:enchanting>;
val binder = <recipetype:enderio:soul_binding>;
val fire = <recipetype:enderio:fire_crafting>;
val balls = <recipetype:enderio:grinding_ball>;
val sag = <recipetype:enderio:sag_milling>;

painter.addRecipe("qa_paint", <item:minecraft:brick>, <item:enderio:painted_fence>);
enchanter.addRecipe("qa_enchanter", <item:minecraft:amethyst_shard> * 2,
    <enchantment:minecraft:aqua_affinity>, 1);
binder.addRecipe("qa_binder", <item:minecraft:quartz>, <item:minecraft:amethyst_shard>,
    1600, 1, SoulSelector.entity(<entitytype:minecraft:cow>), true);
fire.addRecipe("qa_fire", [<block:minecraft:emerald_block>], [], [<resource:minecraft:overworld>],
    <block:minecraft:diamond_block>, FireOutput.item(<item:minecraft:rabbit_hide>, 2, 2, 1.0f));
balls.addRecipe("qa_ball", <item:minecraft:feather>, 2.0f, 1.0f, 0.5f, 5000);
sag.addRecipe("qa_ball_probe", <item:minecraft:stone_button>, 1200, "multiply_output",
    SagOutput.item(<item:minecraft:glow_ink_sac>, 1.0f, false));

if painter.getRecipeByName("crafttweaker:qa_paint") != null
        && enchanter.getRecipesByEnchantment(<enchantment:minecraft:aqua_affinity>).length >= 1
        && binder.getRecipeByName("crafttweaker:qa_binder") != null
        && fire.getRecipesByOutput(<item:minecraft:rabbit_hide>).length == 1
        && balls.getRecipeByName("crafttweaker:qa_ball") != null {
    println("PORT_CT_SPECIAL_ADD_OK");
}

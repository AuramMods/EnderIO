import mods.enderio.SoulRules;

val cow = <entitytype:minecraft:cow>;
val bee = <entitytype:minecraft:bee>;
SoulRules.setSpawner(cow, 3600, "copy");
SoulRules.setEngine(cow, <fluid:minecraft:water>, 197, 5);
SoulRules.setEngineTag(cow, <tag:fluids:minecraft:water>, 197, 5);
SoulRules.setFarming(bee, 1.0f, 0, 1.0f);
SoulRules.setSolar(cow, false, true, <resource:minecraft:overworld>);
if SoulRules.getJson("spawner", cow) != null && SoulRules.getJson("engine", cow) != null
        && SoulRules.getJson("farm", bee) != null && SoulRules.getJson("solar", cow) != null {
    println("PORT_CT_SOUL_RULES_CONFIGURED");
}

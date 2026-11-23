import mods.enderio.ProcessingData;

ProcessingData.setGrindingBall(<item:minecraft:flint>, 1.5f, 1.75f, 0.5f, 45678);
ProcessingData.setGrindingBall(<item:minecraft:charcoal>, 1.5f, 1.75f, 0.5f, 45678);
ProcessingData.setVatYield(<item:minecraft:gunpowder>, <tag:items:enderio:explosives>, 2.25);
ProcessingData.removeVatYield(<item:minecraft:fire_charge>, <tag:items:enderio:explosives>);
ProcessingData.setRangeExtension(<block:enderio:wireless_charger_antenna>, <tag:blocks:enderio:range_extender>, 29);
ProcessingData.removeRangeExtension(<block:enderio:wireless_charger_antenna_advanced>, <tag:blocks:enderio:range_extender>);

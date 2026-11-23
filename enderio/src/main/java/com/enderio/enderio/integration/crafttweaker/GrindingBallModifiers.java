package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.enderio.enderio.api.components.GrindingBallData;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.GrindingBallModifiers")
public record GrindingBallModifiers(GrindingBallData data) {
    @ZenCodeType.Getter("grinding")
    public float grinding() {
        return data.outputMultiplier();
    }

    @ZenCodeType.Getter("chance")
    public float chance() {
        return data.bonusMultiplier();
    }

    @ZenCodeType.Getter("power")
    public float power() {
        return data.powerUse();
    }

    @ZenCodeType.Getter("durability")
    public int durability() {
        return data.durability();
    }
}

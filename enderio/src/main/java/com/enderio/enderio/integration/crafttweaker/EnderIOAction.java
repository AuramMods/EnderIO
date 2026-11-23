package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.action.base.IRuntimeAction;

record EnderIOAction(String describe, Runnable change) implements IRuntimeAction {
    @Override
    public void apply() {
        change.run();
    }

    @Override
    public String systemName() {
        return "Ender IO";
    }
}

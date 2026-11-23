package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.action.base.IAction;

record EnderIOAction(String describe, Runnable change) implements IAction {
    @Override
    public void apply() {
        change.run();
    }

    @Override
    public String systemName() {
        return "Ender IO";
    }
}

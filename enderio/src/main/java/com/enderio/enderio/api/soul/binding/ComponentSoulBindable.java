package com.enderio.enderio.api.soul.binding;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;

public class ComponentSoulBindable implements SoulBindable {

    protected final ItemStack parent;
    protected final ItemDataKey<Soul> componentType;

    public ComponentSoulBindable(ItemStack parent, ItemDataKey<Soul> componentType) {
        this.parent = parent;
        this.componentType = componentType;
    }

    @Override
    public Soul getBoundSoul() {
        return componentType.getOrDefault(parent, Soul.EMPTY);
    }

    @Override
    public boolean canBind() {
        return true;
    }

    @Override
    public boolean isSoulValid(Soul soul) {
        return true;
    }

    @Override
    public void bindSoul(Soul newSoul) {
        if (!canBind()) {
            throw new UnsupportedOperationException("Cannot rebind this item.");
        }

        if (!isSoulValid(newSoul)) {
            throw new IllegalArgumentException("Soul is not valid for this item.");
        }

        componentType.set(parent, newSoul);
    }
}

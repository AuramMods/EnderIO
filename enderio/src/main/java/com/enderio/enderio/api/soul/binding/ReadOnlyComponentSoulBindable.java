package com.enderio.enderio.api.soul.binding;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;

public class ReadOnlyComponentSoulBindable implements SoulBindable {

    protected final ItemStack parent;
    protected final ItemDataKey<Soul> componentType;

    public ReadOnlyComponentSoulBindable(ItemStack parent, ItemDataKey<Soul> componentType) {
        this.parent = parent;
        this.componentType = componentType;
    }

    @Override
    public Soul getBoundSoul() {
        return componentType.getOrDefault(parent, Soul.EMPTY);
    }

    @Override
    public boolean canBind() {
        return false;
    }

    @Override
    public boolean isSoulValid(Soul soul) {
        return false;
    }

    @Override
    public void bindSoul(Soul newSoul) {
        throw new UnsupportedOperationException("Cannot rebind this item.");
    }
}

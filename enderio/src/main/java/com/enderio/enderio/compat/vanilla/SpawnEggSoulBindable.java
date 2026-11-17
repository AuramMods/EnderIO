package com.enderio.enderio.compat.vanilla;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

public class SpawnEggSoulBindable implements SoulBindable {
    private final ItemStack spawnEgg;

    public SpawnEggSoulBindable(ItemStack spawnEgg) {
        this.spawnEgg = spawnEgg;
    }

    @Override
    public Soul getBoundSoul() {
        if (!(spawnEgg.getItem() instanceof SpawnEggItem spawnEggItem)) {
            return Soul.EMPTY;
        }

        // Get custom entity data
        var customEntityData = spawnEgg.getTagElement("EntityTag");
        if (customEntityData != null && !customEntityData.isEmpty()) {
            return new Soul(spawnEggItem.getType(spawnEgg.getTag()), customEntityData.copy());
        }

        return Soul.of(spawnEggItem.getType(spawnEgg.getTag()));
    }

    @Override
    public boolean canBind() {
        return false;
    }

    @Override
    public boolean isSoulValid(Soul soul) {
        return Soul.isSameEntity(getBoundSoul(), soul);
    }

    @Override
    public void bindSoul(Soul newSoul) {
        throw new UnsupportedOperationException("Cannot bind a spawn egg to a soul");
    }
}

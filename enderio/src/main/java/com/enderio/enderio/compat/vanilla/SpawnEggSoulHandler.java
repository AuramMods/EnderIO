package com.enderio.enderio.compat.vanilla;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.storage.SoulHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

public class SpawnEggSoulHandler implements SoulHandler {
    private final ItemStack spawnEgg;

    public SpawnEggSoulHandler(ItemStack spawnEgg) {
        this.spawnEgg = spawnEgg;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public Soul getSoulInSlot(int slot) {
        if (slot > 0) {
            return Soul.EMPTY;
        }

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
    public boolean tryInsertSoul(Soul soul, boolean isSimulate) {
        return false;
    }

    @Override
    public Soul tryExtractSoul(boolean isSimulate) {
        return getSoulInSlot(0);
    }

    @Override
    public boolean isSoulValid(int slot, Soul soul) {
        return false;
    }
}

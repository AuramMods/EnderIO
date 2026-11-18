package com.enderio.enderio.content.tools.vials;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.foundation.EIONBTKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Restores the occupied vial payload written by Forge Ender IO 6.x. */
public final class Forge6SoulVialData {
    private Forge6SoulVialData() {}

    public static void restore(ItemStack stack) {
        var blockData = BlockItem.getBlockEntityData(stack);
        if (blockData == null || !blockData.contains(EIONBTKeys.ENTITY_STORAGE)) return;

        var storage = blockData.getCompound(EIONBTKeys.ENTITY_STORAGE);
        var savedEntity = storage.getCompound("Entity");
        var id = ResourceLocation.tryParse(savedEntity.getString(Entity.ID_TAG));
        var type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || Soul.ITEM_DATA.has(stack)) return;

        Soul.ITEM_DATA.set(stack, new Soul(type, savedEntity.copy()));
        float maxHealth = storage.getFloat("MaxHealth");
        if (maxHealth > 0 && Float.isFinite(maxHealth) && !SoulVialItem.ENTITY_MAX_HEALTH.has(stack)) {
            SoulVialItem.ENTITY_MAX_HEALTH.set(stack, maxHealth);
        }

        // Typed item writes copy the root tag. Remove the converted payload from the new copy.
        var migratedBlockData = stack.getTagElement(BlockItem.BLOCK_ENTITY_TAG);
        migratedBlockData.remove(EIONBTKeys.ENTITY_STORAGE);
        if (migratedBlockData.isEmpty()) stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
    }
}

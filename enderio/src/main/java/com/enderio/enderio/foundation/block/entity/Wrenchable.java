package com.enderio.enderio.foundation.block.entity;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

// TODO: Move to API.

/**
 * An interface that block entities may implement in order to implement special behaviours(other than to rotate the block) when right-clicked with the Yeta wrench.
 */
public interface Wrenchable {
    net.minecraft.tags.TagKey<net.minecraft.world.item.Item> WRENCH_TAG = net.minecraft.tags.ItemTags.create(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    InteractionResult onWrenched(UseOnContext context);
}

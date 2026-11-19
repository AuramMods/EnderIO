package com.enderio.enderio.content.paint.item;

import com.enderio.enderio.content.paint.PaintUtils;
import com.enderio.enderio.content.paint.block.entity.DoublePaintedBlockEntity;
import com.enderio.enderio.foundation.EIONBTKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

public class PaintedSlabBlockItem extends PaintedBlockItem {

    public PaintedSlabBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var previousState = level.getBlockState(pos);
        if (!super.placeBlock(context, state)) {
            return false;
        }

        var paintData = PaintUtils.getItemPaint(context.getItemInHand());
        if (paintData != null && level.getBlockEntity(pos) instanceof DoublePaintedBlockEntity slab) {
            var placedType = state.getValue(SlabBlock.TYPE);
            boolean addsTop = placedType == SlabType.TOP
                || placedType == SlabType.DOUBLE && previousState.getOptionalValue(SlabBlock.TYPE).orElse(null) == SlabType.BOTTOM;
            if (addsTop) {
                slab.setSecondaryPaint(paintData.paint());
            } else {
                slab.setPrimaryPaint(paintData.paint());
            }
        }
        return true;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        var tag = BlockItem.getBlockEntityData(stack);
        if (tag == null || !(tag.contains(EIONBTKeys.PAINT) || tag.contains(EIONBTKeys.PAINT_2))) {
            return BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
        }

        // Placement has already assigned paint to the added half. Merge only the remaining metadata.
        var remaining = tag.copy();
        remaining.remove(EIONBTKeys.PAINT);
        remaining.remove(EIONBTKeys.PAINT_2);
        if (remaining.isEmpty()) {
            return false;
        }

        var copy = stack.copy();
        copy.addTagElement(BlockItem.BLOCK_ENTITY_TAG, remaining);
        return BlockItem.updateCustomBlockEntityTag(level, player, pos, copy);
    }
}

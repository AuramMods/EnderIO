package com.enderio.enderio.foundation.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

public class TreeHelper {

    public static boolean isTree(BlockState blockState) {
        return blockState.is(BlockTags.LOGS) || blockState.is(BlockTags.LEAVES);
    }

    public static List<BlockPos> getTreeHarvestOrder(BlockGetter level, BlockPos bottom, Predicate<BlockPos> inRange) {
        var queue = new ArrayDeque<BlockPos>();
        var tree = new ArrayList<BlockPos>();
        var seen = new HashSet<BlockPos>();

        queue.add(bottom);
        seen.add(bottom);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!inRange.test(pos) || !isTree(level.getBlockState(pos))) {
                continue;
            }

            tree.add(pos);
            for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                BlockPos neighbor = next.immutable();
                if (seen.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        // Each block was discovered through an earlier neighbor. Removing those neighbors last
        // keeps every remaining block connected to the base if the harvest has to pause.
        Collections.reverse(tree);
        return tree;
    }
}

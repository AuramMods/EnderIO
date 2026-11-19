package com.enderio.enderio.content.machines.farming_station.tasks;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.api.farm.FarmTask;
import com.enderio.enderio.api.farm.FarmingMachine;
import com.enderio.enderio.foundation.util.TreeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class HarvestTreeFarmTask implements FarmTask {
    public static final HarvestTreeFarmTask INSTANCE = new HarvestTreeFarmTask();

    private HarvestTreeFarmTask() {
    }

    @Override
    public <T extends BlockEntity & FarmingMachine> FarmInteraction process(BlockPos targetBlock, T blockEntity) {
        var level = blockEntity.getLevel();
        BlockPos bottom = targetBlock.above();
        if (!level.getBlockState(bottom).is(BlockTags.LOGS)) {
            return FarmInteraction.IGNORED;
        }
        if (blockEntity.getAxe().isEmpty()) {
            return FarmInteraction.BLOCKED;
        }

        AABB range = new AABB(blockEntity.getPosition())
            .inflate(blockEntity.getFarmingRange())
            .inflate(4, 8, 4) // increase range to accommodate for extra leaves
            .move(0, 8, 0);
        var tree = TreeHelper.getTreeHarvestOrder(level, bottom, pos -> range.contains(pos.getX(), pos.getY(), pos.getZ()));
        for (BlockPos pos : tree) {
            ItemStack axe = blockEntity.getAxe();
            if (axe.isEmpty()) {
                return FarmInteraction.BLOCKED;
            }
            BlockState state = level.getBlockState(pos);
            if (!TreeHelper.isTree(state)) {
                continue;
            }
            ItemStack tool = state.is(BlockTags.LOGS) ? axe : blockEntity.getShears();
            if (!blockEntity.handleDrops(state, pos, targetBlock, blockEntity, tool)) {
                return FarmInteraction.BLOCKED;
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            tool.mineBlock(level, state, pos, blockEntity.getPlayer());
        }
        return FarmInteraction.FINISHED;
    }
}

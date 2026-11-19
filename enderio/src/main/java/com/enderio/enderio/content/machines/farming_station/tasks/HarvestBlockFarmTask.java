package com.enderio.enderio.content.machines.farming_station.tasks;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.api.farm.FarmTask;
import com.enderio.enderio.api.farm.FarmingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HarvestBlockFarmTask implements FarmTask {

    public static final HarvestBlockFarmTask INSTANCE = new HarvestBlockFarmTask();

    private HarvestBlockFarmTask() {
    }

    @Override
    public <T extends BlockEntity & FarmingMachine> FarmInteraction process(BlockPos targetBlock, T blockEntity) {
        var level = blockEntity.getLevel();
        BlockPos base = targetBlock.above();
        Block plant = level.getBlockState(base).getBlock();
        if (!(plant instanceof CactusBlock || plant instanceof SugarCaneBlock || plant instanceof BambooStalkBlock)) {
            return FarmInteraction.IGNORED;
        }

        BlockPos top = base;
        while (level.getBlockState(top.above()).is(plant)) {
            top = top.above();
        }
        if (top.equals(base)) {
            return FarmInteraction.IGNORED;
        }

        for (BlockPos pos = top; pos.getY() > base.getY(); pos = pos.below()) {
            BlockState state = level.getBlockState(pos);
            boolean needsTool = state.requiresCorrectToolForDrops();
            ItemStack tool = needsTool ? blockEntity.getAxe() : ItemStack.EMPTY;
            if (needsTool && tool.isEmpty()) {
                return FarmInteraction.BLOCKED;
            }
            if (!blockEntity.handleDrops(state, pos, targetBlock, blockEntity, tool)) {
                return FarmInteraction.BLOCKED;
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            if (needsTool) {
                tool.mineBlock(level, state, pos, blockEntity.getPlayer());
            }
        }
        return FarmInteraction.FINISHED;
    }
}

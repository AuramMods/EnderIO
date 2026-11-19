package com.enderio.enderio.content.machines.farming_station.tasks;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.api.farm.FarmTask;
import com.enderio.enderio.api.farm.FarmingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class HarvestPitcherFarmTask implements FarmTask {

    public static final HarvestPitcherFarmTask INSTANCE = new HarvestPitcherFarmTask();

    private HarvestPitcherFarmTask() {
    }

    @Override
    public <T extends BlockEntity & FarmingMachine> FarmInteraction process(BlockPos targetBlock, T blockEntity) {
        var level = blockEntity.getLevel();
        BlockPos pos = targetBlock.above();
        BlockState plant = level.getBlockState(pos);
        if (!(plant.getBlock() instanceof PitcherCropBlock) || plant.getValue(PitcherCropBlock.HALF) != DoubleBlockHalf.LOWER
            || plant.getValue(PitcherCropBlock.AGE) < PitcherCropBlock.MAX_AGE) {
            return FarmInteraction.IGNORED;
        }
        BlockState upper = level.getBlockState(pos.above());
        if (!upper.is(plant.getBlock()) || upper.getValue(PitcherCropBlock.HALF) != DoubleBlockHalf.UPPER) {
            return FarmInteraction.IGNORED;
        }

        boolean needsTool = plant.requiresCorrectToolForDrops();
        ItemStack tool = needsTool ? blockEntity.getAxe() : ItemStack.EMPTY;
        if ((needsTool && tool.isEmpty()) || !blockEntity.handleDrops(plant, pos, targetBlock, blockEntity, tool)) {
            return FarmInteraction.BLOCKED;
        }
        // The lower half has the loot. Vanilla updates remove the upper half when it loses its base.
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        if (needsTool) {
            tool.mineBlock(level, plant, pos, blockEntity.getPlayer());
        }
        return FarmInteraction.FINISHED;
    }
}

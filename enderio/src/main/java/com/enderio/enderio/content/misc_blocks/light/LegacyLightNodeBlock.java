package com.enderio.enderio.content.misc_blocks.light;

import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LegacyLightNodeBlock extends BaseEntityBlock {
    public LegacyLightNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegacyLightNodeBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide && !level.getBlockState(fromPos).is(EIOBlocks.LIGHT_NODE.get())
            && level.getBlockEntity(pos) instanceof LegacyLightNodeBlockEntity node
            && node.owner() != null && level.hasChunkAt(node.owner())
            && level.getBlockEntity(node.owner()) instanceof LegacyPoweredLightBlockEntity light) {
            light.refreshNodes();
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof LegacyLightNodeBlockEntity node)) {
            return;
        }
        BlockPos owner = node.owner();
        if (owner == null || !LegacyPoweredLightBlockEntity.inRange(pos, owner)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        } else if (!level.hasChunkAt(owner)) {
            level.scheduleTick(pos, this, 20);
        } else if (!(level.getBlockEntity(owner) instanceof LegacyPoweredLightBlockEntity light) || !light.isActive()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        }
    }
}

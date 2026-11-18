package com.enderio.enderio.content.misc_blocks.light;

import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LegacyPoweredLightBlock extends LegacyLightBlock implements EntityBlock {
    private final boolean wireless;

    public LegacyPoweredLightBlock(Properties properties, boolean inverted, boolean wireless) {
        super(properties, inverted);
        this.wireless = wireless;
        registerDefaultState(defaultBlockState().setValue(ENABLED, false));
    }

    boolean isWireless() {
        return wireless;
    }

    static Direction supportSide(BlockState state) {
        return getConnectedDirection(state).getOpposite();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegacyPoweredLightBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != EIOBlockEntities.POWERED_LIGHT.get()) {
            return null;
        }
        return (world, pos, current, entity) -> {
            if (entity instanceof LegacyPoweredLightBlockEntity light) {
                light.tick(world, current);
            }
        };
    }

    @Override
    protected void updateEnabled(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof LegacyPoweredLightBlockEntity light) {
            light.refreshNodes();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide && !level.getBlockState(fromPos).is(EIOBlocks.LIGHT_NODE.get())) {
            updateEnabled(level, pos, state);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !replacement.is(this)) {
            LegacyPoweredLightBlockEntity.clearNodes(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}

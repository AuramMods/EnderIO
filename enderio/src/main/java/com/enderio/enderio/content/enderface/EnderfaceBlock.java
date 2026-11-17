package com.enderio.enderio.content.enderface;

import com.enderio.enderio.api.travel.TravelTargetApi;
import com.enderio.enderio.foundation.block.EIOEntityBlock;
import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public class EnderfaceBlock extends EIOEntityBlock<EnderfaceBlockEntity> {

    public EnderfaceBlock(Properties properties) {
        super(EIOBlockEntities.ENDERFACE::get, properties);
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof EnderfaceBlockEntity) {
            TravelTargetApi.INSTANCE.removeAt(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

package com.enderio.enderio.content.misc_blocks.light;

import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LegacyLightNodeBlockEntity extends BlockEntity {
    private static final String OWNER = "BlockPos";

    @Nullable
    private BlockPos owner;

    public LegacyLightNodeBlockEntity(BlockPos pos, BlockState state) {
        super(EIOBlockEntities.LIGHT_NODE.get(), pos, state);
    }

    @Nullable
    BlockPos owner() {
        return owner;
    }

    void setOwner(BlockPos pos) {
        owner = pos.immutable();
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.put(OWNER, NbtUtils.writeBlockPos(owner));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.contains(OWNER, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(OWNER)) : null;
    }
}

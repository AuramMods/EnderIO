package com.enderio.core.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Native block-entity lifecycle for data that requires the owning world's dynamic registries. */
public abstract class RegistryBlockEntity extends EnderBlockEntity {
    @Nullable private CompoundTag pendingLoad;

    protected RegistryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public final void load(CompoundTag tag) {
        super.load(tag);
        // Only an unattached entity needs a deferred payload. A rejected live load must
        // leave future saves reading the live state instead of writing the rejected payload.
        if (level == null) {
            pendingLoad = tag.copy();
        } else {
            loadWithRegistries(tag.copy(), level.registryAccess());
            pendingLoad = null;
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        applyPendingLoad();
    }

    private void applyPendingLoad() {
        if (pendingLoad != null && level != null) {
            loadWithRegistries(pendingLoad.copy(), level.registryAccess());
            pendingLoad = null;
        }
    }

    @Override
    protected final void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (pendingLoad == null && level != null) saveWithRegistries(tag, level.registryAccess());
    }

    @Override
    protected final void saveAdditionalSynced(CompoundTag tag) {
        super.saveAdditionalSynced(tag);
        if (pendingLoad != null) tag.merge(pendingLoad.copy());
        else if (level != null) saveSyncedWithRegistries(tag, level.registryAccess());
    }

    protected abstract void loadWithRegistries(CompoundTag tag, RegistryAccess registries);
    protected abstract void saveWithRegistries(CompoundTag tag, RegistryAccess registries);
    protected abstract void saveSyncedWithRegistries(CompoundTag tag, RegistryAccess registries);
}

package com.enderio.core.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

public final class CapabilityUtil {
    private CapabilityUtil() {}

    /** Queries only loaded block entities; looking for a neighbor never loads its chunk. */
    public static <T> LazyOptional<T> find(Level level, BlockPos pos, Capability<T> capability,
            @Nullable Direction side) {
        if (!level.hasChunkAt(pos)) {
            return LazyOptional.empty();
        }
        var entity = level.getBlockEntity(pos);
        return entity == null || entity.isRemoved() ? LazyOptional.empty() : entity.getCapability(capability, side);
    }

    @Nullable
    public static <T> T get(Level level, BlockPos pos, Capability<T> capability, @Nullable Direction side) {
        return find(level, pos, capability, side).orElse(null);
    }

    /** Notifies cached consumers when an Ender IO block changes its exposed sides or handlers. */
    public static void refresh(BlockEntity entity) {
        BoundCapabilityProvider.refresh(entity);
    }

    public static void refresh(Level level, BlockPos pos) {
        if (level.hasChunkAt(pos)) {
            var entity = level.getBlockEntity(pos);
            if (entity != null) refresh(entity);
        }
    }
}

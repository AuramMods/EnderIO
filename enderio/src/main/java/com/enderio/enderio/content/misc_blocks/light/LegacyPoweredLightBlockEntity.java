package com.enderio.enderio.content.misc_blocks.light;

import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.ArrayList;
import java.util.List;

public class LegacyPoweredLightBlockEntity extends BlockEntity {
    private static final String ACTIVE = "Active";
    private static final int RANGE = 2;

    private boolean active;
    private boolean nodesDirty = true;

    public LegacyPoweredLightBlockEntity(BlockPos pos, BlockState state) {
        super(EIOBlockEntities.POWERED_LIGHT.get(), pos, state);
    }

    void tick(Level level, BlockState state) {
        var light = (LegacyPoweredLightBlock) state.getBlock();
        boolean enabled = light.allowsLighting(level, worldPosition) && (light.isWireless() || drawPower(level, state));
        if (active != enabled) {
            active = enabled;
            nodesDirty = true;
            setChanged();
        }
        LegacyLightBlock.setEnabled(level, worldPosition, state, enabled);

        // Across chunk borders there may be new positions to inspect as nearby chunks load.
        if (nodesDirty || active && crossesChunkBorder() && level.getGameTime() % 20 == 0) {
            if (active) {
                updateNodes(level);
            } else {
                clearNodes(level, worldPosition);
            }
            nodesDirty = false;
        }
    }

    private boolean drawPower(Level level, BlockState state) {
        Direction side = LegacyPoweredLightBlock.supportSide(state);
        BlockPos sourcePos = worldPosition.relative(side);
        if (!level.hasChunkAt(sourcePos)) {
            return false;
        }
        BlockEntity source = level.getBlockEntity(sourcePos);
        return source != null && source.getCapability(ForgeCapabilities.ENERGY, side.getOpposite())
            .map(storage -> storage.extractEnergy(1, false) == 1)
            .orElse(false);
    }

    boolean isActive() {
        return active;
    }

    void refreshNodes() {
        nodesDirty = true;
    }

    private boolean crossesChunkBorder() {
        int x = worldPosition.getX() & 15;
        int z = worldPosition.getZ() & 15;
        return x < RANGE || x > 15 - RANGE || z < RANGE || z > 15 - RANGE;
    }

    private void updateNodes(Level level) {
        var nodeBlock = EIOBlocks.LIGHT_NODE.get();
        List<BlockPos> obstacles = new ArrayList<>();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : nodePositions(worldPosition)) {
            if (!accessible(level, pos) || pos.equals(worldPosition)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            (state.isAir() || state.is(nodeBlock) ? candidates : obstacles).add(pos.immutable());
        }

        for (BlockPos pos : candidates) {
            if (obstacles.stream().anyMatch(obstacle -> blocksSpread(pos, obstacle))) {
                removeOwnedNode(level, pos, worldPosition);
            } else if (level.getBlockState(pos).isAir() && level.setBlock(pos, nodeBlock.defaultBlockState(), Block.UPDATE_ALL)
                && level.getBlockEntity(pos) instanceof LegacyLightNodeBlockEntity node) {
                node.setOwner(worldPosition);
            }
        }
    }

    // A block casts a shadow away from the source along each of the three axes.
    private boolean blocksSpread(BlockPos target, BlockPos obstacle) {
        return between(worldPosition.getX(), target.getX(), obstacle.getX())
            && between(worldPosition.getY(), target.getY(), obstacle.getY())
            && between(worldPosition.getZ(), target.getZ(), obstacle.getZ());
    }

    private static boolean between(int start, int end, int value) {
        return value >= Math.min(start, end) && value <= Math.max(start, end);
    }

    private static Iterable<BlockPos> nodePositions(BlockPos center) {
        return BlockPos.betweenClosed(center.offset(-RANGE, -RANGE, -RANGE), center.offset(RANGE, RANGE, RANGE));
    }

    static boolean inRange(BlockPos pos, BlockPos center) {
        return Math.abs(pos.getX() - center.getX()) <= RANGE && Math.abs(pos.getY() - center.getY()) <= RANGE
            && Math.abs(pos.getZ() - center.getZ()) <= RANGE;
    }

    private static boolean accessible(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
    }

    static void clearNodes(Level level, BlockPos center) {
        for (BlockPos pos : nodePositions(center)) {
            if (accessible(level, pos)) {
                removeOwnedNode(level, pos, center);
            }
        }
    }

    private static void removeOwnedNode(Level level, BlockPos pos, BlockPos owner) {
        if (level.getBlockEntity(pos) instanceof LegacyLightNodeBlockEntity node && owner.equals(node.owner())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(ACTIVE, active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        active = tag.getBoolean(ACTIVE);
        nodesDirty = true;
    }
}

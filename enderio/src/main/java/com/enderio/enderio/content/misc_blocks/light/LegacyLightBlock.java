package com.enderio.enderio.content.misc_blocks.light;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LegacyLightBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    private static final VoxelShape CEILING_X = box(6, 14, 5, 10, 16, 11);
    private static final VoxelShape CEILING_Z = box(5, 14, 6, 11, 16, 10);
    private static final VoxelShape FLOOR_X = box(6, 0, 5, 10, 2, 11);
    private static final VoxelShape FLOOR_Z = box(5, 0, 6, 11, 2, 10);
    private static final VoxelShape NORTH = box(5, 6, 14, 11, 10, 16);
    private static final VoxelShape SOUTH = box(5, 6, 0, 11, 10, 2);
    private static final VoxelShape WEST = box(14, 6, 5, 16, 10, 11);
    private static final VoxelShape EAST = box(0, 6, 5, 2, 10, 11);

    private final boolean inverted;

    public LegacyLightBlock(Properties properties, boolean inverted) {
        super(properties);
        this.inverted = inverted;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL).setValue(ENABLED, !inverted));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED, FACE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(FACE)) {
            case FLOOR -> facing.getAxis() == Direction.Axis.X ? FLOOR_X : FLOOR_Z;
            case CEILING -> facing.getAxis() == Direction.Axis.X ? CEILING_X : CEILING_Z;
            case WALL -> switch (facing) {
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                case EAST -> EAST;
                default -> NORTH;
            };
        };
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!level.isClientSide && !oldState.is(this)) {
            updateEnabled(level, pos, state);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide) {
            updateEnabled(level, pos, state);
        }
    }

    protected boolean allowsLighting(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos) == inverted;
    }

    protected void updateEnabled(Level level, BlockPos pos, BlockState state) {
        setEnabled(level, pos, state, allowsLighting(level, pos));
    }

    static void setEnabled(Level level, BlockPos pos, BlockState state, boolean enabled) {
        if (state.getValue(ENABLED) != enabled) {
            level.setBlock(pos, state.setValue(ENABLED, enabled), UPDATE_ALL);
        }
    }
}

package com.enderio.enderio.foundation.block;

import com.enderio.enderio.integration.gamestages.StageRules;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.api.soul.storage.SoulHandler;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class MachineBlock<T extends MachineBlockEntity> extends EIOEntityBlock<T> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MachineBlock(Supplier<BlockEntityType<? extends T>> typeSupplier, Properties properties) {
        super(typeSupplier, properties);

        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Override this if you have a machine with no menu.
     */
    protected boolean canOpenMenu() {
        return true;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        // Do not allow opening in spectator mode.
        // TODO: We can convert our menus to not use a BE backing fully to enable this.
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && !StageRules.permitsMachineMenu(player, machine)) {
            return InteractionResult.FAIL;
        }
        var result = useHeldItem(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (result != InteractionResult.PASS) return result;
        return hand == InteractionHand.MAIN_HAND ? openMachineMenu(state, level, pos, player, hit) : InteractionResult.PASS;
    }

    protected InteractionResult openMachineMenu(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {

        // Attempt to open machine menu.
        if (canOpenMenu()) {
            if (level.getBlockEntity(pos) instanceof MenuProvider menuProvider) {
                if (player instanceof ServerPlayer serverPlayer) {
                    net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, menuProvider, pos);
                }

                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.setMachineOwner(player.getUUID());
        }
    }

    protected InteractionResult useHeldItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hitResult) {
        if (!player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }

        SoulBindable soulBindable = CapabilityUtil.get(level, pos, SoulBindable.CAPABILITY, null);
        if (soulBindable != null && soulBindable.canBind()) {
            SoulHandler soulHandler = stack.getCapability(SoulHandler.CAPABILITY).orElse(null);
            if (soulHandler != null) {
                for (int i = 0; i < soulHandler.getSlots(); i++) {
                    Soul soul = soulHandler.getSoulInSlot(i);
                    if (soulBindable.isSoulValid(soul)) {
                        if (!level.isClientSide) soulBindable.bindSoul(soul.copy());
                        return InteractionResult.sidedSuccess(level.isClientSide);
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }
}

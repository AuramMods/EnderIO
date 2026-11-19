package com.enderio.enderio.content.tools;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.core.common.network.EmitParticlePacket;
import com.enderio.core.common.network.CoreNetwork;
import com.enderio.enderio.foundation.tag.EIOTags;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class ExperienceRodItem extends Item {
    public ExperienceRodItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        boolean wasSuccess;
        if (player.isShiftKeyDown()) {
            wasSuccess = transferFromPlayerToBlock(player, level, pos, clickedFace);
        } else {
            wasSuccess = transferFromBlockToPlayer(player, level, pos, clickedFace);
        }

        if (wasSuccess) {
            var particle = com.enderio.enderio.init.EIOParticles.EXPERIENCE_EFFECT.get();

            CoreNetwork.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(pos),
                new EmitParticlePacket(particle, pos, 0.2, 0.8, 0.2));

            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.1f,
                0.5F * ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.8F));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static boolean transferFromBlockToPlayer(Player player, Level level, BlockPos pos, Direction side) {
        var handler = CapabilityUtil.get(level, pos, ForgeCapabilities.FLUID_HANDLER, side);
        if (handler == null) {
            return false;
        }
        FluidStack stored = handler.getFluidInTank(0);
        int pointVolume = ExperienceUtil.EXP_TO_FLUID;
        int budget = Math.min(player.getXpNeededForNextLevel(), stored.getAmount() / pointVolume);
        if (budget <= 0 || !stored.getFluid().is(EIOTags.Fluids.EXPERIENCE)) {
            return false;
        }
        FluidStack requested = EnderFluidUtil.copyWithAmount(stored, budget * pointVolume);
        int availablePoints = handler.drain(requested, IFluidHandler.FluidAction.SIMULATE).getAmount() / pointVolume;
        if (availablePoints <= 0) {
            return false;
        }
        requested.setAmount(availablePoints * pointVolume);
        int points = handler.drain(requested, IFluidHandler.FluidAction.EXECUTE).getAmount() / pointVolume;
        if (points <= 0) {
            return false;
        }
        player.giveExperiencePoints(points);
        return true;
    }

    private static boolean transferFromPlayerToBlock(Player player, Level level, BlockPos pos, Direction direction) {
        var handler = CapabilityUtil.get(level, pos, ForgeCapabilities.FLUID_HANDLER, direction);
        int pointVolume = ExperienceUtil.EXP_TO_FLUID;
        int budget = (int) Math.min(ExperienceUtil.getPlayerTotalXp(player), Integer.MAX_VALUE / pointVolume);
        if (handler == null || budget <= 0) {
            return false;
        }
        var offered = new FluidStack(EIOFluids.XP_JUICE.source().get(), budget * pointVolume);
        int availablePoints = handler.fill(offered, IFluidHandler.FluidAction.SIMULATE) / pointVolume;
        if (availablePoints <= 0) {
            return false;
        }
        offered.setAmount(availablePoints * pointVolume);
        int points = handler.fill(offered, IFluidHandler.FluidAction.EXECUTE) / pointVolume;
        if (points <= 0) {
            return false;
        }
        player.giveExperiencePoints(-points);
        return true;
    }
}

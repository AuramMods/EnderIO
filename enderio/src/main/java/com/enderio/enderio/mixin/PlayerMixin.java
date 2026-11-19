package com.enderio.enderio.mixin;

import com.enderio.enderio.content.enderface.EnderfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IForgePlayer {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean canReach(BlockPos pos, double padding) {
        return enderio$canReachBlock(pos, getBlockReach() + padding);
    }

    @Override
    public boolean canReachRaw(BlockPos pos, double padding) {
        return enderio$canReachBlock(pos, getAttributeValue(ForgeMod.BLOCK_REACH.get()) + padding);
    }

    @Unique
    private boolean enderio$canReachBlock(BlockPos pos, double reach) {
        // Keep Forge's inclusive eye-to-center check without a default-interface super call in the mixin.
        return getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach
            || EnderfaceBlockEntity.canPlayerInteractWithBlock((Player) (Object) this, level(), pos);
    }
}

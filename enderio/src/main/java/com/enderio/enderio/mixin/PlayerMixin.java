package com.enderio.enderio.mixin;

import com.enderio.enderio.content.enderface.EnderfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IForgePlayer {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean canReach(BlockPos pos, double padding) {
        return IForgePlayer.super.canReach(pos, padding)
            || EnderfaceBlockEntity.canPlayerInteractWithBlock((Player) (Object) this, level(), pos);
    }

    @Override
    public boolean canReachRaw(BlockPos pos, double padding) {
        return IForgePlayer.super.canReachRaw(pos, padding)
            || EnderfaceBlockEntity.canPlayerInteractWithBlock((Player) (Object) this, level(), pos);
    }
}

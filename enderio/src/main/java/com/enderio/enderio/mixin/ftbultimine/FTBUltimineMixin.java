package com.enderio.enderio.mixin.ftbultimine;

import com.enderio.enderio.compat.ftb_ultimine.FTBUltimineCompat;
import dev.architectury.event.EventResult;
import dev.architectury.utils.value.IntValue;
import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = FTBUltimine.class, remap = false)
public abstract class FTBUltimineMixin {
    @Shadow private boolean isBreakingBlock;

    @Redirect(method = "blockBroken", at = @At(value = "INVOKE", target =
        "Ldev/ftb/mods/ftbultimine/FTBUltiminePlayerData;rayTrace(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult enderio$captureHit(ServerPlayer player) {
        var hit = FTBUltiminePlayerData.rayTrace(player);
        FTBUltimineCompat.captureHit(player, hit);
        return hit;
    }

    @Redirect(method = "blockBroken", at = @At(value = "INVOKE", remap = true, target =
        "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean enderio$breakConduit(ServerPlayerGameMode gameMode, BlockPos target,
            Level level, BlockPos origin, BlockState state, ServerPlayer player, IntValue xp) {
        return FTBUltimineCompat.breakBlock(gameMode, player, target);
    }

    @Inject(method = "blockBroken", at = @At("RETURN"))
    private void enderio$finishBreaking(Level level, BlockPos pos, BlockState state, ServerPlayer player,
            IntValue xp, CallbackInfoReturnable<EventResult> cir) {
        // Nested Forge block-break events return while the outer operation is active.
        if (!isBreakingBlock) FTBUltimineCompat.finishBreaking(player);
    }
}

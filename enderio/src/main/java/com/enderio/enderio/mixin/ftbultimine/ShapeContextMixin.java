package com.enderio.enderio.mixin.ftbultimine;

import com.enderio.enderio.compat.ftb_ultimine.ConduitBlockSelectionHandler;
import dev.ftb.mods.ftbultimine.shape.ShapeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ShapeContext.class, remap = false)
public abstract class ShapeContextMixin {
    @Shadow public abstract ServerPlayer player();
    @Shadow public abstract BlockPos pos();
    @Shadow public abstract BlockState original();

    @Inject(method = "check(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void enderio$selectConduit(BlockPos candidate, CallbackInfoReturnable<Boolean> cir) {
        var result = ConduitBlockSelectionHandler.INSTANCE.customSelectionCheck(player(), pos(), candidate,
            original(), player().level().getBlockState(candidate));
        if (result != Event.Result.DEFAULT) cir.setReturnValue(result == Event.Result.ALLOW);
    }
}

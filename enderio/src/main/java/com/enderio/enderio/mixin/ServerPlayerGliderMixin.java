package com.enderio.enderio.mixin;

import com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayerGliderMixin {
    @Shadow public ServerPlayer player;
    @Shadow private double lastGoodY;
    @Shadow private boolean clientIsFloating;

    @Inject(method = "handleMovePlayer", at = @At(value = "FIELD", target =
        "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z",
        opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void acceptDescendingGlider(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // Forge checks collision remainder here. Use the actual accepted descent for an equipped, active glider.
        if (clientIsFloating && lastGoodY - player.getY() > 0.03125 && PlayerMovementHandler.isGliding(player)) {
            clientIsFloating = false;
        }
    }
}

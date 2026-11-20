package com.enderio.enderio.mixin;

import com.enderio.enderio.client.content.machines.gui.screen.EnderfaceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void enderio$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        enderio$invalidatePreviewBlock(pos);
    }

    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void enderio$blockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState == newState) {
            enderio$invalidatePreviewBlock(pos);
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void enderio$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        enderio$invalidatePreviewChunk(pos);
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void enderio$chunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        enderio$invalidatePreviewChunk(chunk.getPos());
    }

    @Unique
    private void enderio$invalidatePreviewBlock(BlockPos pos) {
        if (Minecraft.getInstance().screen instanceof EnderfaceScreen preview) {
            preview.invalidateBlock((ClientLevel) (Object) this, pos);
        }
    }

    @Unique
    private void enderio$invalidatePreviewChunk(ChunkPos pos) {
        if (Minecraft.getInstance().screen instanceof EnderfaceScreen preview) {
            preview.invalidateChunk((ClientLevel) (Object) this, pos);
        }
    }
}

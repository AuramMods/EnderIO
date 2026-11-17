package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;

public class ServerboundEnderfaceInteractPacket {
    public static final ResourceLocation ID = EnderIOAPI.rl("enderface_interact");
    public static final PacketCodec<ServerboundEnderfaceInteractPacket> STREAM_CODEC = PacketCodec
            .ofMember(ServerboundEnderfaceInteractPacket::write, ServerboundEnderfaceInteractPacket::new);

    private final BlockHitResult blockHit;

    public ServerboundEnderfaceInteractPacket(BlockHitResult blockHit) {
        this.blockHit = blockHit;
    }

    private ServerboundEnderfaceInteractPacket(FriendlyByteBuf buffer) {
        this.blockHit = buffer.readBlockHitResult();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockHitResult(blockHit);
    }

    public BlockHitResult getHitResult() {
        return blockHit;
    }

}

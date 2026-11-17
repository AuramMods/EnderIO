package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.BlockHitResult;

public class ServerboundEnderfaceInteractPacket implements CustomPacketPayload {
    public static final Type<ServerboundEnderfaceInteractPacket> TYPE = new Type<>(EnderIO.rl("enderface_interact"));
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

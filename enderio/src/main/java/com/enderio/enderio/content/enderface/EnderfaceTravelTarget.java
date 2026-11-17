package com.enderio.enderio.content.enderface;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.api.travel.TravelTarget;
import com.enderio.enderio.api.travel.TravelTargetSerializer;
import com.enderio.enderio.api.travel.TravelTargetType;
import com.enderio.enderio.init.EIOTravelTargets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public record EnderfaceTravelTarget(BlockPos pos) implements TravelTarget {
    public static NetworkDataSlot.CodecType<EnderfaceTravelTarget> DATA_SLOT_TYPE = new NetworkDataSlot.CodecType<>(
            EnderfaceTravelTarget.Serializer.CODEC.codec(), EnderfaceTravelTarget.Serializer.STREAM_CODEC);

    @Override
    public int item2BlockRange() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int block2BlockRange() {
        return Integer.MAX_VALUE;
    }

    @Override
    public TravelTargetType<?> type() {
        return EIOTravelTargets.ENDERFACE_TYPE.get();
    }

    @Override
    public TravelTargetSerializer<?> serializer() {
        return EIOTravelTargets.ENDERFACE_SERIALIZER.get();
    }

    @Override
    public boolean canTeleportTo() {
        return false;
    }

    @Override
    public boolean canTravelTo() {
        return false;
    }

    @Override
    public boolean canJumpTo() {
        return false;
    }

    @Override
    public boolean canInteract() {
        return true;
    }

    @Override
    public boolean interact(Level level, Player player) {
        if (level.isClientSide) {
            // TODO: Causes classload errors on dedicated servers.
//            Minecraft.getInstance().setScreen(new EnderfaceScreen(pos.immutable(), Minecraft.getInstance().level));
        }
        return true;
    }

    public static class Serializer implements TravelTargetSerializer<EnderfaceTravelTarget> {

        public static MapCodec<EnderfaceTravelTarget> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(EnderfaceTravelTarget::pos))
                        .apply(instance, EnderfaceTravelTarget::new));

        public static PacketCodec<EnderfaceTravelTarget> STREAM_CODEC = PacketCodec
                .composite(PacketCodecs.BLOCK_POS, EnderfaceTravelTarget::pos, EnderfaceTravelTarget::new);

        @Override
        public MapCodec<EnderfaceTravelTarget> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<EnderfaceTravelTarget> streamCodec() {
            return STREAM_CODEC;
        }
    }
}

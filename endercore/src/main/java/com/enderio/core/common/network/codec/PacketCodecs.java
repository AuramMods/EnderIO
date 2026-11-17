package com.enderio.core.common.network.codec;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Reuses the platform's item, fluid and particle serializers, including Forge NBT. */
public final class PacketCodecs {
    public static final PacketCodec<Boolean> BOOL = PacketCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final PacketCodec<Short> SHORT = PacketCodec.of((buffer, value) -> buffer.writeShort(value), FriendlyByteBuf::readShort);
    public static final PacketCodec<Integer> INT = PacketCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt);
    public static final PacketCodec<Long> VAR_LONG = PacketCodec.of(FriendlyByteBuf::writeVarLong, FriendlyByteBuf::readVarLong);
    public static final PacketCodec<Float> FLOAT = PacketCodec.of(FriendlyByteBuf::writeFloat, FriendlyByteBuf::readFloat);
    public static final PacketCodec<Double> DOUBLE = PacketCodec.of(FriendlyByteBuf::writeDouble, FriendlyByteBuf::readDouble);
    public static final PacketCodec<String> STRING_UTF8 = PacketCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf);
    public static final int MAX_BYTE_ARRAY_SIZE = 1_048_576;
    public static final PacketCodec<byte[]> BYTE_ARRAY = PacketCodec.of((buffer, value) -> {
        if (value.length > MAX_BYTE_ARRAY_SIZE) throw new EncoderException("Byte array exceeds packet limit");
        buffer.writeByteArray(value);
    }, buffer -> buffer.readByteArray(MAX_BYTE_ARRAY_SIZE));
    public static final PacketCodec<BlockPos> BLOCK_POS = PacketCodec.of(FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::readBlockPos);
    public static final PacketCodec<ResourceLocation> RESOURCE_LOCATION = PacketCodec.of(FriendlyByteBuf::writeResourceLocation,
        FriendlyByteBuf::readResourceLocation);
    public static final PacketCodec<ItemStack> ITEM_STACK = PacketCodec.of(FriendlyByteBuf::writeItem, FriendlyByteBuf::readItem);
    public static final PacketCodec<FluidStack> FLUID_STACK = PacketCodec.of((buffer, stack) -> stack.writeToPacket(buffer),
        FluidStack::readFromPacket);
    public static final PacketCodec<ParticleOptions> PARTICLE = PacketCodec.of((buffer, particle) -> {
        buffer.writeVarInt(BuiltInRegistries.PARTICLE_TYPE.getId(particle.getType()));
        particle.writeToNetwork(buffer);
    }, buffer -> {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.byId(buffer.readVarInt());
        if (type == null) throw new DecoderException("Unknown particle type");
        return readParticle(type, buffer);
    });

    private PacketCodecs() {}

    private static <T extends ParticleOptions> T readParticle(ParticleType<T> type, FriendlyByteBuf buffer) {
        return type.getDeserializer().fromNetwork(type, buffer);
    }
}

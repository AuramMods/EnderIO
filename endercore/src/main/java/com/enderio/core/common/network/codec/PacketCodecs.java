package com.enderio.core.common.network.codec;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.IForgeRegistry;
import com.mojang.datafixers.util.Either;

import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Reuses the platform's item, fluid and particle serializers, including Forge NBT. */
public final class PacketCodecs {
    public static final PacketCodec<Boolean> BOOL = PacketCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final PacketCodec<Short> SHORT = PacketCodec.of((buffer, value) -> buffer.writeShort(value), FriendlyByteBuf::readShort);
    public static final PacketCodec<Integer> INT = PacketCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt);
    public static final PacketCodec<Integer> VAR_INT = PacketCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
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
    public static final PacketCodec<UUID> UUID = PacketCodec.of(FriendlyByteBuf::writeUUID, FriendlyByteBuf::readUUID);
    public static final PacketCodec<Direction> DIRECTION = idMapper(Direction::from3DDataValue, Direction::get3DDataValue);
    public static final PacketCodec<DyeColor> DYE_COLOR = idMapper(DyeColor::byId, DyeColor::getId);
    public static final PacketCodec<Ingredient> INGREDIENT = PacketCodec.of((buffer, value) -> value.toNetwork(buffer), Ingredient::fromNetwork);
    public static final PacketCodec<CompoundTag> COMPOUND_TAG = PacketCodec.of((buffer, value) -> {
        if (value == null) throw new EncoderException("Required compound tag is absent");
        buffer.writeNbt(value);
    }, buffer -> {
        CompoundTag tag = buffer.readNbt(); // Native 2 MiB accounting, not readAnySizeNbt.
        if (tag == null) throw new DecoderException("Required compound tag is absent");
        return tag;
    });
    public static final PacketCodec<ItemStack> ITEM_STACK = PacketCodec.of(FriendlyByteBuf::writeItem, FriendlyByteBuf::readItem);
    public static final PacketCodec<ItemStack> REQUIRED_ITEM_STACK = PacketCodec.of((buffer, stack) -> {
        if (stack.isEmpty()) throw new EncoderException("Required item stack is empty");
        ITEM_STACK.encode(buffer, stack);
    }, buffer -> {
        ItemStack stack = ITEM_STACK.decode(buffer);
        if (stack.isEmpty()) throw new DecoderException("Required item stack is empty");
        return stack;
    });
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

    /** Rejects unknown IDs even when the native enum/registry accessor wraps or returns a default. */
    public static <T> PacketCodec<T> idMapper(IntFunction<T> fromId, ToIntFunction<T> toId) {
        return PacketCodec.of((buffer, value) -> {
            int id = toId.applyAsInt(value);
            if (id < 0 || !java.util.Objects.equals(value, fromId.apply(id))) throw new EncoderException("Unregistered packet value");
            buffer.writeVarInt(id);
        }, buffer -> {
            int id = buffer.readVarInt();
            T value = id < 0 ? null : fromId.apply(id);
            if (value == null || toId.applyAsInt(value) != id) throw new DecoderException("Unknown packet value ID: " + id);
            return value;
        });
    }

    public static <L, R> PacketCodec<Either<L, R>> either(PacketCodec<L> left, PacketCodec<R> right) {
        return PacketCodec.of((buffer, value) -> value.map(l -> {
            buffer.writeBoolean(true);
            left.encode(buffer, l);
            return null;
        }, r -> {
            buffer.writeBoolean(false);
            right.encode(buffer, r);
            return null;
        }), buffer -> buffer.readBoolean() ? Either.left(left.decode(buffer)) : Either.right(right.decode(buffer)));
    }

    public static <T> PacketCodec<ResourceKey<T>> resourceKey(ResourceKey<? extends Registry<T>> registry) {
        return PacketCodec.of((buffer, value) -> {
            if (!value.isFor(registry)) throw new EncoderException("Resource key belongs to a different registry");
            buffer.writeResourceLocation(value.location());
        }, buffer -> ResourceKey.create(registry, buffer.readResourceLocation()));
    }

    public static <T> PacketCodec<T> registry(Registry<T> registry) {
        return idMapper(registry::byId, value -> registry.getResourceKey(value).isPresent() ? registry.getId(value) : -1);
    }

    /** Custom Forge registries are created later; resolve the supplier only when a packet is processed. */
    public static <T> PacketCodec<T> forgeRegistry(Supplier<IForgeRegistry<T>> supplier) {
        return PacketCodec.of((buffer, value) -> {
            IForgeRegistry<T> registry = supplier.get();
            ResourceLocation key = registry.getKey(value);
            if (key == null || registry.getValue(key) != value) throw new EncoderException("Unregistered Forge packet value");
            buffer.writeResourceLocation(key);
        }, buffer -> {
            IForgeRegistry<T> registry = supplier.get();
            ResourceLocation key = buffer.readResourceLocation();
            if (!registry.containsKey(key)) throw new DecoderException("Unknown Forge packet value: " + key);
            return registry.getValue(key);
        });
    }

    /** Dynamic holders must belong to the current packet's registry; direct/unbound or stale holders are invalid. */
    public static <T> PacketCodec<Holder<T>> holderRegistry(ResourceKey<? extends Registry<T>> key) {
        return PacketCodec.of((buffer, holder) -> {
            if (!(buffer instanceof RegistryPacketBuffer contextual)) throw new EncoderException("Missing packet registry context: " + key);
            Registry<T> registry = contextual.registryAccess().registryOrThrow(key);
            ResourceKey<T> entry = holder.unwrapKey().orElseThrow(() -> new EncoderException("Unbound packet holder"));
            if (!entry.isFor(key) || registry.getHolder(entry).orElse(null) != holder) throw new EncoderException("Holder belongs to a different registry context");
            buffer.writeResourceLocation(entry.location());
        }, buffer -> {
            if (!(buffer instanceof RegistryPacketBuffer contextual)) throw new DecoderException("Missing packet registry context: " + key);
            Registry<T> registry = contextual.registryAccess().registryOrThrow(key);
            ResourceLocation location = buffer.readResourceLocation();
            return registry.getHolder(ResourceKey.create(key, location)).orElseThrow(() -> new DecoderException("Unknown packet holder: " + location));
        });
    }

    private static <T extends ParticleOptions> T readParticle(ParticleType<T> type, FriendlyByteBuf buffer) {
        return type.getDeserializer().fromNetwork(type, buffer);
    }
}

package com.enderio.enderio.api.soul;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.resources.RegistryOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a stored soul, derived from a {@link LivingEntity}.
 * @param entityTag the entity's NBT tag.
 */
public record Soul(@Nullable EntityType<?> entityType, CompoundTag entityTag) {

    // Keys that should not be compared or saved
    // Note be careful adding new things to this list - it will affect saves.
    private static final List<String> IGNORED_KEYS = List.of(
        Entity.ID_TAG, // We store the entity type separately to the entity data.
        "Air",
        "Brain",
        "DeathTime",
        "FallDistance",
        "FallFlying",
        "HurtByTimestamp",
        "HurtTime",
        "Motion",
        "OnGround",
        "PortalCooldown",
        "Pos",
        "Rotation",
        "SleepingX",
        "SleepingY",
        "SleepingZ",
        "Leash",
        Entity.UUID_TAG
    );

    // Do not compare obviously unreasonable NBT Keys
    private static final List<String> IGNORED_KEYS_DURING_COMPARISON = List.of(
        // TODO: need to check for any other tags that should be ignored.
        // Perhaps make this configurable?
        Bee.TAG_CANNOT_ENTER_HIVE_TICKS,
        Bee.TAG_TICKS_SINCE_POLLINATION,
        Bee.TAG_CROPS_GROWN_SINCE_POLLINATION,
        Bee.TAG_HIVE_POS,
        Entity.PASSENGERS_TAG
    );

    public static final Soul EMPTY = new Soul(Optional.empty(), new CompoundTag());

    private static final Codec<Soul> NEW_CODEC;

    private static final Codec<Soul> OLD_CODEC = CompoundTag.CODEC.fieldOf("entityTag").codec().flatXmap(tag -> {
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString(Entity.ID_TAG));
        if (typeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)) {
            return DataResult.error(() -> "Unknown legacy soul entity type: " + tag.getString(Entity.ID_TAG));
        }
        return DataResult.success(new Soul(BuiltInRegistries.ENTITY_TYPE.get(typeId), tag));
    }, soul -> soul.isEmpty() ? DataResult.error(() -> "Cannot encode empty legacy soul")
        : DataResult.success(soul.getEntityTagWithId()));

    // DFU in 1.20.1's optionalFieldOf suppresses malformed present values. Preserve
    // null/missing support without turning an unknown entity ID into an empty soul.
    private static final MapCodec<Optional<EntityType<?>>> OPTIONAL_ENTITY_TYPE = new MapCodec<>() {
        @Override
        public <T> DataResult<Optional<EntityType<?>>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T value = input.get("entity_type");
            return value == null || value.equals(ops.empty()) ? DataResult.success(Optional.empty())
                : ResourceLocation.CODEC.parse(ops, value).flatMap(id -> BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? DataResult.success(Optional.of(BuiltInRegistries.ENTITY_TYPE.get(id)))
                    : DataResult.error(() -> "Unknown soul entity type: " + id));
        }

        @Override
        public <T> RecordBuilder<T> encode(Optional<EntityType<?>> value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            value.ifPresent(type -> prefix.add("entity_type", BuiltInRegistries.ENTITY_TYPE.byNameCodec().encodeStart(ops, type)));
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString("entity_type"));
        }
    };

    public static final Codec<Soul> OPTIONAL_CODEC;

    static {
        // Moved to optionalFieldOf with an optional wrapper to ensure that we can load a null entity_type, but throw it as invalid.
        // Otherwise it'll attempt to deserialize null and fail deep inside the codec logic.
        // TODO: 26.1: Change entityType to be an Optional perhaps? Not doing anything further here to avoid breaking API.
        Codec<Soul> newCodec = RecordCodecBuilder.create(
            instance -> instance.group(
                OPTIONAL_ENTITY_TYPE.forGetter(soul -> Optional.ofNullable(soul.entityType())),
                CompoundTag.CODEC.fieldOf("entity_tag").forGetter(Soul::entityTag)
            ).apply(instance, Soul::new));

        // Only apply validator to the main codec, we'll skip it on the optional codec for now in case
        // TODO: 26.1: simplify this back down.
        NEW_CODEC = newCodec.flatXmap(Soul::requireEntity, Soul::requireEntity);

        Codec<Soul> optionalValueCodec = alternative(newCodec, OLD_CODEC);
        OPTIONAL_CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Soul, T>> decode(DynamicOps<T> ops, T input) {
                boolean emptyMap = ops.getMap(input).result().map(map -> map.entries().findAny().isEmpty()).orElse(false);
                return emptyMap ? DataResult.success(Pair.of(Soul.EMPTY, ops.empty()))
                    : optionalValueCodec.decode(ops, input).map(pair -> pair.mapFirst(soul -> soul.isEmpty() ? Soul.EMPTY : soul));
            }

            @Override
            public <T> DataResult<T> encode(Soul soul, DynamicOps<T> ops, T prefix) {
                return soul.isEmpty() ? ops.mergeToMap(prefix, java.util.Map.of()) : optionalValueCodec.encode(soul, ops, prefix);
            }
        };
    }

    private static DataResult<Soul> requireEntity(Soul soul) {
        return soul.entityType == null ? DataResult.error(() -> "Entity type cannot be null") : DataResult.success(soul);
    }

    private static Codec<Soul> alternative(Codec<Soul> current, Codec<Soul> legacy) {
        return Codec.either(current, legacy).xmap(either -> either.map(soul -> soul, soul -> soul), Either::left);
    }

    public static final Codec<Soul> CODEC = alternative(NEW_CODEC, OLD_CODEC);

    public static final PacketCodec<Soul> STREAM_CODEC = PacketCodec.of((buffer, soul) -> {
        if (soul.isEmpty()) throw new EncoderException("Cannot encode an empty soul with the required soul codec");
        buffer.writeVarInt(BuiltInRegistries.ENTITY_TYPE.getId(soul.entityType()));
        buffer.writeNbt(soul.entityTag());
    }, buffer -> {
        int entityId = buffer.readVarInt();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(entityId);
        CompoundTag tag = buffer.readNbt();
        if (type == null || BuiltInRegistries.ENTITY_TYPE.getId(type) != entityId || tag == null) throw new DecoderException("Invalid soul entity type or NBT");
        return new Soul(type, tag);
    });

    public Soul {
        // Remove tags we don't want
        IGNORED_KEYS.forEach(entityTag::remove);
    }

    private Soul(Optional<EntityType<?>> entityTypeOpt, CompoundTag entityTag) {
        this(entityTypeOpt.orElse(null), entityTag);
    }

    public static final PacketCodec<Soul> OPTIONAL_STREAM_CODEC = new PacketCodec<>() {
        @Override
        public Soul decode(FriendlyByteBuf byteBuf) {
            boolean hasEntity = byteBuf.readBoolean();
            if (!hasEntity) {
                return EMPTY;
            }

            return STREAM_CODEC.decode(byteBuf);
        }

        @Override
        public void encode(FriendlyByteBuf o, Soul soul) {
            o.writeBoolean(soul.hasEntity());
            if (soul.hasEntity()) {
                STREAM_CODEC.encode(o, soul);
            }
        }
    };

    public static Soul of(LivingEntity entity) {
        var entityTag = new CompoundTag();
        entity.saveWithoutId(entityTag);
        return new Soul(entity.getType(), entityTag);
    }

    public static Soul of(ResourceLocation entityType) {
        return of(BuiltInRegistries.ENTITY_TYPE.get(entityType));
    }

    public static Soul of(EntityType<?> entityType) {
        return new Soul(entityType, new CompoundTag());
    }

    public static boolean isSameEntity(Soul soul1, Soul soul2) {
        return Objects.equals(soul1.entityType(), soul2.entityType());
    }

    public static boolean isSameEntity(Soul soul, LivingEntity livingEntity) {
        return isSameEntity(soul, livingEntity.getType());
    }

    public static boolean isSameEntity(Soul soul, EntityType<?> entityType) {
        return Objects.equals(soul.entityType(), entityType);
    }

    public static boolean isSameEntitySameTag(Soul soul1, Soul soul2) {
        if (!isSameEntity(soul1, soul2)) {
            return false;
        }

        return isSameTag(soul1.entityTag(), soul2.entityTag());
    }

    public static boolean isSameEntitySameTag(Soul soul, LivingEntity livingEntity) {
        if (!isSameEntity(soul, livingEntity)) {
            return false;
        }

        var entityTagToCompare = new CompoundTag();
        livingEntity.saveWithoutId(entityTagToCompare);
        return isSameTag(soul.entityTag(), entityTagToCompare);
    }

    private static boolean isSameTag(CompoundTag tag1, CompoundTag tag2) {
        var allKeys = Stream.concat(tag1.getAllKeys().stream(), tag2.getAllKeys().stream()).collect(Collectors.toSet());
        for (var key : allKeys) {
            if (IGNORED_KEYS.contains(key) ||
                IGNORED_KEYS_DURING_COMPARISON.contains(key)) {
                continue;
            }

            if (!Objects.equals(tag1.get(key), tag2.get(key))) {
                return false;
            }
        }

        return true;
    }

    public boolean hasEntity() {
        return entityType != null;
    }

    public boolean isEmpty() {
        return entityType == null;
    }

    /**
     * @throws IllegalStateException if the soul is empty.
     * @return
     */
    public ResourceLocation entityTypeId() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot get Entity Type ID from empty StoredEntityData");
        }

        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
    }

    public CompoundTag getEntityTagWithId() {
        var tag = entityTag.copy();
        tag.putString(Entity.ID_TAG, entityTypeId().toString());
        return tag;
    }

    public Soul copy() {
        if (isEmpty()) {
            return EMPTY;
        }

        return new Soul(entityType(), entityTag.copy());
    }

    public Soul copyOnlyType() {
        if (isEmpty()) {
            return EMPTY;
        }

        return of(entityType());
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public Tag save(HolderLookup.Provider lookupProvider) {
        if (!this.hasEntity()) {
            throw new IllegalStateException("Cannot encode empty StoredEntityData");
        } else {
            return CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), this).getOrThrow(false, message -> LOGGER.error("Cannot save soul: {}", message));
        }
    }

    public Tag saveOptional(HolderLookup.Provider lookupProvider) {
        return this.hasEntity() ? save(lookupProvider) : new CompoundTag();
    }

    public static Optional<Soul> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), tag)
            .resultOrPartial(error -> LOGGER.error("Tried to load invalid StoredEntityData: '{}'", error));
    }

    public static Soul parseOptional(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : parse(lookupProvider, tag).orElse(EMPTY);
    }
}

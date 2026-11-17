package com.enderio.core.common.recipes;

import com.enderio.core.common.network.codec.RegistryPacketBuffer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Serialized recipe data used to associate saved task outputs with their source recipe. */
public final class RecipeSnapshot {
    private RecipeSnapshot() {}

    @SuppressWarnings("unchecked")
    public static <R extends Recipe<?>> byte[] encode(R recipe, RegistryAccess registries) {
        var buffer = new RegistryPacketBuffer(Unpooled.buffer(), registries);
        try {
            var serializer = (RecipeSerializer<R>) recipe.getSerializer();
            buffer.writeResourceLocation(BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer));
            if (serializer instanceof CodecRecipeSerializer<?> codecSerializer) {
                // Saved comparisons need registry names, not connection-local numeric item IDs.
                buffer.writeBoolean(true);
                var codec = ((CodecRecipeSerializer<R>) codecSerializer).codec().codec();
                var data = codec.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), recipe)
                    .getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
                buffer.writeNbt((CompoundTag) data);
            } else {
                // Native recipe serializers supplied by other mods may expose only their wire form.
                buffer.writeBoolean(false);
                serializer.toNetwork(buffer, recipe);
            }
            var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }
}

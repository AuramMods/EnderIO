package com.enderio.enderio.api.conduits;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.serialization.CodecIngredientSerializer;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.init.EIOItems;
import com.google.gson.JsonElement;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.AvailableSince("8.0.5")
@ApiStatus.Experimental
public class ConduitIngredient extends AbstractIngredient {
    public static final MapCodec<ConduitIngredient> CODEC = ResourceLocation.CODEC.fieldOf("conduit_type")
        .xmap(ConduitIngredient::new, ConduitIngredient::conduitId);
    public static final CodecIngredientSerializer<ConduitIngredient> TYPE = new CodecIngredientSerializer<>(CODEC.codec(),
        PacketCodecs.RESOURCE_LOCATION.map(ConduitIngredient::new, ConduitIngredient::conduitId));

    private final ResourceLocation conduitId;

    private ConduitIngredient(ResourceLocation conduitId) { this.conduitId = conduitId; }

    public static Ingredient of(Holder<Conduit<?, ?>> conduit) {
        var key = conduit.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Conduit ingredient requires a registry key"));
        if (!key.isFor(EnderIORegistries.Keys.CONDUIT) || !conduit.isBound()) {
            throw new IllegalArgumentException("Conduit ingredient requires a bound conduit registry holder");
        }
        return new ConduitIngredient(key.location());
    }

    public ResourceLocation conduitId() { return conduitId; }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return stack != null && stack.is(EIOItems.CONDUIT.get()) && conduitId.equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    @Override
    public ItemStack[] getItems() { return new ItemStack[] {ConduitBlockItem.getStackFor(conduitId, 1)}; }

    @Override
    public boolean isEmpty() { return false; }

    @Override
    public boolean isSimple() { return false; }

    @Override
    public CodecIngredientSerializer<ConduitIngredient> getSerializer() { return TYPE; }

    @Override
    public JsonElement toJson() { return TYPE.toJson(this); }
}

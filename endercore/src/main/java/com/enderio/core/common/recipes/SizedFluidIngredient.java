package com.enderio.core.common.recipes;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.serialization.JsonCodec;
import com.mojang.serialization.Codec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import java.util.Arrays;
import java.util.Objects;

public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {
    public static final Codec<SizedFluidIngredient> FLAT_CODEC = JsonCodec.of(element -> {
        var json = GsonHelper.convertToJsonObject(element, "sized fluid ingredient");
        return new SizedFluidIngredient(FluidIngredient.fromJson(json.has("ingredient") ? json.get("ingredient") : json),
            GsonHelper.getAsInt(json, "amount", 1000));
    }, value -> {
        var json = value.ingredient.toJson();
        json.addProperty("amount", value.amount);
        return json;
    });
    public static final PacketCodec<SizedFluidIngredient> STREAM_CODEC = PacketCodec.composite(
        FluidIngredient.STREAM_CODEC, SizedFluidIngredient::ingredient, PacketCodecs.VAR_INT, SizedFluidIngredient::amount, SizedFluidIngredient::new);
    public SizedFluidIngredient {
        Objects.requireNonNull(ingredient);
        if (amount <= 0) throw new IllegalArgumentException("Fluid ingredient amount must be positive");
    }
    public static SizedFluidIngredient of(Fluid fluid, int amount) { return new SizedFluidIngredient(FluidIngredient.of(fluid), amount); }
    public static SizedFluidIngredient of(FluidStack stack) { return of(stack.getFluid(), stack.getAmount()); }
    public static SizedFluidIngredient of(TagKey<Fluid> tag, int amount) { return new SizedFluidIngredient(FluidIngredient.tag(tag), amount); }
    public boolean test(FluidStack stack) { return stack.getAmount() >= amount && ingredient.test(stack); }
    public FluidStack[] getFluids() { return Arrays.stream(ingredient.getStacks()).map(stack -> new FluidStack(stack, amount)).toArray(FluidStack[]::new); }
}

package com.enderio.enderio.content.machines.soul_binder;

import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.network.MassiveStreamCodec;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.api.soul.binding.ingredients.FilledSoulStorageIngredient;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.foundation.recipe.FluidRecipeInput;
import com.enderio.enderio.foundation.souldata.SoulDataReloadListener;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import io.netty.handler.codec.DecoderException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record SoulBindingRecipe(ItemStack output, Ingredient input, int energy, int experience,
        Optional<ResourceLocation> entityType, Optional<MobCategory> mobCategory, Optional<String> soulData,
        boolean copyInputComponents, Optional<TagKey<EntityType<?>>> entityTag, ResourceLocation id)
        implements MachineRecipe<SoulBindingRecipe.Input> {

    public SoulBindingRecipe(ItemStack output, Ingredient input, int energy, int experience, Optional<ResourceLocation> entityType, Optional<MobCategory> mobCategory, Optional<String> soulData, boolean copyInputComponents) { this(output, input, energy, experience, entityType, mobCategory, soulData, copyInputComponents, null); }

    public SoulBindingRecipe(ItemStack output, Ingredient input, int energy, int experience,
            Optional<ResourceLocation> entityType, Optional<MobCategory> mobCategory, Optional<String> soulData,
            boolean copyInputComponents, ResourceLocation id) {
        this(output, input, energy, experience, entityType, mobCategory, soulData, copyInputComponents, Optional.empty(), id);
    }

    public SoulBindingRecipe withId(ResourceLocation id) {
        return new SoulBindingRecipe(output, input, energy, experience, entityType, mobCategory, soulData,
                copyInputComponents, entityTag, java.util.Objects.requireNonNull(id));
    }

    public Ingredient getInput() {
        return input;
    }

    @Override
    public int getBaseEnergyCost() {
        return energy;
    }

    @Override
    public List<OutputStack> craft(Input input, RegistryAccess registryAccess) {
        ItemStack vial = input.getItem(0);
        ItemStack result = createOutput(input.itemToBind());

        var vialSoulBindable = vial.getCapability(EnderIOCapabilities.SOUL_BINDABLE_ITEM).orElse(null);
        var resultSoulBinding = result.getCapability(EnderIOCapabilities.SOUL_BINDABLE_ITEM).orElse(null);

        if (vialSoulBindable != null && resultSoulBinding != null) {
            resultSoulBinding.bindSoul(vialSoulBindable.getBoundSoul());
        }

        return List.of(OutputStack.of(result), OutputStack.of(EIOItems.SOUL_VIAL.get().getDefaultInstance()));
    }

    public ItemStack createOutput(ItemStack material) {
        var result = output.copy();
        if (copyInputComponents && material.hasTag()) {
            result.getOrCreateTag().merge(material.getTag().copy());
            ItemCapabilityRegistry.refresh(result);
        }
        return result;
    }

    @Override
    public List<OutputStack> getResultStacks(RegistryAccess registryAccess) {
        return List.of(OutputStack.of(output.copy()), OutputStack.of(EIOItems.SOUL_VIAL.get().getDefaultInstance()));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, FilledSoulStorageIngredient.of(EIOItems.SOUL_VIAL), input);
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        if (!recipeInput.getItem(0).is(EIOItems.SOUL_VIAL.get())) {
            return false;
        }

        if (!input.test(recipeInput.getItem(1))) {
            return false;
        }

        return acceptsSoul(SoulBoundUtils.getBoundSoul(recipeInput.getItem(0)), level)
                && ExperienceUtil.getLevelFromFluid(recipeInput.getFluid(2).getAmount()) >= experience;
    }

    public boolean acceptsSoul(Soul soul) {
        return acceptsSoul(soul, null);
    }

    public boolean acceptsSoul(Soul soul, Level level) {
        if (soul.isEmpty()) {
            return false;
        }

        if (soulData.isPresent()) {
            var listener = SoulDataReloadListener.fromString(soulData.get());
            return listener != null && listener.matches(level, soul.entityTypeId()).isPresent();
        }

        return (mobCategory.isEmpty() || soul.entityType().getCategory() == mobCategory.get())
                && (entityType.isEmpty() || entityType.get().equals(soul.entityTypeId()))
                && (entityTag.isEmpty() || soul.entityType().is(entityTag.get()));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EIORecipes.SOUL_BINDING.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return EIORecipes.SOUL_BINDING.type().get();
    }

    public record Input(ItemStack boundSoulItem, ItemStack itemToBind, FluidStack experience)
            implements FluidRecipeInput {
        @Override
        public ItemStack getItem(int slotIndex) {
            return switch (slotIndex) {
            case 0 -> boundSoulItem;
            case 1 -> itemToBind;
            case 2 -> ItemStack.EMPTY;
            default -> throw new IllegalArgumentException("No item for index " + slotIndex);
            };
        }

        @Override
        public FluidStack getFluid(int slotIndex) {
            if (slotIndex != 2) {
                throw new IllegalArgumentException("No fluid for index " + slotIndex);
            }

            return experience;
        }

        @Override
        public int size() {
            return 3;
        }
    }

    public static class Serializer extends com.enderio.core.common.recipes.CodecRecipeSerializer<SoulBindingRecipe> {

        private static final MapCodec<SoulBindingRecipe> CODEC = RecordCodecBuilder.<SoulBindingRecipe>mapCodec(instance -> instance
                .group(com.enderio.core.common.recipes.RecipeStackCodecs.ITEM.fieldOf("output").forGetter(SoulBindingRecipe::output),
                        com.enderio.core.common.recipes.IngredientCodecs.REQUIRED.fieldOf("input").forGetter(SoulBindingRecipe::input),
                        Codec.INT.fieldOf("energy").forGetter(SoulBindingRecipe::energy),
                        withLegacyField("experience", Codec.INT.fieldOf("experience"), Codec.INT.fieldOf("exp"))
                                .forGetter(SoulBindingRecipe::experience),
                        ResourceLocation.CODEC.optionalFieldOf("entity_type").forGetter(SoulBindingRecipe::entityType),
                        MobCategory.CODEC.optionalFieldOf("mob_category").forGetter(SoulBindingRecipe::mobCategory),
                        withLegacyField("soul_data", Codec.STRING.optionalFieldOf("soul_data"), Codec.STRING.optionalFieldOf("souldata"))
                                .forGetter(SoulBindingRecipe::soulData),
                        Codec.BOOL.optionalFieldOf("copyInputComponents", false)
                                .forGetter(SoulBindingRecipe::copyInputComponents),
                        TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity_tag").forGetter(SoulBindingRecipe::entityTag))
                .apply(instance, (output, input, energy, experience, type, category, data, copy, tag) ->
                        new SoulBindingRecipe(output, input, energy, experience, type, category, data, copy, tag, null)))
                .flatXmap(Serializer::validate, Serializer::validate);

        private static <F> MapCodec<F> withLegacyField(String field, MapCodec<F> current, MapCodec<F> legacy) {
            return new MapCodec<>() {
                @Override
                public <T> DataResult<F> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return (input.get(field) == null ? legacy : current).decode(ops, input);
                }

                @Override
                public <T> RecordBuilder<T> encode(F value, DynamicOps<T> ops, RecordBuilder<T> output) {
                    return current.encode(value, ops, output);
                }

                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.concat(current.keys(ops), legacy.keys(ops));
                }
            };
        }

        private static DataResult<SoulBindingRecipe> validate(SoulBindingRecipe recipe) {
            int entityType = recipe.entityType().isPresent() ? 1 : 0;
            int mobCategory = recipe.mobCategory().isPresent() ? 1 : 0;
            int soulData = recipe.soulData().isPresent() ? 1 : 0;
            int entityTag = recipe.entityTag().isPresent() ? 1 : 0;
            if (entityType + mobCategory + soulData + entityTag > 1) {
                return DataResult.error(() -> "Soul Binding recipe properties entity_type, mob_category, soul_data and entity_tag are mutually exclusive.");
            }
            return DataResult.success(recipe);
        }

        public static final PacketCodec<SoulBindingRecipe> STREAM_CODEC = MassiveStreamCodec
                .composite(PacketCodecs.REQUIRED_ITEM_STACK_DATA, SoulBindingRecipe::output, PacketCodecs.INGREDIENT,
                        SoulBindingRecipe::input, PacketCodecs.INT, SoulBindingRecipe::energy, PacketCodecs.INT,
                        SoulBindingRecipe::experience, PacketCodecs.RESOURCE_LOCATION.optional(),
                        SoulBindingRecipe::entityType,
                        // Forge's extensible enum codec is not a vanilla EnumCodec.
                        PacketCodecs.STRING_UTF8.map(
                                name -> {
                                    var category = MobCategory.byName(name);
                                    if (category == null) throw new DecoderException("Unknown mob category: " + name);
                                    return category;
                                },
                                MobCategory::getName).optional(),
                        SoulBindingRecipe::mobCategory, PacketCodecs.STRING_UTF8.optional(),
                        SoulBindingRecipe::soulData, PacketCodecs.BOOL, SoulBindingRecipe::copyInputComponents,
                        PacketCodecs.RESOURCE_LOCATION.map(id -> TagKey.create(Registries.ENTITY_TYPE, id), TagKey::location).optional(),
                        SoulBindingRecipe::entityTag, (output, input, energy, experience, type, category, data, copy, tag) ->
                                new SoulBindingRecipe(output, input, energy, experience, type, category, data, copy, tag, null));

        @Override
        protected SoulBindingRecipe withId(SoulBindingRecipe recipe, ResourceLocation id) { return recipe.withId(id); }

        @Override
        public MapCodec<SoulBindingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<SoulBindingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}

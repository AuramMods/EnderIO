package com.enderio.core.common.loot;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Native loot export of the block's carried state; world-only state is not copied. */
public final class CopyBlockEntityDataFunction extends LootItemConditionalFunction {
    @Nullable private final Set<ResourceLocation> include;

    private CopyBlockEntityDataFunction(LootItemCondition[] conditions, @Nullable Set<ResourceLocation> include) {
        super(conditions);
        this.include = include == null ? null : Set.copyOf(include);
    }

    @Override
    public LootItemFunctionType getType() { return CoreLootFunctions.COPY_BLOCK_ENTITY_DATA.get(); }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() { return Set.of(LootContextParams.BLOCK_ENTITY); }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof EnderBlockEntity entity) {
            entity.copyItemDataTo(stack, include);
        }
        return stack;
    }

    public static Builder<?> copyAll() { return simpleBuilder(conditions -> new CopyBlockEntityDataFunction(conditions, null)); }

    public static Builder<?> copyOnly(ResourceLocation... ids) {
        return simpleBuilder(conditions -> new CopyBlockEntityDataFunction(conditions, Set.of(ids)));
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<CopyBlockEntityDataFunction> {
        @Override
        public void serialize(JsonObject json, CopyBlockEntityDataFunction value, JsonSerializationContext context) {
            super.serialize(json, value, context);
            if (value.include != null) {
                var ids = new JsonArray();
                value.include.stream().sorted().forEach(id -> ids.add(id.toString()));
                json.add("include", ids);
            }
        }

        @Override
        public CopyBlockEntityDataFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            Set<ResourceLocation> include = null;
            if (json.has("include")) {
                include = new HashSet<>();
                for (var value : json.getAsJsonArray("include")) include.add(ResourceLocation.parse(value.getAsString()));
            }
            return new CopyBlockEntityDataFunction(conditions, include);
        }
    }
}

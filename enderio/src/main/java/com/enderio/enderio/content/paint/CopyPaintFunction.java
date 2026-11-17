package com.enderio.enderio.content.paint;

import com.enderio.enderio.content.paint.block.entity.PaintedBlockEntity;
import com.enderio.enderio.init.EIOLootModifiers;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public class CopyPaintFunction extends LootItemConditionalFunction {

    private final boolean shouldCopyPrimary;

    CopyPaintFunction(LootItemCondition[] conditions, boolean shouldCopyPrimary) {
        super(conditions);
        this.shouldCopyPrimary = shouldCopyPrimary;
    }

    @Override
    public LootItemFunctionType getType() {
        return EIOLootModifiers.COPY_PAINT.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        BlockEntity blockEntity = context.getParamOrNull(LootContextParams.BLOCK_ENTITY);

        if (blockEntity instanceof PaintedBlockEntity paintedBlockEntity) {
            Optional<Block> currentPaint = shouldCopyPrimary
                ? paintedBlockEntity.getPrimaryPaint()
                : paintedBlockEntity.getSecondaryPaint();

            currentPaint.ifPresent(paint -> BlockPaintData.ITEM_DATA.set(stack, BlockPaintData.of(paint)));
        }

        return stack;
    }

    public static Builder<?> copyPrimary() {
        return simpleBuilder((conditions) -> new CopyPaintFunction(conditions, true));
    }

    public static Builder<?> copySecondary() {
        return simpleBuilder((conditions) -> new CopyPaintFunction(conditions, false));
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<CopyPaintFunction> {
        @Override
        public void serialize(JsonObject json, CopyPaintFunction function,
                              JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("should_copy_primary", function.shouldCopyPrimary);
        }

        @Override
        public CopyPaintFunction deserialize(JsonObject json,
                JsonDeserializationContext context, LootItemCondition[] conditions) {
            var value = json.get("should_copy_primary");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new JsonSyntaxException("should_copy_primary must be a boolean");
            }
            return new CopyPaintFunction(conditions, value.getAsBoolean());
        }
    }
}

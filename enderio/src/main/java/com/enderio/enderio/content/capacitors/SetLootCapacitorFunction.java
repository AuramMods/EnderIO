package com.enderio.enderio.content.capacitors;

import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.init.EIOLootModifiers;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class SetLootCapacitorFunction extends LootItemConditionalFunction {

    private final NumberProvider range;

    SetLootCapacitorFunction(LootItemCondition[] conditions, NumberProvider range) {
        super(conditions);
        this.range = range;
    }

    @Override
    public LootItemFunctionType getType() {
        return EIOLootModifiers.SET_LOOT_CAPACITOR.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        float base = range.getFloat(context);
        Map<CapacitorModifier, Float> modifiers = new EnumMap<>(CapacitorModifier.class);

        var modifier = CapacitorModifier.getRandomModifier(context.getRandom());
        modifier.ifPresent(m -> modifiers.put(m, range.getFloat(context)));

        // 15% chance of a secondary modifier
        if (context.getRandom().nextFloat() < 0.15f) {
            modifier = CapacitorModifier.getRandomModifier(context.getRandom(), modifiers.keySet());
            modifier.ifPresent(m -> modifiers.put(m, range.getFloat(context)));
        }

        // 2% change of a third
        if (context.getRandom().nextFloat() < 0.02f) {
            modifier = CapacitorModifier.getRandomModifier(context.getRandom(), modifiers.keySet());
            modifier.ifPresent(m -> modifiers.put(m, range.getFloat(context)));
        }

        CapacitorData.ITEM_DATA.set(stack, new CapacitorData(base, modifiers));
        return stack;
    }

    public static Builder<?> setLootCapacitor(NumberProvider range) {
        return simpleBuilder((conditions) -> new SetLootCapacitorFunction(conditions, range));
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return range.getReferencedContextParams();
    }

    public static final class Serializer extends LootItemConditionalFunction.Serializer<SetLootCapacitorFunction> {
        @Override
        public void serialize(JsonObject json, SetLootCapacitorFunction function,
                              JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.add("range", context.serialize(function.range));
        }

        @Override
        public SetLootCapacitorFunction deserialize(JsonObject json,
                JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new SetLootCapacitorFunction(conditions,
                GsonHelper.getAsObject(json, "range", context, NumberProvider.class));
        }
    }
}

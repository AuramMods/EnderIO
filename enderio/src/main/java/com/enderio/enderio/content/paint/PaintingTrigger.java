package com.enderio.enderio.content.paint;

import com.enderio.enderio.api.EnderIOAPI;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;

public class PaintingTrigger extends SimpleCriterionTrigger<PaintingTrigger.TriggerInstance> {
    // The actual 1.21.1 registration and generated advancements use 'painting'.
    public static final ResourceLocation ID = EnderIOAPI.rl("painting");
    @Override public ResourceLocation getId() { return ID; }

    public void trigger(ServerPlayer player, Block paint) {
        super.trigger(player, instance -> instance.matches(paint));
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        var id = ResourceLocation.parse(GsonHelper.getAsString(json, "paint"));
        var paint = BuiltInRegistries.BLOCK.getOptional(id)
            .orElseThrow(() -> new JsonSyntaxException("Unknown paint block: " + id));
        return new TriggerInstance(player, paint);
    }

    public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
        private final Block paint;
        public TriggerInstance(ContextAwarePredicate player, Block paint) {
            super(ID, player);
            this.paint = paint;
        }
        public boolean matches(Block paint) { return this.paint == paint; }
        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            var json = super.serializeToJson(context);
            json.addProperty("paint", BuiltInRegistries.BLOCK.getKey(paint).toString());
            return json;
        }
        public static Criterion painted(Block paint) {
            return new Criterion(new TriggerInstance(ContextAwarePredicate.ANY, paint));
        }
    }
}

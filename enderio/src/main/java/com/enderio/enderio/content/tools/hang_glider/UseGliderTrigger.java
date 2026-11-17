package com.enderio.enderio.content.tools.hang_glider;

import com.enderio.enderio.api.EnderIOAPI;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class UseGliderTrigger extends SimpleCriterionTrigger<UseGliderTrigger.TriggerInstance> {
    public static final ResourceLocation ID = EnderIOAPI.rl("use_glider");
    @Override public ResourceLocation getId() { return ID; }
    public void trigger(ServerPlayer player) { super.trigger(player, instance -> true); }
    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new TriggerInstance(player);
    }
    public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
        public TriggerInstance(ContextAwarePredicate player) { super(ID, player); }
    }
}

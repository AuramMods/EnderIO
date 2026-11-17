package com.enderio.enderio.mixin;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.machines.alloy.SmeltingRecipeInheritance;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void enderio$inheritSmeltingRecipes(Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo ci) {
        SmeltingRecipeInheritance.inherit((RecipeManager) (Object) this,
            MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_VANILLA_ITEM_ENERGY.get());
    }
}

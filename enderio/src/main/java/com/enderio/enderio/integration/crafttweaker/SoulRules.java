package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.enderio.content.machines.powered_spawner.MobSpawnMode;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.enderio.foundation.souldata.SoulData;
import com.enderio.enderio.foundation.souldata.SoulDataReloadListener;
import com.enderio.enderio.foundation.souldata.SpawnerSoul;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Optional;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SoulRules")
public final class SoulRules {
    private SoulRules() {}

    @ZenCodeType.Method
    public static void setSpawner(EntityType<?> entity, int energy, String mode) {
        var spawnMode = FluidRecipeArguments.option(MobSpawnMode.CODEC, mode);
        var rule = new SpawnerSoul.SoulData(id(entity), positive(energy, "Spawner energy"), spawnMode);
        set(SpawnerSoul.NAME, SpawnerSoul.SPAWNER, rule);
    }

    @ZenCodeType.Method
    public static void setEngine(EntityType<?> entity, IFluidStack fuel, int powerPerMb, int ticksPerMb) {
        var fluid = FluidRecipeArguments.stack(fuel).getFluid();
        setEngine(entity, BuiltInRegistries.FLUID.getKey(fluid).toString(), powerPerMb, ticksPerMb);
    }

    @ZenCodeType.Method
    public static void setEngineTag(EntityType<?> entity, KnownTag<Fluid> fuel, int powerPerMb, int ticksPerMb) {
        setEngine(entity, "#" + fuel.id(), powerPerMb, ticksPerMb);
    }

    @ZenCodeType.Method
    public static void setFarming(EntityType<?> entity, float bonemeal, int seeds, float power) {
        if (!Float.isFinite(bonemeal) || bonemeal < 0 || seeds < 0 || !Float.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("Farming souls need nonnegative bonemeal and seeds and positive power");
        }
        set(FarmSoul.NAME, FarmSoul.FARM, new FarmSoul.SoulData(id(entity), bonemeal, seeds, power));
    }

    @ZenCodeType.Method
    public static void setSolar(EntityType<?> entity, boolean daytime, boolean nighttime,
            @ZenCodeType.Nullable ResourceLocation dimension) {
        var level = Optional.ofNullable(dimension).map(key -> ResourceKey.create(Registries.DIMENSION, key));
        set(SolarSoul.NAME, SolarSoul.SOLAR, new SolarSoul.SoulData(id(entity), daytime, nighttime, level));
    }

    @ZenCodeType.Method
    public static void remove(String domain, EntityType<?> entity) {
        var rules = rules(domain);
        var entityId = id(entity);
        CraftTweakerAPI.apply(new EnderIOAction("Removing Ender IO " + domain + " soul rule for " + entityId,
                () -> rules.remove(manager(), entityId)));
    }

    @ZenCodeType.Method
    @ZenCodeType.Nullable
    public static String getJson(String domain, EntityType<?> entity) {
        return rules(domain).encode(manager(), id(entity)).map(Object::toString).orElse(null);
    }

    private static void setEngine(EntityType<?> entity, String fuel, int powerPerMb, int ticksPerMb) {
        var rule = new EngineSoul.SoulData(id(entity), fuel, positive(powerPerMb, "FE per mB"),
                positive(ticksPerMb, "Ticks per mB"));
        set(EngineSoul.NAME, EngineSoul.ENGINE, rule);
    }

    private static int positive(int value, String parameter) {
        return ProcessingRecipeManager.positive(value, parameter);
    }

    private static ResourceLocation id(EntityType<?> entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity);
    }

    private static SoulDataReloadListener<?> rules(String domain) {
        var rules = SoulDataReloadListener.fromString(domain);
        if (rules == null) {
            throw new IllegalArgumentException("Unknown Ender IO soul rule domain " + domain);
        }
        return rules;
    }

    private static RecipeManager manager() {
        return CraftTweakerAPI.getAccessibleElementsProvider().recipeManager();
    }

    private static <T extends SoulData> void set(String domain, SoulDataReloadListener<T> rules, T value) {
        CraftTweakerAPI.apply(new EnderIOAction("Setting Ender IO " + domain + " soul rule for " + value.getKey(),
                () -> rules.set(manager(), value)));
    }
}

package com.enderio.core.gametest.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.*;
import mekanism.api.chemical.infuse.*;
import mekanism.api.chemical.pigment.*;
import mekanism.api.chemical.slurry.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

@Mod("endercore_mekanism_tests")
public final class MekanismTestMod {
    public MekanismTestMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        var gases = DeferredRegister.create(MekanismAPI.GAS_REGISTRY_NAME, "endercore_mekanism_tests");
        var infusions = DeferredRegister.create(MekanismAPI.INFUSE_TYPE_REGISTRY_NAME, "endercore_mekanism_tests");
        var pigments = DeferredRegister.create(MekanismAPI.PIGMENT_REGISTRY_NAME, "endercore_mekanism_tests");
        var slurries = DeferredRegister.create(MekanismAPI.SLURRY_REGISTRY_NAME, "endercore_mekanism_tests");
        gases.register("same", () -> new Gas(GasBuilder.builder()));
        infusions.register("same", () -> new InfuseType(InfuseTypeBuilder.builder()));
        pigments.register("same", () -> new Pigment(PigmentBuilder.builder()));
        slurries.register("same", () -> new Slurry(SlurryBuilder.clean()));
        gases.register(bus);
        infusions.register(bus);
        pigments.register(bus);
        slurries.register(bus);
    }
}

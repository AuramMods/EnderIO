package com.enderio.enderio.init;

import com.enderio.enderio.EnderIO;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class EIOSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, EnderIO.MOD_ID);

    public static final RegistryObject<SoundEvent> STIRLING = SOUNDS.register("stirling",
        () -> SoundEvent.createVariableRangeEvent(EnderIO.rl("block.stirling_generator.burn")));
    public static final RegistryObject<SoundEvent> SLICER = SOUNDS.register("slice_and_splice",
        () -> SoundEvent.createVariableRangeEvent(EnderIO.rl("block.slice_and_splice.slice")));
    public static final RegistryObject<SoundEvent> SAG_MILL = SOUNDS.register("sag_mill",
        () -> SoundEvent.createVariableRangeEvent(EnderIO.rl("block.sag_mill.tumble")));
    public static final RegistryObject<SoundEvent> SOUL_BINDER = SOUNDS.register("soul_binder",
        () -> SoundEvent.createVariableRangeEvent(EnderIO.rl("block.soul_binder.grind")));
    public static final RegistryObject<SoundEvent> VAT = SOUNDS.register("vat",
        () -> SoundEvent.createVariableRangeEvent(EnderIO.rl("block.vat.gurgle")));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }

}

package com.enderio.core.gametest;

import com.enderio.core.EnderCore;
import com.enderio.core.common.capability.ServerLevelState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class ServerLevelStateTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeLevelAttachmentIsLazyAndKeepsKeyIdentity(GameTestHelper helper) {
        var calls = new AtomicInteger();
        var first = new ServerLevelState<>(() -> { calls.incrementAndGet(); return new ArrayList<String>(); });
        var second = new ServerLevelState<>(ArrayList<String>::new);
        var level = helper.getLevel();
        helper.assertTrue(ServerLevelState.CAPABILITY.isRegistered(), "Forge did not register the capability");
        helper.assertTrue(first.getIfPresent(level) == null && calls.get() == 0, "Lookup eagerly created service");
        var value = first.get(level);
        value.add("retained");
        helper.assertTrue(first.getIfPresent(level) == value && first.get(level) == value && calls.get() == 1,
            "Repeated lookup lost state or invoked the factory again");
        helper.assertTrue(second.getIfPresent(level) == null && second.get(level) != value && second.get(level).isEmpty(),
            "Different keys shared a service");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualServerDimensionsOwnSeparateServices(GameTestHelper helper) {
        var key = new ServerLevelState<>(ArrayList<String>::new);
        var overworld = helper.getLevel().getServer().overworld();
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Native server did not create the Nether");
        var first = key.get(overworld);
        first.add("overworld only");
        helper.assertTrue(key.getIfPresent(nether) == null, "State leaked between dimensions before creation");
        var second = key.get(nether);
        helper.assertTrue(second != first && second.isEmpty() && key.get(overworld).size() == 1,
            "Native levels shared or replaced their state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void failedFactoryDoesNotInstallPartialState(GameTestHelper helper) {
        var attempts = new AtomicInteger();
        var value = new Object();
        var key = new ServerLevelState<>(() -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalArgumentException("fixture failure");
            return value;
        });
        boolean failed = false;
        try { key.get(helper.getLevel()); }
        catch (IllegalArgumentException expected) { failed = true; }
        helper.assertTrue(failed && key.getIfPresent(helper.getLevel()) == null, "Failed creation installed state");
        helper.assertTrue(key.get(helper.getLevel()) == value && attempts.get() == 2, "Factory could not recover");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeInvalidationClearsStateBeforeNotifyingConsumers(GameTestHelper helper) {
        // Dispatch an independent real attachment event: do not invalidate the shared test world.
        var event = new AttachCapabilitiesEvent<Level>(Level.class, helper.getLevel());
        MinecraftForge.EVENT_BUS.post(event);
        var provider = event.getCapabilities().get(EnderCore.loc("level_state"));
        helper.assertTrue(provider != null && !(provider instanceof ICapabilitySerializable<?>),
            "Missing transient provider or unexpected persistent serialization");
        helper.assertTrue(!provider.getCapability(ForgeCapabilities.ENERGY).isPresent(), "Provider claimed unrelated capability");
        var capability = provider.getCapability(ServerLevelState.CAPABILITY);
        var storage = capability.orElseThrow(() -> new AssertionError("Missing test capability"));
        var key = new ServerLevelState<>(Object::new);
        storage.get(key);
        var notifications = new AtomicInteger();
        capability.addListener(ignored -> {
            helper.assertTrue(storage.getIfPresent(key) == null, "Listener observed retained level state");
            notifications.incrementAndGet();
        });
        event.getListeners().forEach(Runnable::run);
        event.getListeners().forEach(Runnable::run);
        helper.assertTrue(!capability.isPresent() && notifications.get() == 1, "Invalidation did not retire capability once");
        boolean rejected = false;
        try { storage.get(key); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Retained invalidated storage recreated a service");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newAttachmentStartsEmptyAfterOldProviderRetires(GameTestHelper helper) {
        var key = new ServerLevelState<>(ArrayList<String>::new);
        var oldEvent = new AttachCapabilitiesEvent<Level>(Level.class, helper.getLevel());
        MinecraftForge.EVENT_BUS.post(oldEvent);
        var oldStorage = oldEvent.getCapabilities().get(EnderCore.loc("level_state"))
            .getCapability(ServerLevelState.CAPABILITY).orElseThrow(() -> new AssertionError("Missing old provider"));
        var oldValue = oldStorage.get(key);
        oldValue.add("old world state");
        oldEvent.getListeners().forEach(Runnable::run);
        var newEvent = new AttachCapabilitiesEvent<Level>(Level.class, helper.getLevel());
        MinecraftForge.EVENT_BUS.post(newEvent);
        var newStorage = newEvent.getCapabilities().get(EnderCore.loc("level_state"))
            .getCapability(ServerLevelState.CAPABILITY).orElseThrow(() -> new AssertionError("Missing new provider"));
        helper.assertTrue(newStorage.getIfPresent(key) == null && newStorage.get(key).isEmpty()
            && newStorage.get(key) != oldValue, "New provider reused retired state");
        helper.assertTrue(key.getIfPresent(helper.getLevel()) == null, "Fixture events modified the live world's provider");
        newEvent.getListeners().forEach(Runnable::run);
        helper.succeed();
    }
}

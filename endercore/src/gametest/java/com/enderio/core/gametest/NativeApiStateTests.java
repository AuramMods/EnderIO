package com.enderio.core.gametest;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.core.common.network.menu.SyncSlot.ChangeType;
import com.enderio.core.common.network.menu.payload.SlotPayload;
import com.enderio.core.common.serialization.ComponentCodecs;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.enderio.enderio.foundation.fluid.FluidStorageSyncSlot;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class NativeApiStateTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidCopiesKeepTagsIndependentAndDoNotReviveEmptyStacks(GameTestHelper helper) {
        var source = new FluidStack(Fluids.WATER, 700);
        source.getOrCreateTag().putString("variant", "original");
        var copy = EnderFluidUtil.copyWithAmount(source, 125);
        helper.assertTrue(copy.getAmount() == 125 && copy.getTag().getString("variant").equals("original"), "Copy lost volume or NBT");
        copy.getTag().putString("variant", "copy");
        helper.assertTrue(source.getAmount() == 700 && source.getTag().getString("variant").equals("original"), "Copy aliases source");
        helper.assertTrue(EnderFluidUtil.copyWithAmount(source, 0).isEmpty(), "Zero amount is not empty");
        helper.assertTrue(EnderFluidUtil.copyWithAmount(source, -1).isEmpty(), "Negative amount is not empty");
        helper.assertTrue(EnderFluidUtil.copyWithAmount(FluidStack.EMPTY, 1000).isEmpty(), "Empty fluid was revived");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tankSynchronizationTracksMutableAmountNbtCapacityAndEmptyState(GameTestHelper helper) {
        var server = FluidStorageSyncSlot.standalone();
        var client = FluidStorageSyncSlot.standalone();
        var water = new FluidStack(Fluids.WATER, 125);
        water.getOrCreateTag().putInt("variant", 1);
        server.set(new FluidStorageInfo(water, 1000));
        transfer(helper, server, client, ChangeType.FULL);
        helper.assertTrue(server.detectChanges() == ChangeType.NONE, "Unchanged tank generated traffic");
        water.setAmount(250);
        transfer(helper, server, client, ChangeType.PARTIAL);
        helper.assertTrue(client.get().contents().getTag().getInt("variant") == 1, "Amount delta erased NBT");
        water.getTag().putInt("variant", 2);
        transfer(helper, server, client, ChangeType.FULL);
        server.set(new FluidStorageInfo(water, 2000));
        transfer(helper, server, client, ChangeType.FULL);
        server.set(new FluidStorageInfo(new FluidStack(Fluids.LAVA, 50), 2000));
        transfer(helper, server, client, ChangeType.FULL);
        server.set(new FluidStorageInfo(FluidStack.EMPTY, 2000));
        transfer(helper, server, client, ChangeType.FULL);
        helper.assertTrue(server.detectChanges() == ChangeType.NONE, "Empty tank repeatedly changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tankValueEqualityIncludesFluidNbtQuantityAndCapacity(GameTestHelper helper) {
        var water = new FluidStack(Fluids.WATER, 100);
        water.getOrCreateTag().putInt("variant", 1);
        var value = new FluidStorageInfo(water, 1000);
        var same = new FluidStorageInfo(water.copy(), 1000);
        helper.assertTrue(value.equals(same) && value.hashCode() == same.hashCode(), "Equivalent fluid snapshots differ");
        helper.assertTrue(!value.equals(new FluidStorageInfo(EnderFluidUtil.copyWithAmount(water, 101), 1000)), "Amount ignored");
        helper.assertTrue(!value.equals(new FluidStorageInfo(water.copy(), 1001)), "Capacity ignored");
        same.contents().getTag().putInt("variant", 2);
        helper.assertTrue(!value.equals(same), "Fluid NBT ignored");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void structuredComponentsRetainTranslationStyleAndSiblings(GameTestHelper helper) {
        var component = Component.translatable("port.example", Component.literal("argument"), 42, "text")
            .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/help")))
            .append(Component.literal(" suffix").withStyle(ChatFormatting.ITALIC));
        var json = ComponentCodecs.COMPONENT.encodeStart(JsonOps.INSTANCE, component).result().orElseThrow();
        helper.assertTrue(json.isJsonObject() && json.getAsJsonObject().has("translate"), "Component flattened to plain text");
        var jsonCopy = ComponentCodecs.COMPONENT.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        helper.assertTrue(Component.Serializer.toJsonTree(component).equals(Component.Serializer.toJsonTree(jsonCopy)), "JSON lost component structure");
        var nbt = ComponentCodecs.COMPONENT.encodeStart(NbtOps.INSTANCE, component).result().orElseThrow();
        helper.assertTrue(nbt instanceof net.minecraft.nbt.StringTag, "NBT must use native component JSON strings");
        var nbtCopy = ComponentCodecs.COMPONENT.parse(NbtOps.INSTANCE, nbt).result().orElseThrow();
        helper.assertTrue(Component.Serializer.toJsonTree(component).equals(Component.Serializer.toJsonTree(nbtCopy)), "NBT lost component structure");
        var registryOps = net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        var registryNbt = ComponentCodecs.COMPONENT.encodeStart(registryOps, component).result().orElseThrow();
        var registryCopy = ComponentCodecs.COMPONENT.parse(registryOps, registryNbt).result().orElseThrow();
        helper.assertTrue(Component.Serializer.toJsonTree(component).equals(Component.Serializer.toJsonTree(registryCopy)), "Registry-wrapped NBT lost component structure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void componentCodecReportsNullAndMalformedDataAsErrors(GameTestHelper helper) {
        helper.assertTrue(ComponentCodecs.COMPONENT.parse(JsonOps.INSTANCE, JsonNull.INSTANCE).error().isPresent(), "Null accepted as a component");
        helper.assertTrue(ComponentCodecs.COMPONENT.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"unknown\":true}")).error().isPresent(), "Malformed component accepted");
        helper.succeed();
    }

    private static void transfer(GameTestHelper helper, FluidStorageSyncSlot server, FluidStorageSyncSlot client, ChangeType expected) {
        var actual = server.detectChanges();
        helper.assertTrue(actual == expected, "Wrong change type: expected " + expected + ", got " + actual);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SlotPayload.STREAM_CODEC.encode(buffer, server.createPayload(helper.getLevel(), actual));
            client.unpackPayload(helper.getLevel(), SlotPayload.STREAM_CODEC.decode(buffer));
            helper.assertTrue(!buffer.isReadable(), "Payload left unread bytes");
            helper.assertTrue(server.get().equals(client.get()), "Tank state diverged after native payload round trip");
        } finally {
            buffer.release();
        }
    }
}

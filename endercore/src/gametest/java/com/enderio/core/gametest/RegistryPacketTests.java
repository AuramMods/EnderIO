package com.enderio.core.gametest;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.RegistryPacketBuffer;
import com.enderio.enderio.api.attachment.CoordinateSelection;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RegistryPacketTests {
    private static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(id("packet_registry"));
    private static final ResourceKey<String> ENTRY = ResourceKey.create(REGISTRY, id("entry"));

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("endercore_tests", path); }

    private static MappedRegistry<String> registry(String value) {
        var registry = new MappedRegistry<String>(REGISTRY, Lifecycle.stable());
        Registry.register(registry, ENTRY, value);
        registry.freeze();
        return registry;
    }

    private static RegistryAccess access(Registry<String> registry) {
        return new RegistryAccess.ImmutableRegistryAccess(Map.of(REGISTRY, registry));
    }

    private static void rejects(GameTestHelper helper, Class<? extends RuntimeException> expected, Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) {
            helper.assertTrue(expected.isInstance(error), "Unexpected error: " + error);
            return;
        }
        helper.fail("Invalid packet was accepted");
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void holdersResolveAgainstReceivingRegistryAndRejectStaleContexts(GameTestHelper helper) {
        var first = registry("first-world");
        var second = registry("second-world");
        var raw = new FriendlyByteBuf(Unpooled.buffer());
        var sender = new RegistryPacketBuffer(raw, access(first));
        var receiver = new RegistryPacketBuffer(raw, access(second));
        PacketCodec<Holder<String>> codec = PacketCodecs.holderRegistry(REGISTRY);
        try {
            codec.encode(sender, first.getHolderOrThrow(ENTRY));
            Holder<String> received = codec.decode(receiver);
            helper.assertTrue(received == second.getHolderOrThrow(ENTRY) && received.value().equals("second-world"),
                "Packet retained a holder from another world/context");
            helper.assertTrue(raw.readableBytes() == 0, "Holder packet has trailing bytes");
            rejects(helper, EncoderException.class, () -> codec.encode(sender, received));
            rejects(helper, EncoderException.class, () -> codec.encode(sender, Holder.direct("first-world")));
            rejects(helper, EncoderException.class, () -> codec.encode(raw, first.getHolderOrThrow(ENTRY)));
            raw.clear();
            raw.writeResourceLocation(id("missing"));
            rejects(helper, DecoderException.class, () -> codec.decode(receiver));
            raw.clear();
            raw.writeResourceLocation(ENTRY.location());
            rejects(helper, DecoderException.class, () -> codec.decode(raw));
        } finally { raw.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeAndForgeRegistriesRejectDefaultFallbacks(GameTestHelper helper) {
        var nativeCodec = PacketCodecs.registry(BuiltInRegistries.ITEM);
        var forgeCodec = PacketCodecs.forgeRegistry(() -> ForgeRegistries.ITEMS);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            nativeCodec.encode(buffer, Items.ENDER_PEARL);
            helper.assertTrue(nativeCodec.decode(buffer) == Items.ENDER_PEARL, "Native item ID changed");
            forgeCodec.encode(buffer, Items.ENDER_PEARL);
            helper.assertTrue(forgeCodec.decode(buffer) == Items.ENDER_PEARL, "Forge item key changed");
            for (int invalid : new int[] {-1, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeVarInt(invalid);
                rejects(helper, DecoderException.class, () -> nativeCodec.decode(buffer));
            }
            buffer.clear();
            buffer.writeResourceLocation(id("missing_item"));
            rejects(helper, DecoderException.class, () -> forgeCodec.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(99);
            rejects(helper, DecoderException.class, () -> PacketCodecs.DYE_COLOR.decode(buffer));
            buffer.clear();
            buffer.writeVarInt(6);
            rejects(helper, DecoderException.class, () -> PacketCodecs.DIRECTION.decode(buffer));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeItemPacketsPreserveTypedStateAndRejectMissingRequiredNbt(GameTestHelper helper) {
        var stack = ItemDataTestContent.ITEM.toStack();
        var data = new CompoundTag();
        data.putInt("enderio:energy", 531);
        stack.getOrCreateTag().put("enderio:data", data);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PacketCodecs.ITEM_STACK.encode(buffer, stack);
            ItemStack received = PacketCodecs.ITEM_STACK.decode(buffer);
            helper.assertTrue(ItemStack.isSameItemSameTags(stack, received), "Native packet lost typed item state");
            received.getTag().getCompound("enderio:data").putInt("enderio:energy", 1);
            helper.assertTrue(stack.getTag().getCompound("enderio:data").getInt("enderio:energy") == 531, "Decoded item aliases source NBT");
            PacketCodecs.COMPOUND_TAG.optional().encode(buffer, java.util.Optional.empty());
            helper.assertTrue(PacketCodecs.COMPOUND_TAG.optional().decode(buffer).isEmpty(), "Optional NBT absence changed");
            buffer.clear();
            buffer.writeNbt(null);
            rejects(helper, DecoderException.class, () -> PacketCodecs.COMPOUND_TAG.decode(buffer));
            buffer.clear();
            PacketCodecs.ITEM_STACK.encode(buffer, ItemStack.EMPTY);
            rejects(helper, DecoderException.class, () -> PacketCodecs.REQUIRED_ITEM_STACK.decode(buffer));
            rejects(helper, EncoderException.class, () -> PacketCodecs.REQUIRED_ITEM_STACK.encode(buffer, ItemStack.EMPTY));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualCoordinateSelectionRetainsDimensionAndPosition(GameTestHelper helper) {
        var value = new CoordinateSelection(Level.NETHER, new BlockPos(-30_000_000, -64, 29_999_999));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CoordinateSelection.STREAM_CODEC.encode(buffer, value);
            helper.assertTrue(value.equals(CoordinateSelection.STREAM_CODEC.decode(buffer)) && buffer.readableBytes() == 0,
                "Main-mod travel coordinate packet changed dimension or position");
        } finally { buffer.release(); }
        helper.succeed();
    }
}

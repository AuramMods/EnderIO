package com.enderio.core.gametest;

import com.enderio.core.common.item.RegistryItemDataKey;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RegistryItemDataTests {
    private static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(ItemDataTestContent.id("item_registry"));
    private static final ResourceKey<String> ENTRY = ResourceKey.create(REGISTRY, ItemDataTestContent.id("entry"));
    private static final RegistryItemDataKey<String> KEY = new RegistryItemDataKey<>(ItemDataTestContent.id("entry_data"), REGISTRY);

    private static MappedRegistry<String> registry(String value) {
        var result = new MappedRegistry<String>(REGISTRY, Lifecycle.stable());
        Registry.register(result, ENTRY, value);
        result.freeze();
        return result;
    }
    private static RegistryAccess access(Registry<String> registry) { return new RegistryAccess.ImmutableRegistryAccess(Map.of(REGISTRY, registry)); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registryItemsRebindAfterCopySavePacketAndWorldChange(GameTestHelper helper) {
        var first = registry("first");
        var second = registry("second");
        var stack = ItemDataTestContent.ITEM.toStack();
        KEY.set(stack, first.getHolderOrThrow(ENTRY));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PacketCodecs.ITEM_STACK.encode(buffer, stack);
            ItemStack[] variants = {stack, stack.copy(), ItemStack.of(stack.save(new CompoundTag())), PacketCodecs.ITEM_STACK.decode(buffer)};
            for (var copy : variants) {
                helper.assertTrue(KEY.get(copy, access(first)) == first.getHolderOrThrow(ENTRY), "First context lookup failed");
                helper.assertTrue(KEY.get(copy, access(second)) == second.getHolderOrThrow(ENTRY), "Registry item retained a holder from another world");
                helper.assertTrue(KEY.getId(copy).equals(ENTRY.location()), "Stable ID changed during persistence/transport");
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unknownRegistryIdsSurviveAndInvalidHoldersDoNotMutateState(GameTestHelper helper) {
        var world = registry("value");
        var stack = ItemDataTestContent.ITEM.toStack();
        var missingId = ItemDataTestContent.id("unavailable_datapack_entry");
        KEY.setId(stack, missingId);
        var saved = stack.save(new CompoundTag());
        helper.assertTrue(KEY.get(stack, access(world)) == null && KEY.getId(stack).equals(missingId), "Unknown entry fell back or was discarded");
        try { KEY.set(stack, Holder.direct("inline")); helper.fail("Direct holder accepted"); } catch (IllegalArgumentException expected) { }
        var otherKey = ResourceKey.<String>createRegistryKey(ItemDataTestContent.id("different_registry"));
        var other = new MappedRegistry<String>(otherKey, Lifecycle.stable());
        var otherEntry = ResourceKey.create(otherKey, ENTRY.location());
        Registry.register(other, otherEntry, "wrong");
        other.freeze();
        try { KEY.set(stack, other.getHolderOrThrow(otherEntry)); helper.fail("Wrong registry accepted"); } catch (IllegalArgumentException expected) { }
        helper.assertTrue(saved.equals(stack.save(new CompoundTag())), "Rejected/unknown lookup mutated the item");
        KEY.remove(stack);
        helper.assertTrue(!KEY.has(stack) && KEY.get(stack, access(world)) == null, "Registry item removal failed");
        helper.succeed();
    }
}

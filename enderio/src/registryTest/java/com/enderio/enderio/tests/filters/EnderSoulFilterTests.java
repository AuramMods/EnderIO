package com.enderio.enderio.tests.filters;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.filters.soul.EnderSoulFilter;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnderSoulFilterTests {
    @Test
    public void legacyStackLoadPreservesFilterSlotsAndModes() {
        var saved = legacyStack("enderio:entity_filter", "minecraft:cow");
        var stack = ItemStack.of(saved);
        Assertions.assertTrue(stack.is(EIOItems.BASIC_SOUL_FILTER.get()));
        var restored = EIODataComponents.SOUL_FILTER.get(stack);
        Assertions.assertNotNull(restored);
        Assertions.assertEquals(5, restored.matches().size());
        Assertions.assertEquals(Soul.EMPTY, restored.matches().get(1));
        Assertions.assertEquals(Soul.of(EntityType.COW), restored.matches().get(4));
        Assertions.assertTrue(restored.isDenyList());
        Assertions.assertTrue(restored.shouldCompareTags());
        var taggedPig = restored.matches().get(0);
        Assertions.assertEquals("{\"text\":\"Marker\"}", taggedPig.entityTag().getString("CustomName"));
        Assertions.assertFalse(taggedPig.entityTag().contains("Motion"));
        Assertions.assertFalse(restored.test(taggedPig));
        Assertions.assertTrue(restored.test(Soul.of(EntityType.PIG)));
        Assertions.assertFalse(restored.test(Soul.of(EntityType.COW)));
        Assertions.assertTrue(restored.test(Soul.of(EntityType.SHEEP)));
        Assertions.assertFalse(stack.getTag().contains("EntityEntries"));
        Assertions.assertEquals(74, stack.getTag().getInt("PortFixture"));
        Assertions.assertEquals(restored, EIODataComponents.SOUL_FILTER.get(ItemStack.of(stack.save(new CompoundTag()))));
        Assertions.assertEquals(restored, EIODataComponents.SOUL_FILTER.get(
            ItemStack.of(legacyStack("enderio:basic_soul_filter", "minecraft:cow"))));
    }

    @Test
    public void unregisteredLegacyEntityLeavesTheOriginalFilterRecoverable() {
        var saved = legacyStack("enderio:entity_filter", "missing_mod:creature");
        var stack = ItemStack.of(saved);
        Assertions.assertTrue(stack.is(EIOItems.BASIC_SOUL_FILTER.get()));
        Assertions.assertNull(EIODataComponents.SOUL_FILTER.get(stack));
        Assertions.assertEquals(saved.getCompound("tag"), stack.getTag());
        Assertions.assertEquals(saved.getCompound("tag"), ItemStack.of(stack.save(new CompoundTag())).getTag());
    }

    private static CompoundTag legacyStack(String filterId, String lastEntityId) {
        var pig = new CompoundTag();
        pig.putString("id", "minecraft:pig");
        pig.putString("CustomName", "{\"text\":\"Marker\"}");
        pig.put("Motion", new ListTag());
        var last = new CompoundTag();
        last.putString("id", lastEntityId);
        var entries = new ListTag();
        for (var entity : java.util.List.of(pig, new CompoundTag(), new CompoundTag(), new CompoundTag(), last)) {
            var entry = new CompoundTag();
            entry.put("Entity", entity);
            entries.add(entry);
        }
        var tag = new CompoundTag();
        tag.put("EntityEntries", entries);
        tag.putBoolean("IsInverted", true);
        tag.putBoolean("IsNbt", true);
        tag.putInt("PortFixture", 74);
        var saved = new CompoundTag();
        saved.putString("id", filterId);
        saved.putByte("Count", (byte) 1);
        saved.put("tag", tag);
        return saved;
    }

    @Test
    public void testBasicAllowFilter() {
        var filter = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY), Soul.of(EntityType.SKELETON)), false, false);

        Assertions.assertTrue(filter.test(Soul.of(EntityType.ALLAY)));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.SKELETON)));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.COD)));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.SHEEP)));
    }

    @Test
    public void testBasicDenyFilter() {
        var filter = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY), Soul.of(EntityType.SKELETON)), true, false);

        Assertions.assertFalse(filter.test(Soul.of(EntityType.ALLAY)));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.SKELETON)));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.COD)));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.SHEEP)));
    }

    @Test
    public void testBasicAllowFilterWithComponentComparison() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Health", 20);
        var soulWithHealth = new Soul(EntityType.ALLAY, tag);
        var anotherSoulWithHealth = new Soul(EntityType.SKELETON, tag);

        var filter = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, soulWithHealth, anotherSoulWithHealth), false, true);

        Assertions.assertTrue(filter.test(soulWithHealth));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.ALLAY)));
        Assertions.assertTrue(filter.test(anotherSoulWithHealth));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.SKELETON)));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.COD)));
        Assertions.assertFalse(filter.test(Soul.of(EntityType.SHEEP)));
    }

    @Test
    public void testBasicDenyFilterWithComponentComparison() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Health", 20);
        var soulWithHealth = new Soul(EntityType.ALLAY, tag);
        var anotherSoulWithHealth = new Soul(EntityType.SKELETON, tag);

        var filter = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, soulWithHealth, anotherSoulWithHealth), true, true);

        Assertions.assertFalse(filter.test(soulWithHealth));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.ALLAY)));
        Assertions.assertFalse(filter.test(anotherSoulWithHealth));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.SKELETON)));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.COD)));
        Assertions.assertTrue(filter.test(Soul.of(EntityType.SHEEP)));
    }

    @Test
    public void testEqualsSameValues() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY), Soul.of(EntityType.SKELETON)), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY), Soul.of(EntityType.SKELETON)), false, false);

        Assertions.assertEquals(filter1, filter2);
        Assertions.assertEquals(filter1.hashCode(), filter2.hashCode());
    }

    @Test
    public void testEqualsSameInstance() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, false);

        Assertions.assertEquals(filter1, filter1);
        Assertions.assertEquals(filter1.hashCode(), filter1.hashCode());
    }

    @Test
    public void testNotEqualsDifferentMatches() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.SKELETON)), false, false);

        Assertions.assertNotEquals(filter1, filter2);
    }

    @Test
    public void testNotEqualsDifferentIsDenyList() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), true, false);

        Assertions.assertNotEquals(filter1, filter2);
    }

    @Test
    public void testNotEqualsDifferentShouldCompareTags() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, true);

        Assertions.assertNotEquals(filter1, filter2);
    }

    @Test
    public void testNotEqualsDifferentMatchCount() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY)), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY, Soul.of(EntityType.ALLAY), Soul.of(EntityType.SKELETON)), false, false);

        Assertions.assertNotEquals(filter1, filter2);
    }

    @Test
    public void testEmptyListEquals() {
        var filter1 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY), false, false);
        var filter2 = new EnderSoulFilter(NonNullList.of(Soul.EMPTY), false, false);

        Assertions.assertEquals(filter1, filter2);
        Assertions.assertEquals(filter1.hashCode(), filter2.hashCode());
    }
}

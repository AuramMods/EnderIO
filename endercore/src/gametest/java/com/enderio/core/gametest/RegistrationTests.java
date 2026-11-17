package com.enderio.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import static com.enderio.core.gametest.ItemDataTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RegistrationTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forgeFactoriesResolveExactlyOnceWithItemForms(GameTestHelper helper) {
        helper.assertTrue(itemFactoryCalls == 1 && blockFactoryCalls == 1, "Deferred factories did not run exactly once");
        helper.assertTrue(ITEM.getId().equals(factoryId), "Factory got a different registry ID");
        helper.assertTrue(ForgeRegistries.ITEMS.getValue(ITEM.getId()) == ITEM.get(), "Item handle did not resolve registered item");
        helper.assertTrue(ForgeRegistries.BLOCKS.getValue(BLOCK.getId()) == BLOCK.get(), "Block handle did not resolve registered block");
        helper.assertTrue(BLOCK_ITEM.get() instanceof BlockItem item && item.getBlock() == BLOCK.get(), "Block item resolved too early or to wrong block");
        helper.assertTrue(BLOCK.asItem() == BLOCK_ITEM.get() && BLOCK.toStack(4).getCount() == 4, "Block item form or stack count changed");
        helper.assertTrue(ITEM.toStack(5).getCount() == 5 && ITEM.get().getMaxStackSize() == 13, "Item properties/count were lost");
        helper.assertTrue(BUILDER_ITEM.get().getMaxStackSize() == 7, "Builder properties were lost");
        helper.assertTrue(ITEMS.getEntries().stream().anyMatch(entry -> entry.get() == ITEM.get()), "Enumeration omitted registered item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registryAliasesLoadOldItemIds(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.BLOCKS.getValue(id("old_data_fixture")) == BLOCK.get(), "Old block ID did not resolve");
        helper.assertTrue(ForgeRegistries.ITEMS.getValue(id("old_data_fixture")) == BLOCK_ITEM.get(), "Old block item ID did not resolve");
        var saved = new CompoundTag();
        saved.putString("id", id("old_data_item").toString());
        saved.putByte("Count", (byte) 3);
        var stack = ItemStack.of(saved);
        helper.assertTrue(stack.is(ITEM.get()) && stack.getCount() == 3, "Native item loading did not honor the old ID");
        helper.assertTrue(stack.save(new CompoundTag()).getString("id").equals(ITEM.getId().toString()), "Re-save did not use canonical ID");
        helper.succeed();
    }
}

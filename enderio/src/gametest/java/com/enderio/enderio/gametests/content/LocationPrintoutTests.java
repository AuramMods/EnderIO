package com.enderio.enderio.gametests.content;

import com.enderio.enderio.api.attachment.CoordinateSelection;
import com.enderio.enderio.content.tools.coordinate_selector.LocationPrintoutItem;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class LocationPrintoutTests {
    @GameTest
    public static void forge6PrintoutMigrationKeepsExactData(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var fixture = new ItemStack(EIOItems.LOCATION_PRINTOUT.get());
            var historical = new CoordinateSelection(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("example:missing_dimension")), new BlockPos(-1234, -48, 876));
            var oldData = new CompoundTag();
            oldData.putString("Level", historical.level().location().toString());
            oldData.put("BlockPos", NbtUtils.writeBlockPos(historical.pos()));
            fixture.addTagElement("CoordinateSelection", oldData);
            fixture.setHoverName(Component.literal("Archive"));
            fixture.getOrCreateTag().putInt("Marker", 27);

            var converted = ItemStack.of(fixture.save(new CompoundTag()));
            helper.assertTrue(LocationPrintoutItem.getSelection(converted).filter(historical::equals).isPresent(),
                "native load must keep the historical coordinates and unregistered dimension key");
            helper.assertTrue(!converted.getTag().contains("CoordinateSelection")
                && converted.getTag().getInt("Marker") == 27 && converted.getHoverName().getString().equals("Archive"),
                "only the translated selection tag should be replaced");

            var modern = new CoordinateSelection(Level.NETHER, new BlockPos(4, 55, -13));
            LocationPrintoutItem.setSelection(converted, modern);
            converted.addTagElement("CoordinateSelection", oldData);
            var reopened = ItemStack.of(converted.save(new CompoundTag()));
            helper.assertTrue(LocationPrintoutItem.getSelection(reopened).filter(modern::equals).isPresent()
                && !reopened.getTag().contains("CoordinateSelection"), "stale historical data cannot override a modern selection");

            var incomplete = oldData.copy();
            incomplete.getCompound("BlockPos").remove("Z");
            fixture.addTagElement("CoordinateSelection", incomplete);
            var original = ItemStack.of(fixture.save(new CompoundTag()));
            helper.assertTrue(LocationPrintoutItem.getSelection(original).isEmpty()
                && incomplete.equals(original.getTagElement("CoordinateSelection")),
                "incomplete data stays intact without inventing a coordinate");
            helper.startSequence().thenSucceed();
        });
    }
}

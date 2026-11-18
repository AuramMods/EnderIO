package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.machines.obelisks.xp.XPObeliskBlockEntity;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import java.util.UUID;

public class XPObeliskTests {
    @GameTest
    public static void partialWithdrawalPreservesSubPointFluid(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ObeliskXPTest"));
            var machine = helper.getBlockEntity(0, 1, 0, XPObeliskBlockEntity.class);
            machine.getFluidTank().setFluid(new FluidStack(EIOFluids.XP_JUICE.source().get(), 27));
            player.giveExperiencePoints(5);
            machine.addLevelsToPlayer(player, 1);
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 7, "keep the seven millibucket remainder");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 6L, "withdraw one whole point");
            machine.removeAllXpFromPlayer(player);
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 127, "the player and tank conserve all XP");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 0L, "deposit the remaining partial level");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void nearCapacityDepositUsesWholePointsAndRetainsTaggedFluid(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ObeliskXPTest"));
            var machine = helper.getBlockEntity(0, 1, 0, XPObeliskBlockEntity.class);
            var tag = new CompoundTag();
            tag.putString("PortFixture", "keep");
            int capacity = machine.getFluidTank().getCapacity();
            machine.getFluidTank().setFluid(new FluidStack(EIOFluids.XP_JUICE.source().get(), capacity - 27, tag));
            player.giveExperiencePoints(5);
            machine.removeAllXpFromPlayer(player);
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), capacity - 7, "only one whole point fits");
            helper.assertValueEqual(machine.getFluidTank().getFluid().getTag(), tag, "keep the installed fluid tag");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 4L, "charge the player for the accepted point");
            machine.removeAllXpFromPlayer(player);
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), capacity - 7, "cannot create a partial point");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 4L, "leave the player unchanged when full");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void roundTripTenLevelsFromAnExactThreshold(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ObeliskXPTest"));
            var machine = helper.getBlockEntity(0, 1, 0, XPObeliskBlockEntity.class);
            player.experienceLevel = 12;
            player.totalExperience = 216;
            machine.removeLevelsFromPlayer(player, 10);
            helper.assertValueEqual(player.experienceLevel, 2, "store ten exact levels");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 16L, "exact level-two experience");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 4000, "deposit two hundred points");
            machine.addLevelsToPlayer(player, 10);
            helper.assertValueEqual(player.experienceLevel, 12, "withdraw ten exact levels");
            helper.assertValueEqual(ExperienceUtil.getPlayerTotalXp(player), 216L, "exact level-twelve experience");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 0, "withdraw two hundred points");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void carriedForgeSixObeliskPromotesItsPortableState(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var xp = EIOFluids.XP_JUICE.source().get();
            var fluidTag = new CompoundTag();
            fluidTag.putInt("PortFixture", 72);
            var forgeTank = new FluidStack(xp, 1573, fluidTag).writeToNBT(new CompoundTag());
            forgeTank.putString("FluidName", "enderio:xp_juice");
            var tanks = new ListTag();
            tanks.add(forgeTank);
            var storage = new CompoundTag();
            storage.put("Tanks", tanks);
            var io = new CompoundTag();
            io.putByte("2", (byte) 4);
            var legacy = new CompoundTag();
            legacy.put("Fluid", storage);
            legacy.put("IoConfig", io);
            legacy.putInt("RedstoneControl", 3);
            legacy.putString("PortFixturePayload", "keep");
            var stack = new ItemStack(EIOBlocks.XP_OBELISK.asItem());
            stack.addTagElement(BlockItem.BLOCK_ENTITY_TAG, legacy);

            var restored = ItemStack.of(stack.save(new CompoundTag()));
            var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(restored);
            helper.assertTrue(fluid != null && fluid.isFluidStackIdentical(new FluidStack(xp, 1573, fluidTag)),
                "encoded item fluid is applied after native placement");
            helper.assertValueEqual(IOConfig.ITEM_DATA.get(restored).getMode(Direction.NORTH), IOMode.DISABLED,
                "legacy disabled face");
            helper.assertValueEqual(RedstoneControl.ITEM_DATA.get(restored), RedstoneControl.NEVER_ACTIVE, "legacy redstone mode");
            var remainder = BlockItem.getBlockEntityData(restored);
            helper.assertTrue(remainder != null && !remainder.contains("Fluid")
                && remainder.getString("PortFixturePayload").equals("keep"), "retain unrelated item data");
            helper.startSequence().thenSucceed();
        });
    }

    private static void template(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.XP_OBELISK.get().defaultBlockState()));
    }
}

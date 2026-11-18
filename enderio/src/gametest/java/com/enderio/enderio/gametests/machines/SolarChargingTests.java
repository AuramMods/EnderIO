package com.enderio.enderio.gametests.machines;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlockEntity;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerBlockEntity;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.HashMap;

public class SolarChargingTests {
    @GameTest
    public static void solarOverflowAndSplitPreserveExactEnergy(GameplayTest test) {
        var panel = EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.ENERGETIC).get();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 1, 1)
            .set(0, 0, 0, panel.defaultBlockState())
            .set(1, 0, 0, panel.defaultBlockState())
            .set(2, 0, 0, panel.defaultBlockState())
            .set(3, 0, 0, panel.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> {
                var source = helper.getBlockEntity(0, 1, 0, SolarPanelBlockEntity.class);
                helper.setBlock(0, 2, 0, EIOFluids.LIQUID_SUNSHINE.block().get());
                setEnergy(source, 4000);
                var bridge = helper.getBlockEntity(1, 1, 0, SolarPanelBlockEntity.class);
                setEnergy(bridge, 3998);
                setEnergy(helper.getBlockEntity(2, 1, 0, SolarPanelBlockEntity.class), 0);
                setEnergy(helper.getBlockEntity(3, 1, 0, SolarPanelBlockEntity.class), 0);
                var sourceEnergy = source.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
                var bridgeCapability = bridge.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN);
                var bridgeEnergy = bridgeCapability.orElseThrow(AssertionError::new);
                helper.assertTrue(!source.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent(), "panel top has no power output");
                helper.assertValueEqual(sourceEnergy.getMaxEnergyStored(), 16000, "connected array total capacity");
                source.serverTick();
                helper.assertValueEqual(sourceEnergy.getEnergyStored(), 8002, "one panel adds exactly four FE to the array");
                int bridgeCharge = bridge.saveWithoutMetadata().getInt(MachineNBTKeys.ENERGY_STORED);

                helper.setBlock(1, 1, 0, Blocks.AIR);
                helper.assertTrue(!bridgeCapability.isPresent() && !bridgeEnergy.canExtract(), "removed panel invalidates output and retained handler");
                helper.assertValueEqual(bridgeEnergy.getEnergyStored(), 0, "removed panel exposes no charge");
                helper.assertValueEqual(bridgeEnergy.extractEnergy(100, false), 0, "removed panel cannot debit a live fragment");
                var far = helper.getBlockEntity(3, 1, 0, SolarPanelBlockEntity.class)
                    .getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).orElseThrow(AssertionError::new);
                helper.assertValueEqual(sourceEnergy.getMaxEnergyStored(), 4000, "source separated from far fragment");
                helper.assertValueEqual(far.getMaxEnergyStored(), 8000, "two-node fragment stays connected");
                helper.assertValueEqual(sourceEnergy.getEnergyStored() + far.getEnergyStored(), 8002 - bridgeCharge, "split retains both balances");

                helper.setBlock(1, 1, 0, panel);
                helper.assertValueEqual(sourceEnergy.getMaxEnergyStored(), 16000, "replacement rejoins both fragments");
                helper.assertValueEqual(sourceEnergy.getEnergyStored(), 8002 - bridgeCharge, "rejoining does not credit extra energy");

                helper.setBlock(0, 2, 0, Blocks.AIR);
                helper.setBlock(1, 1, 0, Blocks.AIR);
                var farEnd = helper.getBlockEntity(3, 1, 0, SolarPanelBlockEntity.class);
                int leftBalance = sourceEnergy.getEnergyStored();
                int rightBalance = far.getEnergyStored();
                helper.setBlock(0, 1, 1, EIOBlocks.ALLOY_SMELTER.get());
                helper.setBlock(3, 1, 1, EIOBlocks.ALLOY_SMELTER.get());
                for (int x : new int[]{0, 3}) {
                    helper.insertIntoContainer(x, 1, 1, EIOItems.BASIC_CAPACITOR.get(), 1);
                    helper.changeIoConfig(x, 1, 1, config -> config.setIOMode(Direction.NORTH, IOMode.PULL));
                }
                var level = helper.getLevel();
                long time = level.getDayTime();
                try {
                    level.setDayTime(18000);
                    source.serverTick();
                    helper.getBlockEntity(2, 1, 0, SolarPanelBlockEntity.class).serverTick();
                    farEnd.serverTick();
                } finally {
                    level.setDayTime(time);
                }
                helper.assertValueEqual(helper.getBlockEntity(0, 1, 1, PoweredMachineBlockEntity.class)
                    .getEnergyStorage().getEnergyStored(), leftBalance, "left fragment still pushes after splitting");
                helper.assertValueEqual(helper.getBlockEntity(3, 1, 1, PoweredMachineBlockEntity.class)
                    .getEnergyStorage().getEnergyStored(), rightBalance, "right fragment still pushes after splitting");
            })
            .thenSucceed());
    }

    @GameTest
    public static void phantomSolarUsesCurrentRuleAfterLoadAndUnbind(GameplayTest test) {
        var panel = EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1).set(0, 0, 0, panel.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> {
                var level = helper.getLevel();
                var solar = helper.getBlockEntity(0, 1, 0, SolarPanelBlockEntity.class);
                var phantom = Soul.of(EntityType.PHANTOM);
                var originalData = SolarSoul.SOLAR.map;
                long originalTime = level.getDayTime();
                try {
                    helper.setBlock(0, 2, 0, EIOFluids.LIQUID_DARKNESS.block().get());
                    solar.bindSoul(phantom);
                    var saved = solar.saveWithoutMetadata();
                    solar.bindSoul(Soul.EMPTY);
                    level.setDayTime(18000);
                    helper.assertValueEqual(solar.getGenerationRate(), 0, "unbound panel does not generate at midnight");
                    solar.load(saved);
                    helper.assertTrue(Soul.isSameEntity(solar.getBoundSoul(), phantom), "phantom restored on disk load");
                    helper.assertValueEqual(solar.getGenerationRate(), 64, "phantom restores night production immediately");

                    SolarSoul.SOLAR.map = new HashMap<>(originalData);
                    SolarSoul.SOLAR.map.remove(phantom.entityTypeId());
                    helper.assertValueEqual(solar.getGenerationRate(), 0, "removed soul rule clears night production");
                    SolarSoul.SOLAR.map = originalData;
                    helper.assertValueEqual(solar.getGenerationRate(), 64, "restored rule takes effect on the loaded panel");
                    level.setDayTime(6000);
                    helper.setBlock(0, 2, 0, EIOFluids.LIQUID_SUNSHINE.block().get());
                    helper.assertValueEqual(solar.getGenerationRate(), 0, "phantom does not produce at noon");
                    solar.bindSoul(Soul.EMPTY);
                    helper.assertValueEqual(solar.getGenerationRate(), 64, "unbinding restores daylight generation");
                } finally {
                    SolarSoul.SOLAR.map = originalData;
                    level.setDayTime(originalTime);
                }
            })
            .thenSucceed());
    }

    @GameTest
    public static void wiredCanFinishAndReleaseWithItsLastFe(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.WIRED_CHARGER.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var charger = helper.getBlockEntity(0, 1, 0, WiredChargerBlockEntity.class);
            var storage = charger.getEnergyStorage();
            var inventory = charger.getInventory();
            var staff = new ItemStack(EIOItems.TRAVEL_STAFF.get());
            var output = WiredChargerBlockEntity.ITEM_CHARGED;
            int capacity = ItemStackEnergy.getMaxEnergyStored(staff);
            helper.startSequence()
                .thenExecute(() -> {
                    helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                    ItemStackEnergy.set(staff, capacity - 60);
                    staff.getOrCreateTag().putInt("PortSolar", 59);
                    storage.setEnergyStored(60);
                    helper.insertIntoContainer(0, 1, 0, staff);
                })
                .thenWaitUntil(() -> helper.assertTrue(!output.getItemStack(inventory).isEmpty(), "fully charged staff moved to the output"))
                .thenExecute(() -> {
                    helper.assertValueEqual(storage.getEnergyStored(), 0, "staff received the final 60 FE");
                    helper.assertValueEqual(ItemStackEnergy.getEnergyStored(output.getItemStack(inventory)), capacity, "staff charge");
                    helper.assertValueEqual(output.getItemStack(inventory).getOrCreateTag().getInt("PortSolar"), 59, "staff tag preserved");
                    helper.assertTrue(WiredChargerBlockEntity.ITEM_TO_CHARGE.getItemStack(inventory).isEmpty(), "only one staff remains");
                    var charged = output.extractItem(inventory, 1, false);
                    helper.insertIntoContainer(0, 1, 0, charged);
                })
                .thenWaitUntil(() -> helper.assertTrue(!output.getItemStack(inventory).isEmpty(), "already-full item passes with an empty buffer"))
                .thenSucceed();
        });
    }

    private static void setEnergy(SolarPanelBlockEntity solar, int amount) {
        var tag = solar.saveWithoutMetadata();
        tag.putInt(MachineNBTKeys.ENERGY_STORED, amount);
        solar.load(tag);
    }
}

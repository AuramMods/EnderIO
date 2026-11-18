package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class WeatherObeliskTests {
    @GameTest
    public static void savedFluidAndRocketLoadBeforeLevelIsAssigned(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.WEATHER_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var running = helper.getBlockEntity(0, 1, 0, WeatherObeliskBlockEntity.class);
            var seed = EIOFluids.CLOUD_SEED.source().get();
            running.getFluidHandler().fill(new FluidStack(seed, 1237), FluidAction.EXECUTE);
            helper.insertIntoContainer(0, 1, 0, Items.FIREWORK_ROCKET, 2);

            var reloaded = new WeatherObeliskBlockEntity(running.getBlockPos(), running.getBlockState());
            reloaded.load(running.saveWithoutMetadata());
            helper.assertValueEqual(reloaded.getTank().getFluid().getFluid(), seed, "saved recipe fluid");
            helper.assertValueEqual(reloaded.getTank().getFluidAmount(), 1237, "saved fluid amount");
            helper.assertValueEqual(WeatherObeliskBlockEntity.ROCKET.getItemStack(reloaded.getInventory()).getCount(), 2,
                "saved rocket count");
            helper.startSequence().thenSucceed();
        });
    }
}

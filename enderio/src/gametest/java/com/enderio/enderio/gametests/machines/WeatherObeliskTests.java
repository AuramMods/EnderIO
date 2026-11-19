package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class WeatherObeliskTests {
    @GameTest
    public static void weatherConsumesTheMatchedTaggedFluidAlongWithOneRocket(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.WEATHER_OBELISK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, WeatherObeliskBlockEntity.class);
            var reagent = new FluidStack(EIOFluids.CLOUD_SEED.source().get(), 1237);
            reagent.getOrCreateTag().putInt("PortFixture", 77);
            machine.getFluidHandler().fill(reagent, FluidAction.EXECUTE);

            var saved = machine.saveWithoutMetadata();
            var task = new CompoundTag();
            task.putString("RecipeId", "enderio:rain");
            task.putInt("ProgressMade", 600);
            task.putInt("ProgressRequired", 600);
            saved.put("Task", task);
            machine.load(saved);

            var level = helper.getLevel();
            var weather = (ServerLevelData) level.getLevelData();
            int clearTime = weather.getClearWeatherTime(), rainTime = weather.getRainTime(), thunderTime = weather.getThunderTime();
            boolean raining = weather.isRaining(), thundering = weather.isThundering();
            var bounds = new AABB(machine.getBlockPos()).inflate(2);
            try {
                machine.serverTick();
                helper.assertValueEqual(machine.getTank().getFluidAmount(), 1237, "the job waits for a missing rocket");
                helper.assertTrue(level.getEntitiesOfClass(FireworkRocketEntity.class, bounds).isEmpty(),
                    "a job missing its rocket cannot launch a firework");
                helper.assertTrue(weather.isRaining() == raining, "a job missing its rocket cannot change the weather");

                helper.insertIntoContainer(0, 1, 0, Items.FIREWORK_ROCKET, 2);
                machine.serverTick();
                helper.assertValueEqual(WeatherObeliskBlockEntity.ROCKET.getItemStack(machine.getInventory()).getCount(), 1,
                    "a completed job consumes one rocket");
                helper.assertValueEqual(machine.getTank().getFluidAmount(), 237,
                    "a completed job consumes the required bucket of tagged fluid");
                helper.assertValueEqual(machine.getTank().getFluid().getTag().getInt("PortFixture"), 77, "remaining fluid keeps its tag");
                helper.assertTrue(weather.isRaining(), "successful payment changes the weather");
                helper.assertValueEqual(level.getEntitiesOfClass(FireworkRocketEntity.class, bounds).size(), 1, "one firework launches");
            } finally {
                weather.setClearWeatherTime(clearTime);
                weather.setRainTime(rainTime);
                weather.setThunderTime(thunderTime);
                weather.setRaining(raining);
                weather.setThundering(thundering);
                level.getEntitiesOfClass(FireworkRocketEntity.class, bounds).forEach(Entity::discard);
            }
            helper.startSequence().thenSucceed();
        });
    }

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

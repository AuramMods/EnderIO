package com.enderio.enderio.content.machines.obelisks.weather;

import com.enderio.enderio.integration.gamestages.StageFeature;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.foundation.task.CraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.List;

public class WeatherObeliskBlockEntity extends MachineBlockEntity implements FluidTankUser {

    @Override
    public StageFeature automationStageFeature() {
        return StageFeature.OBELISK;
    }


    public static final ItemStack FIREWORK = new ItemStack(Items.FIREWORK_ROCKET, 1);
    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();
    public static final SingleSlotAccess ROCKET = new SingleSlotAccess();
    public static final int TANK_CAPACITY = 3000;
    private final CraftingMachineTaskHost<WeatherChangeRecipe, WeatherChangeRecipe.Input> craftingTaskHost;

    public WeatherObeliskBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.WEATHER_OBELISK.get(), worldPosition, blockState, false);
        fluidHandler = createFluidHandler();

        craftingTaskHost = new CraftingMachineTaskHost<>(this, this::canAcceptTask,
                EIORecipes.WEATHER_CHANGE.type().get(), this::createTask, this::createRecipeInput) {

            @Override
            protected boolean shouldStartNewTask() {
                if (ROCKET.getItemStack(getInventory()).isEmpty()) {
                    return true;
                }
                return super.shouldStartNewTask();
            }
        };
    }

    private WeatherChangeRecipe.Input createRecipeInput() {
        return new WeatherChangeRecipe.Input(TANK.getFluid(getFluidHandler()));
    }

    private CraftingMachineTask<WeatherChangeRecipe, WeatherChangeRecipe.Input> createTask(Level level,
            WeatherChangeRecipe.Input input,
            @Nullable WeatherChangeRecipe recipe) {
        return new CraftingMachineTask<>(level, getInventory(), input, null, recipe) {

            @Override
            protected void consumeInputs(WeatherChangeRecipe recipe) {
                // Weather recipes match the fluid type regardless of the stored stack's tag.
                TANK.drain(WeatherObeliskBlockEntity.this, recipe.fluid().getAmount(), IFluidHandler.FluidAction.EXECUTE);
                ROCKET.getItemStack(getInventory()).shrink(1);
                if (level instanceof ServerLevel server) applyWeather(server, recipe.mode());
            }

            @Override
            protected int makeProgress(int remainingProgress) {
                var recipe = getRecipe();
                if (!hasInputs(recipe) || !level.canSeeSky(getBlockPos().above())) return 0;
                boolean weatherDifferent = switch (recipe.mode()) {
                case RAIN -> !level.isRaining();
                case CLEAR -> level.isRaining() || level.isThundering();
                case LIGHTNING -> !level.isThundering();
                };
                if (!weatherDifferent) return 0;
                level.blockEntityChanged(worldPosition);
                return 1;
            }

            @Override
            protected int getProgressRequired(WeatherChangeRecipe recipe) {
                return 600;
            }

            @Override
            protected boolean placeOutputs(List<OutputStack> outputs, boolean simulate) {
                return level instanceof ServerLevel && hasInputs(getRecipe());
            }
        };
    }

    private boolean hasInputs(@Nullable WeatherChangeRecipe recipe) {
        return recipe != null && !ROCKET.getItemStack(getInventory()).isEmpty() && recipe.matches(createRecipeInput(), level);
    }

    private void applyWeather(ServerLevel server, WeatherChangeRecipe.WeatherMode mode) {
        switch (mode) {
        case RAIN -> server.setWeatherParameters(0, ServerLevel.RAIN_DURATION.sample(server.getRandom()), true, false);
        case CLEAR -> server.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(server.getRandom()), 0, false, false);
        case LIGHTNING -> server.setWeatherParameters(0, ServerLevel.THUNDER_DURATION.sample(server.getRandom()), true, true);
        }
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH);
        if (month == Calendar.JUNE) {
            FIREWORK.getOrCreateTag().put("Fireworks", WeatherChangeRecipe.WeatherMode.SURPRISE.copy());
        } else if (month == Calendar.MARCH && calendar.get(Calendar.DAY_OF_MONTH) == 31) {
            FIREWORK.getOrCreateTag().put("Fireworks", WeatherChangeRecipe.WeatherMode.SURPRISE_2.copy());
        } else {
            FIREWORK.getOrCreateTag().put("Fireworks", mode.getFireworks());
        }
        server.addFreshEntity(new FireworkRocketEntity(server, null, getBlockPos().getX() + 0.5,
                getBlockPos().getY() + 1.1, getBlockPos().getZ() + 0.5, FIREWORK));
    }

    private Boolean canAcceptTask() {
        return !isRedstoneBlocked();
    }

    @Override
    public void serverTick() {
        super.serverTick();

        if (canAct()) {
            craftingTaskHost.tick();
        }
        updateMachineState(MachineState.ACTIVE, isActive());
    }

    @Override
    public boolean isActive() {
        return canAct() && craftingTaskHost.hasTask();
    }

    @Override
    public MachineTankLayout getTankLayout() {
        return MachineTankLayout.builder().tank(TANK, TANK_CAPACITY).build();
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public MachineFluidTank getTank() {
        return TANK.getTank(getFluidHandler());
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                craftingTaskHost.newTaskAvailable();
                // Forge can deserialize the tank before assigning the level.
                if (level == null || level.isClientSide) return;
                setChanged();
                updateMachineState(MachineState.EMPTY_TANK, TANK.isEmpty(this));
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        };
    }

    @Override
    protected @Nullable MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .storageSlot((i, s) -> s.is(Items.FIREWORK_ROCKET))
                .slotAccess(ROCKET)
                .build();
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        craftingTaskHost.newTaskAvailable();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        craftingTaskHost.onLevelReady();
        if (level != null && !level.isClientSide) {
            updateMachineState(MachineState.EMPTY_TANK, TANK.isEmpty(this));
        }
    }

    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.BOTH);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WeatherObeliskMenu(containerId, playerInventory, this);
    }

    public float getCraftingProgress() {
        return craftingTaskHost.getProgress();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        saveTank(lookupProvider, tag);
        craftingTaskHost.save(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);
        craftingTaskHost.load(lookupProvider, tag);
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        FluidStack storedFluid = EIODataComponents.ITEM_FLUID_CONTENT.get(components);
        if (storedFluid != null) {
            var tank = TANK.getTank(this);
            tank.setFluid(storedFluid.copy());
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        var tank = TANK.getTank(this);
        if (!tank.isEmpty()) {
            EIODataComponents.ITEM_FLUID_CONTENT.set(components, tank.getFluid().copy());
        }
    }
}

package com.enderio.enderio.content.machines.soul_binder;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.client.SoundHandler;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.recipe.MachineRecipeCaches;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.foundation.task.PoweredCraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import com.enderio.enderio.init.EIOSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SoulBinderBlockEntity extends PoweredMachineBlockEntity implements FluidTankUser {

    public static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.SOUL_BINDER_CAPACITY);
    public static final QuadraticScalable USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.SOUL_BINDER_USAGE);

    public static final SingleSlotAccess INPUT_SOUL = new SingleSlotAccess();
    public static final SingleSlotAccess INPUT_OTHER = new SingleSlotAccess();
    public static final MultiSlotAccess OUTPUT = new MultiSlotAccess();
    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();

    @UseOnly(LogicalSide.CLIENT)
    @Nullable
    private SoulBindingRecipe clientRecipe;

    private final CraftingMachineTaskHost<SoulBindingRecipe, SoulBindingRecipe.Input> craftingTaskHost;

    public SoulBinderBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.SOUL_BINDER.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, CAPACITY, USAGE);
        fluidHandler = createFluidHandler();

        // Create the crafting task host
        craftingTaskHost = new CraftingMachineTaskHost<>(this, this::hasEnergy,
                EIORecipes.SOUL_BINDING.type().get(), this::createTask, this::createRecipeInput);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SoulBinderMenu(containerId, playerInventory, this);
    }

    @Override
    public void serverTick() {
        super.serverTick();

        if (canAct()) {
            craftingTaskHost.tick();
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ProgressMachineBlock.POWERED)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            SoundHandler.playSound(pos, EIOSounds.SOUL_BINDER.get(), SoundSource.BLOCKS, MachinesConfig.CLIENT.MACHINE_VOLUME.get(), 1.0f, random, x, y, z);

            Direction direction = state.getValue(ProgressMachineBlock.FACING);
            Direction.Axis axis = direction.getAxis();
            double r = 0.52;
            double ss = random.nextDouble() * 0.8 - 0.4;
            double dx = axis == Direction.Axis.X ? direction.getStepX() * r : ss;
            double dy = random.nextDouble() * 6.0 / 16.0 + 7.0 / 16.0;
            double dz = axis == Direction.Axis.Z ? direction.getStepZ() * r : ss;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0); //TODO green flame
        } else {
            SoundHandler.stopSound(pos);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        craftingTaskHost.onLevelReady();
    }

    // region Inventory

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        // TODO: Support for non-soul vial storages.
        return MachineInventoryLayout.builder()
                .setStackLimit(1)
                .inputSlot((slot, stack) -> stack.is(EIOItems.SOUL_VIAL.get()) && SoulBoundUtils.isBound(stack))
                .slotAccess(INPUT_SOUL)
                .inputSlot(this::isValidInput)
                .slotAccess(INPUT_OTHER)
                .setStackLimit(64)
                .outputSlot(2)
                .slotAccess(OUTPUT)
                .capacitor()
                .build();
    }

    private boolean isValidInput(int index, ItemStack stack) {
        if (level == null) {
            return false;
        }
        return MachineRecipeCaches.SOUL_BINDING.hasRecipe(level.getRecipeManager(), List.of(stack));
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        craftingTaskHost.newTaskAvailable();

        if (level != null && level.isClientSide) {
            clientRecipe = level.getRecipeManager()
                    .getRecipeFor(EIORecipes.SOUL_BINDING.type().get(), createFakeRecipeInput(), level)
                    .orElse(null);
        }
    }

    private SoulBindingRecipe.Input createRecipeInput() {
        return new SoulBindingRecipe.Input(INPUT_SOUL.getItemStack(getInventory()),
                INPUT_OTHER.getItemStack(getInventory()), TANK.getFluid(getFluidHandler()));
    }

    @UseOnly(LogicalSide.CLIENT)
    private SoulBindingRecipe.Input createFakeRecipeInput() {
        return new SoulBindingRecipe.Input(INPUT_SOUL.getItemStack(getInventory()),
                INPUT_OTHER.getItemStack(getInventory()),
                new FluidStack(EIOFluids.XP_JUICE.source().get(), Integer.MAX_VALUE));
    }

    // endregion

    @UseOnly(LogicalSide.CLIENT)
    public int getClientExp() {
        // This should always set a valid recipe.
        if (level != null && clientRecipe == null && hasValidRecipe()) {
            clientRecipe = level.getRecipeManager()
                    .getRecipeFor(EIORecipes.SOUL_BINDING.type().get(), createFakeRecipeInput(), level)
                    .orElse(null);
        }

        return clientRecipe != null ? clientRecipe.experience() : 0;
    }

    private boolean hasValidRecipe() {
        if (level == null) {
            return false;
        }
        return MachineRecipeCaches.SOUL_BINDING
                .hasRecipe(level.getRecipeManager(), List.of(INPUT_SOUL.getItemStack(getInventory()), INPUT_OTHER.getItemStack(getInventory())));
    }

    // region Fluid Storage
    @Override
    public @Nullable MachineTankLayout getTankLayout() {
        return MachineTankLayout.builder().tank(TANK, 10000, true, false, f -> f.getFluid().is(com.enderio.enderio.foundation.tag.EIOTags.Fluids.EXPERIENCE)).build();
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                craftingTaskHost.newTaskAvailable();
                updateMachineState(MachineState.EMPTY_TANK, TANK.getFluidAmount(this) <= 0);
                setChanged();
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                // Convert into XP Juice
                if (TANK.isFluidValid(this, resource)) {
                    FluidStack currentFluid = TANK.getFluid(this);
                    if (currentFluid.isEmpty() || resource.isFluidEqual(currentFluid)) {
                        return super.fill(resource, action);
                    } else {
                        return super.fill(EnderFluidUtil.copyWithAmount(currentFluid, resource.getAmount()), action);
                    }
                }

                // Non-XP is not allowed.
                return 0;
            }
        };
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
    }

    // endregion

    // region Crafting Task

    public float getCraftingProgress() {
        return craftingTaskHost.getProgress();
    }

    @Override
    public boolean isActive() {
        return canAct() && hasEnergy() && craftingTaskHost.hasTask();
    }

    protected PoweredCraftingMachineTask<SoulBindingRecipe, SoulBindingRecipe.Input> createTask(Level level,
            SoulBindingRecipe.Input container, @Nullable SoulBindingRecipe recipe) {
        return new PoweredCraftingMachineTask<>(level, getInventory(), getEnergyStorage(), container, OUTPUT, recipe) {
            private ItemStack plannedVial = ItemStack.EMPTY;
            private ItemStack plannedMaterial = ItemStack.EMPTY;

            @Override
            protected SoulBindingRecipe.Input getCurrentRecipeInput() {
                return createRecipeInput();
            }

            @Override
            protected boolean canReuseOutputs(SoulBindingRecipe.Input currentInput) {
                return ItemStack.matches(plannedVial, currentInput.boundSoulItem())
                        && ItemStack.matches(plannedMaterial, currentInput.itemToBind());
            }

            @Override
            protected SoulBindingRecipe.Input prepareToDetermineOutputs(SoulBindingRecipe recipe, SoulBindingRecipe.Input input) {
                // Both stacks can affect the bound result. Copy them before the menu or a hopper can replace them.
                plannedVial = input.boundSoulItem().copy();
                plannedMaterial = input.itemToBind().copy();
                return input;
            }

            @Override
            protected void consumeInputs(SoulBindingRecipe recipe) {
                INPUT_SOUL.getItemStack(getInventory()).shrink(1);
                INPUT_OTHER.getItemStack(getInventory()).shrink(1);

                int fluidCost = Math.toIntExact(ExperienceUtil.getFluidFromLevel(recipe.experience()));
                TANK.drain(getFluidHandler(), fluidCost, IFluidHandler.FluidAction.EXECUTE);
            }

        };
    }

    // endregion

    // region Serialization

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

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        craftingTaskHost.save(lookupProvider, tag);
        saveTank(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        craftingTaskHost.load(lookupProvider, tag);
        loadTank(lookupProvider, tag);
    }

    // endregion
}

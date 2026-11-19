package com.enderio.enderio.content.machines.vat;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.core.common.util.NamedFluidContents;
import com.enderio.enderio.EnderIO;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.client.SoundHandler;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.recipe.MachineRecipeCaches;
import com.enderio.enderio.foundation.io.fluid.FluidItemInteractive;
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
import com.enderio.enderio.init.EIOSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class VatBlockEntity extends MachineBlockEntity implements FluidTankUser, FluidItemInteractive {

    public static final int TANK_CAPACITY = 8 * FluidType.BUCKET_VOLUME;
    public static final TankAccess INPUT_TANK = new TankAccess();
    public static final TankAccess OUTPUT_TANK = new TankAccess();
    public static final MultiSlotAccess REAGENTS = new MultiSlotAccess();

    private static final ResourceLocation EMPTY = EnderIO.rl("");
    private static final String PENDING_OUTPUT_NBT = "PendingOutput";
    private static final String PENDING_OUTPUT_ITEM = "pending_output";

    private final MachineFluidHandler fluidHandler;
    private final CraftingMachineTaskHost<FermentingRecipe, FermentingRecipe.Input> craftingTaskHost;
    private FluidStack pendingOutput = FluidStack.EMPTY;

    public VatBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.VAT.get(), worldPosition, blockState, true);
        fluidHandler = createFluidHandler();

        craftingTaskHost = new CraftingMachineTaskHost<>(this, () -> pendingOutput.isEmpty(),
                EIORecipes.VAT_FERMENTING.type().get(), this::createTask, this::createRecipeInput);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VatMenu(containerId, playerInventory, this);
    }

    @Override
    public void serverTick() {
        super.serverTick();

        boolean running = canAct();
        if (!pendingOutput.isEmpty()) tickPendingOutput(running);
        if (running && pendingOutput.isEmpty()) craftingTaskHost.tick();
        updateMachineState(MachineState.ACTIVE, isActive());
    }

    private void tickPendingOutput(boolean running) {
        var tank = getOutputTank();
        int accepted = running ? tank.fill(pendingOutput, IFluidHandler.FluidAction.EXECUTE) : 0;
        if (accepted > 0) {
            pendingOutput = EnderFluidUtil.copyWithAmount(pendingOutput, pendingOutput.getAmount() - accepted);
            setChanged();
        }
        boolean blocked = !pendingOutput.isEmpty() && tank.fill(pendingOutput, IFluidHandler.FluidAction.SIMULATE) == 0;
        if (getMachineStates().contains(MachineState.FULL_OUTPUT) != blocked) {
            updateMachineState(MachineState.FULL_OUTPUT, blocked);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ProgressMachineBlock.POWERED)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            SoundHandler.playSound(pos, EIOSounds.VAT.get(), SoundSource.BLOCKS, MachinesConfig.CLIENT.MACHINE_VOLUME.get(), 1.0f, random, x, y, z);
        } else {
            SoundHandler.stopSound(pos);
        }
    }

    public boolean isActive() {
        return canAct() && (!pendingOutput.isEmpty() || craftingTaskHost.hasTask());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        craftingTaskHost.onLevelReady();
    }

    protected VatCraftingMachineTask createTask(Level level, FermentingRecipe.Input input,
            @Nullable FermentingRecipe recipe) {
        return new VatCraftingMachineTask(level, getInventory(), getFluidHandler(), input, recipe);
    }

    @Override
    public @Nullable MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
            .inputSlot(2, this::acceptSlotInput)
            .slotAccess(REAGENTS)
            .build();
    }

    protected boolean acceptSlotInput(int slot, ItemStack stack) {
        if (level == null) {
            return false;
        }
        return MachineRecipeCaches.FERMENTING.hasValidRecipeIf(level.getRecipeManager(), getInventory(), REAGENTS, slot, stack);
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        craftingTaskHost.newTaskAvailable();
    }

    private FermentingRecipe.Input createRecipeInput() {
        List<ItemStack> reagents = REAGENTS.getItemStacks(getInventory());
        return new FermentingRecipe.Input(reagents.get(0), reagents.get(1), INPUT_TANK.getFluid(this), level);
    }

    @Override
    public MachineTankLayout getTankLayout() {
        return MachineTankLayout.builder()
                .tank(INPUT_TANK, TANK_CAPACITY, true, false, (stack) -> true)
                .tank(OUTPUT_TANK, TANK_CAPACITY, false, true, (stack) -> true)
                .build();
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                craftingTaskHost.newTaskAvailable();
                setChanged();
            }
        };
    }

    public MachineFluidTank getInputTank() {
        return INPUT_TANK.getTank(this);
    }

    public MachineFluidTank getOutputTank() {
        return OUTPUT_TANK.getTank(this);
    }

    FluidStack getPendingOutput() {
        return pendingOutput;
    }

    public float getCraftingProgress() {
        return pendingOutput.isEmpty() ? craftingTaskHost.getProgress() : 1.0f;
    }

    @Nullable
    public FermentingRecipe getRecipe() {
        if (pendingOutput.isEmpty() && craftingTaskHost.hasTask()) {
            return craftingTaskHost.getCurrentTask().getRecipe();
        }

        return null;
    }

    @UseOnly(LogicalSide.SERVER)
    public void moveFluidToOutputTank() {
        if (pendingOutput.isEmpty() && OUTPUT_TANK.isEmpty(this) && !INPUT_TANK.isEmpty(this)) {
            OUTPUT_TANK.setFluid(this, INPUT_TANK.getFluid(this));
            INPUT_TANK.setFluid(this, FluidStack.EMPTY);
        }
    }

    @UseOnly(LogicalSide.SERVER)
    public void dumpOutputTank() {
        OUTPUT_TANK.setFluid(this, FluidStack.EMPTY);
    }

    protected class VatCraftingMachineTask
            extends CraftingMachineTask<FermentingRecipe, FermentingRecipe.Input> {

        public VatCraftingMachineTask(@NotNull Level level, MachineInventory inventory,
                MachineFluidHandler fluidHandler, FermentingRecipe.Input input,
                @Nullable FermentingRecipe recipe) {
            super(level, inventory, fluidHandler, input, recipe);
        }

        @Override
        protected void consumeInputs(FermentingRecipe recipe) {
            REAGENTS.get(0).getItemStack(inventory).shrink(1);
            REAGENTS.get(1).getItemStack(inventory).shrink(1);

            INPUT_TANK.getTank(fluidHandler).drain(recipe.input().amount(), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        protected boolean placeOutputs(List<OutputStack> outputs, boolean simulate) {
            FluidStack output = outputs.get(0).getFluid();
            MachineFluidTank tank = getOutputTank();

            // Ordinary batches still wait for room. A batch larger than the tank can commit once it starts filling.
            int requiredSpace = output.getAmount() > tank.getCapacity() ? 1 : output.getAmount();
            if (tank.fill(output, IFluidHandler.FluidAction.SIMULATE) < requiredSpace) {
                return false;
            }

            if (!simulate) {
                int accepted = tank.fill(output, IFluidHandler.FluidAction.EXECUTE);
                if (accepted < output.getAmount()) {
                    pendingOutput = EnderFluidUtil.copyWithAmount(output, output.getAmount() - accepted);
                    setChanged();
                }
            }

            return true;
        }

        @Override
        protected int makeProgress(int remainingProgress) {
            return 1; // VAT doesn't consume power
        }

        @Override
        protected int getProgressRequired(FermentingRecipe recipe) {
            return recipe.ticks();
        }

    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        NamedFluidContents fluidContents = EIODataComponents.NAMED_FLUID_CONTENTS.get(components);
        if (fluidContents != null) {
            INPUT_TANK.setFluid(this, fluidContents.copy("input_tank"));
            OUTPUT_TANK.setFluid(this, fluidContents.copy("output_tank"));
            pendingOutput = fluidContents.copy(PENDING_OUTPUT_ITEM);
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        var inputTank = INPUT_TANK.getTank(this);
        var outputTank = OUTPUT_TANK.getTank(this);
        if (!inputTank.isEmpty() || !outputTank.isEmpty() || !pendingOutput.isEmpty()) {
            var fluids = new HashMap<>(Map.of("input_tank", inputTank.getFluid(), "output_tank", outputTank.getFluid()));
            if (!pendingOutput.isEmpty()) fluids.put(PENDING_OUTPUT_ITEM, pendingOutput);
            EIODataComponents.NAMED_FLUID_CONTENTS.set(components, NamedFluidContents.copyOf(fluids));
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(PENDING_OUTPUT_NBT);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        saveTank(lookupProvider, tag);
        if (!pendingOutput.isEmpty()) tag.put(PENDING_OUTPUT_NBT, StackDataCodecs.saveFluid(pendingOutput));
        craftingTaskHost.save(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);
        pendingOutput = StackDataCodecs.loadFluid(tag.getCompound(PENDING_OUTPUT_NBT));
        craftingTaskHost.load(lookupProvider, tag);
    }
}

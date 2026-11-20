package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.energy.IMachineEnergyStorage;
import com.enderio.enderio.foundation.recipe.MachineRecipeCaches;
import com.enderio.enderio.foundation.task.PoweredCraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.enderio.core.common.recipes.SizedIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

// TODO: Award XP

public class AlloySmelterBlockEntity extends PoweredMachineBlockEntity {

    public static final MultiSlotAccess INPUTS = new MultiSlotAccess();
    public static final SingleSlotAccess OUTPUT = new SingleSlotAccess();

    public static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_CAPACITY);
    public static final QuadraticScalable USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE);

    /**
     * The alloying mode for the machine.
     * Determines which recipes it can craft.
     */
    private AlloySmelterMode mode = AlloySmelterMode.ALL;

    protected final AlloySmeltingMachineTaskHost craftingTaskHost;

    private static final Logger LOGGER = LogUtils.getLogger();

    public AlloySmelterBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.ALLOY_SMELTER.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, CAPACITY, USAGE);

        // Crafting task host
        craftingTaskHost = new AlloySmeltingMachineTaskHost(this, this::canAcceptTask,
                EIORecipes.ALLOY_SMELTING.type().get(), this::createTask, this::createRecipeInput);
    }

    protected boolean canAcceptTask() {
        return hasEnergy() && !isRedstoneBlocked();
    }

    /**
     * Get the alloy smelting mode.
     */
    public AlloySmelterMode getMode() {
        return mode;
    }

    public void setMode(AlloySmelterMode mode) {
        this.mode = mode;

        if (level != null && !level.isClientSide) {
            craftingTaskHost.newTaskAvailable();
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AlloySmelterMenu(containerId, inventory, this);
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

            if (random.nextDouble() < 0.1) {
                level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            Direction direction = state.getValue(ProgressMachineBlock.FACING);
            Direction.Axis axis = direction.getAxis();
            double r = 0.52;
            double ss = random.nextDouble() * 0.6 - 0.3;
            double dx = axis == Direction.Axis.X ? direction.getStepX() * r : ss;
            double dy = random.nextDouble() * 3.0 / 16.0 + 10.0 / 16.0;
            double dz = axis == Direction.Axis.Z ? direction.getStepZ() * r : ss;
            level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
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
        return MachineInventoryLayout.builder()
                .inputSlot(3, this::acceptSlotInput)
                .slotAccess(INPUTS)
                .outputSlot()
                .slotAccess(OUTPUT)
                .capacitor()
                .build();
    }

    protected boolean acceptSlotInput(int slot, ItemStack stack) {
        if (level == null) {
            return false;
        }
        if (getMode().canAlloy()) {
            if (MachineRecipeCaches.ALLOY_SMELTING_ONLY_ALLOY.hasValidRecipeIf(level.getRecipeManager(), getInventory(), INPUTS, slot, stack)) {
                return true;
            }
        }

        if (getMode().canSmelt()) {
            // Check all items are the same, or will be
            var currentStacks = INPUTS.getAccesses()
                    .stream()
                    .map(i -> i.isSlot(slot) ? stack : i.getItemStack(getInventory()))
                    .filter(i -> !i.isEmpty())
                    .toList();

            if (currentStacks.stream().allMatch(i -> i.is(stack.getItem())) || currentStacks.size() == 1) {
                return MachineRecipeCaches.ALLOY_SMELTING_ONLY_SMELTING.hasRecipe(level.getRecipeManager(), List.of(stack));
            }
        }

        return false;
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        craftingTaskHost.newTaskAvailable();
    }

    private AlloySmeltingRecipe.Input createRecipeInput() {
        return new AlloySmeltingRecipe.Input(INPUTS.getItemStacks(getInventory()), 1);
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

    protected AlloySmeltingMachineTask createTask(Level level, AlloySmeltingRecipe.Input recipeInput,
            @Nullable AlloySmeltingRecipe recipe) {
        return new AlloySmeltingMachineTask(level, getInventory(), getEnergyStorage(), recipeInput, INPUTS, OUTPUT,
                recipe);
    }

    protected static class AlloySmeltingMachineTask
            extends PoweredCraftingMachineTask<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> {
        private static final int MAX_SMELTING_BATCH = 3;

        private final MultiSlotAccess inputs;
        private int inputsConsumed;

        public AlloySmeltingMachineTask(@NotNull Level level, MachineInventory inventory,
                IMachineEnergyStorage energyStorage, AlloySmeltingRecipe.Input recipeInput, MultiSlotAccess inputs,
                SingleSlotAccess outputSlot, @Nullable AlloySmeltingRecipe recipe) {
            super(level, inventory, energyStorage, recipeInput, outputSlot, recipe);
            this.inputs = inputs;
        }

        @Override
        protected AlloySmeltingRecipe.Input getCurrentRecipeInput() {
            return new AlloySmeltingRecipe.Input(inputs.getItemStacks(getInventory()), 1);
        }

        @Override
        protected boolean canReuseOutputs(AlloySmeltingRecipe.Input currentInput) {
            var recipe = getRecipe();
            // Keep the original batch as an upper bound while it is waiting to finish.
            return recipe == null || !recipe.isSmelting()
                    || (inputsConsumed > 0 && inputsConsumed <= countSmeltingInputs(recipe, currentInput));
        }

        @Override
        protected AlloySmeltingRecipe.Input prepareToDetermineOutputs(AlloySmeltingRecipe recipe,
                AlloySmeltingRecipe.Input recipeInput) {
            inputsConsumed = recipe.isSmelting() ? countSmeltingInputs(recipe, recipeInput) : 1;
            return recipeInput.withInputsConsumed(inputsConsumed);
        }

        private static int countSmeltingInputs(AlloySmeltingRecipe recipe, AlloySmeltingRecipe.Input recipeInput) {
            SizedIngredient ingredient = recipe.inputs().get(0);
            int count = 0;
            for (ItemStack stack : recipeInput.inputs()) {
                if (ingredient.test(stack)) {
                    count += Math.min(MAX_SMELTING_BATCH - count, stack.getCount());
                }
            }
            return count;
        }

        @Override
        protected void consumeInputs(AlloySmeltingRecipe recipe) {
            MachineInventory inv = getInventory();

            if (recipe.isSmelting()) {
                SizedIngredient input = recipe.inputs().get(0);

                int consumed = 0;
                for (int i = inputs.size() - 1; i >= 0; i--) {
                    ItemStack itemStack = inputs.get(i).getItemStack(getInventory());
                    if (input.test(itemStack)) {
                        int consumedNow = Math.min(inputsConsumed - consumed, itemStack.getCount());
                        itemStack.shrink(consumedNow);
                        consumed += consumedNow;
                    }
                }
            } else {
                // Track which ingredients have been consumed
                List<SizedIngredient> inputs = recipe.inputs();
                boolean[] consumed = new boolean[3];

                // Iterate over the slots
                for (SingleSlotAccess slot : this.inputs.getAccesses()) {
                    ItemStack stack = slot.getItemStack(inv);

                    // Iterate over the inputs
                    for (int i = 0; i < 3; i++) {

                        // If this ingredient has been matched already, continue
                        if (consumed[i]) {
                            continue;
                        }

                        if (i < inputs.size()) {
                            // If we expect an input, test we have a match for it.
                            SizedIngredient input = inputs.get(i);

                            if (input.test(stack)) {
                                consumed[i] = true;
                                stack.shrink(input.count());
                            }
                        } else if (stack.isEmpty()) {
                            // If we don't expect an input, make sure we have a blank for it.
                            consumed[i] = true;
                        }
                    }
                }
            }
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
            inputsConsumed = nbt.getInt(MachineNBTKeys.PROCESSED_INPUTS);
        }

        @Override
        public CompoundTag serializeNBT() {
            var tag = super.serializeNBT();
            tag.putInt(MachineNBTKeys.PROCESSED_INPUTS, inputsConsumed);
            return tag;
        }
    }

    protected class AlloySmeltingMachineTaskHost
            extends CraftingMachineTaskHost<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> {
        public AlloySmeltingMachineTaskHost(EnderBlockEntity blockEntity, Supplier<Boolean> canAcceptNewTask,
                RecipeType<AlloySmeltingRecipe> recipeType,
                CraftingMachineTaskFactory<AlloySmeltingMachineTask, AlloySmeltingRecipe, AlloySmeltingRecipe.Input> taskFactory,
                Supplier<AlloySmeltingRecipe.Input> recipeInputSupplier) {
            super(blockEntity, canAcceptNewTask, recipeType, taskFactory, recipeInputSupplier);
        }

        @Override
        protected Optional<AlloySmeltingRecipe> findRecipe() {
            var mode = getMode();
            if (mode == AlloySmelterMode.ALL) {
                return super.findRecipe();
            }

            var level = getLevel();
            if (level == null) {
                return Optional.empty();
            }

            // A furnace recipe and an alloy recipe can accept the same input.
            var input = createRecipeInput();
            return level.getRecipeManager().getAllRecipesFor(EIORecipes.ALLOY_SMELTING.type().get()).stream()
                .filter(recipe -> recipe.isSmelting() == mode.canSmelt() && recipe.matches(input, level))
                .findFirst();
        }
    }

    // endregion

    // region Serialization

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        craftingTaskHost.save(lookupProvider, tag);
        tag.putInt(MachineNBTKeys.MACHINE_MODE, this.mode.ordinal());
        super.saveAdditional(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        craftingTaskHost.load(lookupProvider, tag);

        // TODO: EnderIO 8 - swap to serializing the enum name.
        try {
            mode = AlloySmelterMode.values()[tag.getInt(MachineNBTKeys.MACHINE_MODE)];
        } catch (IndexOutOfBoundsException ex) { // In case something happens in the future.
            LOGGER.error("Invalid alloy smelter mode loaded from NBT. Ignoring.");
        }
        super.load(tag);
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        // TODO: 1.21: Write crafting host into the item components.
        mode = EIODataComponents.ALLOY_SMELTER_MODE.getOrDefault(components, AlloySmelterMode.ALL);
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);
        EIODataComponents.ALLOY_SMELTER_MODE.set(components, mode);
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.MACHINE_MODE);
    }

    // endregion
}

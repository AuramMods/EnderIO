package com.enderio.enderio.foundation.task;

import com.enderio.core.common.recipes.OutputStack;
import com.enderio.core.common.recipes.RecipeSnapshot;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.state.MachineState;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

// TODO: A recipe interface that doesn't require power :)
public abstract class CraftingMachineTask<R extends MachineRecipe<T>, T extends RecipeInput> implements MachineTask {

    protected final Level level;
    protected final MachineInventory inventory;
    @Nullable
    protected final MachineFluidHandler fluidHandler;
    @Nullable
    protected final MultiSlotAccess outputSlots;
    protected final T recipeInput;

    @Nullable
    private R recipe;
    @Nullable
    private final RecipeType<?> recipeType;

    private int progressMade;
    private int progressRequired;

    private boolean hasConsumedInputs;
    private boolean hasDeterminedOutputs;

    private List<OutputStack> outputs = List.of();

    private boolean isComplete;

    private static final Logger LOGGER = LogUtils.getLogger();

    public CraftingMachineTask(@NotNull Level level, MachineInventory inventory, T recipeInput,
            @Nullable MultiSlotAccess outputSlots, @Nullable R recipe) {
        this(level, inventory, null, recipeInput, outputSlots, recipe);
    }

    public CraftingMachineTask(@NotNull Level level, MachineInventory inventory,
            @Nullable MachineFluidHandler fluidHandler, T recipeInput, @Nullable R recipe) {
        this(level, inventory, fluidHandler, recipeInput, null, recipe);
    }

    public CraftingMachineTask(@NotNull Level level, MachineInventory inventory,
            @Nullable MachineFluidHandler fluidHandler, T recipeInput, @Nullable MultiSlotAccess outputSlots,
            @Nullable R recipe) {
        this.level = level;
        this.inventory = inventory;
        this.fluidHandler = fluidHandler;
        this.recipeInput = recipeInput;
        this.outputSlots = outputSlots;
        this.recipe = recipe;
        this.recipeType = recipe == null ? null : recipe.getType();
        inventory.updateMachineState(MachineState.FULL_OUTPUT, false);

        if (recipe != null) {
            inventory.updateMachineState(MachineState.EMPTY_INPUT, !recipe.matches(recipeInput, level));
        } else {
            inventory.updateMachineState(MachineState.EMPTY_INPUT, true);
        }
    }

    public MachineInventory getInventory() {
        return inventory;
    }

    @Nullable
    public R getRecipe() {
        return recipe;
    }

    @Nullable
    public ResourceLocation getRecipeId() {
        return recipe == null ? null : recipe.getId();
    }

    // region Abstract Implementation

    protected abstract void consumeInputs(R recipe);

    protected abstract int makeProgress(int remainingProgress);

    protected abstract int getProgressRequired(R recipe);

    // endregion

    // region Overridable Events

    /**
     * This is fired right before recipe outputs are determined for the task.
     */
    protected T prepareToDetermineOutputs(R recipe, T recipeInput) {
        return recipeInput;
    }

    // endregion

    // region Task Implementation

    @Override
    public void tick() {
        // If the recipe is done, don't let it tick.
        if (isComplete) {
            return;
        }

        // If the recipe failed to load somehow, cancel
        if (recipe == null || level.getRecipeManager().byKey(recipe.getId()).orElse(null) != recipe) {
            isComplete = true;
            return;
        }

        if (!recipe.matches(recipeInput, level)) {
            inventory.updateMachineState(MachineState.EMPTY_INPUT, true);
            isComplete = true;
            return;
        }
        inventory.updateMachineState(MachineState.EMPTY_INPUT, false);

        // Get the outputs list.
        if (!hasDeterminedOutputs) {
            hasDeterminedOutputs = true;
            T processedRecipeInput = prepareToDetermineOutputs(recipe, recipeInput);
            outputs = recipe.craft(processedRecipeInput, level.registryAccess());

            // TODO: Compact any items that are the same into singular stacks?

            // Store the recipe energy cost.
            progressRequired = getProgressRequired(recipe);
        }

        // Try to consume as much energy as possible to finish the craft.
        if (progressMade < progressRequired) {
            progressMade += makeProgress(progressRequired - progressMade);
        }

        // If the recipe has been crafted, attempt to put it into storage
        if (progressMade >= progressRequired) {
            // Attempt to complete the craft
            boolean placeOutputs = placeOutputs(outputs, false);
            inventory.updateMachineState(MachineState.FULL_OUTPUT, !placeOutputs);
            if (placeOutputs) {
                // Take the inputs
                consumeInputs(recipe);

                // The receiver was able to take the outputs, task complete.
                isComplete = true;
            }
        }
    }

    @Override
    public float getProgress() {
        if (recipe == null || progressRequired <= 0) {
            return 0.0f;
        }

        return Math.min(1.0f, progressMade / (float) progressRequired);
    }

    @Override
    public boolean isCompleted() {
        return isComplete;
    }

    // endregion

    // region Resource Depletion

    protected boolean placeOutputs(List<OutputStack> outputs, boolean simulate) {
        // TODO: Handle fluids too.

        // return early if there are no output slots
        if (outputSlots == null) {
            return false;
        }

        // See that we can add all the outputs
        MachineInventory snapshot = getInventory().snapshot(outputSlots); // Snapshot for chained simulations.
        for (OutputStack output : outputs) {
            ItemStack item = output.getItem().copy();

            for (SingleSlotAccess outputAccess : outputSlots.getAccesses()) {
                item = outputAccess.insertItem(snapshot, item, false); // Don't simulate, we are using a snapshot copy
            }

            // If we fail, say we can't accept these outputs
            if (!item.isEmpty()) {
                return false;
            }
        }

        // If we're not simulating, go for it
        if (!simulate) {
            for (OutputStack output : outputs) {
                ItemStack item = output.getItem().copy();

                for (SingleSlotAccess outputAccess : outputSlots.getAccesses()) {
                    item = outputAccess.insertItem(inventory, item, false);
                }
            }
        }

        return true;
    }

    // endregion

    // region Serialization

    private static final String KEY_RECIPE_ID = "RecipeId";
    private static final String KEY_RECIPE_DATA = "RecipeData";
    private static final String KEY_PROGRESS_MADE = "ProgressMade";
    private static final String KEY_PROGRESS_REQUIRED = "ProgressRequired";
    private static final String KEY_HAS_COLLECTED_INPUTS = "HasCollectedInputs";
    private static final String KEY_IS_COMPLETE = "IsComplete";
    private static final String KEY_HAS_DETERMINED_OUTPUTS = "HasDeterminedOutputs";
    private static final String KEY_OUTPUTS = "Outputs";

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        // If the recipe is null, we aren't going to keep the task
        if (recipe == null) {
            return tag;
        }

        tag.putString(KEY_RECIPE_ID, recipe.getId().toString());
        tag.putByteArray(KEY_RECIPE_DATA, RecipeSnapshot.encode(recipe, level.registryAccess()));
        tag.putInt(KEY_PROGRESS_MADE, progressMade);
        tag.putInt(KEY_PROGRESS_REQUIRED, progressRequired);
        tag.putBoolean(KEY_HAS_COLLECTED_INPUTS, hasConsumedInputs);
        tag.putBoolean(KEY_IS_COMPLETE, isComplete);

        tag.putBoolean(KEY_HAS_DETERMINED_OUTPUTS, hasDeterminedOutputs);
        if (hasDeterminedOutputs) {
            ListTag outputsNbt = new ListTag();
            for (OutputStack stack : outputs) {
                outputsNbt.add(stack.serializeNBT());
            }
            tag.put(KEY_OUTPUTS, outputsNbt);
        }

        return tag;
    }

    // TODO: 20.6: Swap tasks to use Codecs.
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // TODO: Exception handling
        var savedId = ResourceLocation.tryParse(nbt.getString(KEY_RECIPE_ID));
        recipe = savedId == null ? null : loadRecipe(savedId);
        progressMade = Math.max(0, nbt.getInt(KEY_PROGRESS_MADE));
        progressRequired = Math.max(0, nbt.getInt(KEY_PROGRESS_REQUIRED));
        hasConsumedInputs = nbt.getBoolean(KEY_HAS_COLLECTED_INPUTS);
        isComplete = nbt.getBoolean(KEY_IS_COMPLETE);

        hasDeterminedOutputs = nbt.getBoolean(KEY_HAS_DETERMINED_OUTPUTS);
        outputs = List.of();
        if (recipe == null || nbt.contains(KEY_RECIPE_DATA, Tag.TAG_BYTE_ARRAY)
                && !java.util.Arrays.equals(nbt.getByteArray(KEY_RECIPE_DATA), RecipeSnapshot.encode(recipe, level.registryAccess()))) {
            // Inputs are consumed only after outputs can be placed; cancelling leaves them in storage.
            isComplete = true;
            return;
        }
        if (hasDeterminedOutputs) {
            if (!nbt.contains(KEY_OUTPUTS, Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Saved machine task has no determined output list");
            }
            ListTag outputsNbt = nbt.getList(KEY_OUTPUTS, Tag.TAG_COMPOUND);
            outputs = new ArrayList<>();
            for (Tag tag : outputsNbt) {
                outputs.add(OutputStack.fromNBT((CompoundTag) tag));
            }
        }
    }

    @Nullable
    protected R loadRecipe(ResourceLocation id) {
        try {
            // noinspection unchecked
            return (R) level.getRecipeManager().byKey(id)
                .filter(found -> recipeType != null && found.getType() == recipeType).orElse(null);
        } catch (ClassCastException ex) {
            // Can occur when loading a world with the old smelting recipe system.
            LOGGER.warn("Failed to cast recipe '{}' to the correct type, not loading in-progress recipe.", id);
            return null;
        }
    }

    // endregion
}

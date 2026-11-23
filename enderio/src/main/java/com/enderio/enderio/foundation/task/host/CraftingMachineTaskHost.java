package com.enderio.enderio.foundation.task.host;

import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.integration.gamestages.StageRules;
import net.minecraft.server.level.ServerLevel;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.core.common.recipes.RecipeLookup;
import com.enderio.enderio.foundation.task.CraftingMachineTask;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

public class CraftingMachineTaskHost<R extends MachineRecipe<T>, T extends RecipeInput> extends MachineTaskHost {

    public interface CraftingMachineTaskFactory<T extends CraftingMachineTask<R, C>, R extends MachineRecipe<C>, C extends RecipeInput> {
        T createTask(Level level, C container, @Nullable R recipe);
    }

    private final RecipeType<R> recipeType;
    private final EnderBlockEntity blockEntity;
    private final CraftingMachineTaskFactory<? extends CraftingMachineTask<R, T>, R, T> taskFactory;
    private final Supplier<T> recipeInputSupplier;

    /**
     * This should be constructed in the constructor of your block entity.
     */
    public CraftingMachineTaskHost(EnderBlockEntity blockEntity, Supplier<Boolean> canAcceptNewTask,
            RecipeType<R> recipeType, CraftingMachineTaskFactory<? extends CraftingMachineTask<R, T>, R, T> taskFactory,
            Supplier<T> recipeInputSupplier) {
        super(blockEntity, canAcceptNewTask);
        this.blockEntity = blockEntity;
        this.recipeType = recipeType;
        this.taskFactory = taskFactory;
        this.recipeInputSupplier = recipeInputSupplier;
    }

    @Nullable
    public CraftingMachineTask<R, T> getCurrentTask() {
        // noinspection unchecked
        return (CraftingMachineTask<R, T>) super.getCurrentTask();
    }

    // region MachineTaskHost Implementation

    @Override
    protected boolean canTickTask() {
        return permitsCurrentRecipe();
    }

    public boolean permitsCurrentRecipe() {
        var task = getCurrentTask();
        if (task == null || task.getRecipe() == null || !(getLevel() instanceof ServerLevel server)) {
            return true;
        }
        var owner = blockEntity instanceof MachineBlockEntity machine
                ? machine.getMachineOwner() : null;
        return StageRules.permitsRecipe(server, owner, task.getRecipe());
    }

    @Override
    protected @Nullable CraftingMachineTask<R, T> getNewTask() {
        if (getLevel() == null) {
            return null;
        }

        return findRecipe().map(r -> taskFactory.createTask(getLevel(), recipeInputSupplier.get(), r)).orElse(null);
    }

    @Override
    protected @Nullable CraftingMachineTask<R, T> loadTask(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
        if (getLevel() == null) {
            return null;
        }

        var recipe = RecipeLookup.find(getLevel().getRecipeManager(), recipeType, nbt.getString("RecipeId"));
        if (recipe.isEmpty()) {
            return null;
        }
        CraftingMachineTask<R, T> task = taskFactory.createTask(getLevel(), recipeInputSupplier.get(), recipe.get());
        task.deserializeNBT(lookupProvider, nbt);
        return task;
    }

    @Override
    protected boolean shouldStartNewTask() {
        if (super.shouldStartNewTask()) {
            return true;
        }

        // If recipe has changed
        var currentRecipe = findRecipe();
        return !getCurrentTask().rebindRecipe(currentRecipe.orElse(null));
    }

    // endregion

    protected T createRecipeInput() {
        return recipeInputSupplier.get();
    }

    protected Optional<R> findRecipe() {
        Level level = getLevel();
        if (level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(recipeType, recipeInputSupplier.get(), level);
    }
}

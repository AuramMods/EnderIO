package com.enderio.enderio.content.storage.crafter;

import com.enderio.enderio.integration.gamestages.StageFeature;
import com.enderio.enderio.integration.gamestages.StageRules;
import net.minecraft.server.level.ServerLevel;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.core.common.recipes.CraftingRecipeInput;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

// TODO: Might want to see if we can adapt this into a crafting task.
public class CrafterBlockEntity extends PoweredMachineBlockEntity {

    @Override
    public StageFeature automationStageFeature() {
        return StageFeature.CRAFTER_AUTOMATION;
    }

    private static final String OUTPUT_BUFFER_TAG = "OutputBuffer";
    private static final ItemDataKey<List<ItemStack>> ITEM_OUTPUT_BUFFER = new ItemDataKey<>(
        EnderIO.rl("crafter_output_buffer"), StackDataCodecs.OPTIONAL_ITEM.listOf(), false);

    public static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.CRAFTER_CAPACITY);
    public static final QuadraticScalable ENERGY_USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.CRAFTER_USAGE);
    public static final MultiSlotAccess INPUT = new MultiSlotAccess();
    public static final SingleSlotAccess OUTPUT = new SingleSlotAccess();
    public static final MultiSlotAccess GHOST = new MultiSlotAccess();
    public static final SingleSlotAccess PREVIEW = new SingleSlotAccess();

    @Nullable
    private CraftingRecipe recipe;
    @Nullable
    private RecipeManager recipeManager;
    private final Queue<ItemStack> outputBuffer = new ArrayDeque<>();

    public CrafterBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.CRAFTER.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);
    }

    private CraftingRecipeInput getCraftingInput(MultiSlotAccess sourceSlots) {
        return new CraftingRecipeInput(3, 3, sourceSlots.getItemStacks(getInventory()));
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        if (GHOST.contains(slot)) {
            updateRecipe();
        }
    }

    private void updateRecipe() {
        if (level == null || level.isClientSide()) {
            return;
        }
        var input = getCraftingInput(GHOST);
        recipeManager = level.getRecipeManager();
        recipe = recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);

        var preview = recipe == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
        if (!ItemStack.matches(PREVIEW.getItemStack(this), preview)) {
            PREVIEW.setStackInSlot(this, preview.copy());
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrafterMenu(containerId, inventory, this);
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .capacitor()
                .setStackLimit(1)
                .inputSlot(9, this::acceptSlotInput)
                .slotAccess(INPUT)
                .setStackLimit(64)
                .outputSlot(1)
                .slotAccess(OUTPUT)
                .setStackLimit(1)
                .ghostSlot(9)
                .slotAccess(GHOST)
                .previewSlot()
                .slotAccess(PREVIEW)
                .build();
    }

    private boolean acceptSlotInput(int slot, ItemStack stack) {
        return ItemStack.isSameItem(this.getInventory().getStackInSlot(slot + 10), stack);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateRecipe();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        outputBuffer.clear();
        var savedOutputs = tag.getList(OUTPUT_BUFFER_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < savedOutputs.size(); i++) {
            var output = StackDataCodecs.loadItem(savedOutputs.getCompound(i));
            if (!output.isEmpty()) {
                outputBuffer.add(output);
            }
        }
        updateRecipe();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!outputBuffer.isEmpty()) {
            var savedOutputs = new ListTag();
            for (var output : outputBuffer) {
                savedOutputs.add(StackDataCodecs.saveItem(output));
            }
            tag.put(OUTPUT_BUFFER_TAG, savedOutputs);
        }
    }

    @Override
    protected void readItemData(ItemStack stack) {
        super.readItemData(stack);
        var savedOutputs = ITEM_OUTPUT_BUFFER.get(stack);
        if (savedOutputs != null) {
            outputBuffer.clear();
            for (var output : savedOutputs) {
                if (!output.isEmpty()) {
                    outputBuffer.add(output);
                }
            }
        }
    }

    @Override
    protected void writeItemData(ItemStack stack) {
        super.writeItemData(stack);
        if (!outputBuffer.isEmpty()) {
            ITEM_OUTPUT_BUFFER.set(stack, List.copyOf(outputBuffer));
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(OUTPUT_BUFFER_TAG);
    }

    @Override
    public void serverTick() {
        if (level.getGameTime() % 20 == 0) {
            var currentRecipes = level.getRecipeManager();
            if (currentRecipes != recipeManager || recipe != null && currentRecipes.byKey(recipe.getId()).orElse(null) != recipe) {
                updateRecipe();
            }
            if (canAct()) {
                tryCraft();
            }
        }

        super.serverTick();
        processOutputBuffer();
    }

    @Override
    public boolean isActive() {
        return canAct() && hasEnergy();
    }

    private void tryCraft() {
        int cost = MachinesConfig.COMMON.ENERGY.CRAFTING_RECIPE_COST.get();
        if (recipe == null || !outputBuffer.isEmpty() || getEnergyStorage().consumeEnergy(cost, true) < cost) {
            return;
        }
        var activeRecipe = level instanceof ServerLevel server
                ? StageRules.resolveCrafterRecipe(server, getMachineOwner(), recipe) : recipe;
        if (activeRecipe == null) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            if (!ItemStack.isSameItem(INPUT.get(i).getItemStack(this), GHOST.get(i).getItemStack(this))) {
                return;
            }
        }
        var input = getCraftingInput(INPUT);
        if (!activeRecipe.matches(input, level)) {
            return;
        }

        var result = activeRecipe.assemble(input, level.registryAccess());
        if (result.isEmpty() || !canMergeOutput(result)) {
            return;
        }
        var remainders = activeRecipe.getRemainingItems(input);
        outputBuffer.add(result.copy());
        for (var remainder : remainders) {
            if (!remainder.isEmpty()) {
                outputBuffer.add(remainder.copy());
            }
        }
        for (var slot : INPUT.getAccesses()) {
            var stack = slot.getItemStack(this);
            if (!stack.isEmpty()) {
                slot.setStackInSlot(this, stack.copyWithCount(stack.getCount() - 1));
            }
        }
        getEnergyStorage().consumeEnergy(cost, false);
    }

    private void processOutputBuffer() {
        var next = outputBuffer.peek();
        if (next != null && canMergeOutput(next)) {
            outputBuffer.remove();
            int count = OUTPUT.getItemStack(this).getCount() + next.getCount();
            OUTPUT.setStackInSlot(this, next.copyWithCount(count));
        }
    }

    private boolean canMergeOutput(ItemStack item) {
        var output = OUTPUT.getItemStack(this);
        int limit = Math.min(getInventory().getSlotLimit(OUTPUT.getIndex()), item.getMaxStackSize());
        return (output.isEmpty() || ItemStack.isSameItemSameTags(output, item))
            && output.getCount() <= limit - item.getCount();
    }
}

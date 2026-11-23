package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.CoreNBTKeys;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.energy.MachineEnergyStorage;
import com.enderio.enderio.foundation.recipe.RecipeInputCache;
import com.enderio.enderio.foundation.task.CraftingMachineTask;
import com.enderio.enderio.foundation.task.host.CraftingMachineTaskHost;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class PrimitiveAlloySmelterBlockEntity extends MachineBlockEntity {
    public static final SingleSlotAccess FUEL = new SingleSlotAccess();
    public static final MultiSlotAccess INPUTS = new MultiSlotAccess();
    public static final SingleSlotAccess OUTPUT = new SingleSlotAccess();

    private static final RecipeInputCache<AlloySmeltingRecipe.Input, AlloySmeltingRecipe> RECIPES =
        new RecipeInputCache<>(EIORecipes.PRIMITIVE_ALLOY_SMELTING.type());
    private static final String OLD_RECIPE_PATH = "alloy_smelting/";
    private static final String RECIPE_PATH = "primitive_alloy_smelting/";
    private static final Logger LOGGER = LogUtils.getLogger();

    private int burnTime;
    private int burnDuration;
    // Expose fuel work only to the shared crafting task.
    private final MachineEnergyStorage fuelWork = new MachineEnergyStorage(this, EnergyIOMode.Input, () -> 0,
        () -> MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_PRODUCTION.get() / 2) {
        @Override
        public int consumeEnergy(int energy, boolean simulate) {
            return burnTime > 0 ? Math.min(Math.max(0, energy), getMaxEnergyUse()) : 0;
        }
    };
    private final CraftingMachineTaskHost<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> crafting =
        new CraftingMachineTaskHost<>(this, this::canAct, EIORecipes.PRIMITIVE_ALLOY_SMELTING.type().get(),
            this::createTask, this::recipeInput) {
            @Override
            @Nullable
            protected CraftingMachineTask<AlloySmeltingRecipe, AlloySmeltingRecipe.Input> loadTask(
                HolderLookup.Provider lookup, CompoundTag saved) {
                try {
                    return super.loadTask(lookup, migrateTask(saved));
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Unable to restore primitive alloy smelter job at {}: {}", getBlockPos(), ex.getMessage());
                    return null;
                }
            }
        };

    public PrimitiveAlloySmelterBlockEntity(BlockPos pos, BlockState state) {
        super(EIOBlockEntities.PRIMITIVE_ALLOY_SMELTER.get(), pos, state, true);
    }

    @Override
    protected MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
            .inputSlot((slot, stack) -> ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0)
            .slotAccess(FUEL)
            .inputSlot(3, (slot, stack) -> level != null
                && RECIPES.hasValidRecipeIf(level.getRecipeManager(), getInventory(), INPUTS, slot, stack))
            .slotAccess(INPUTS)
            .outputSlot()
            .slotAccess(OUTPUT)
            .build();
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        crafting.newTaskAvailable();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        crafting.onLevelReady();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (canAct()) {
            crafting.tick();
        }
        if (burnTime > 0) {
            burnTime--;
            setChanged();
        }
        if (canAct() && burnTime == 0 && crafting.hasTask() && crafting.getProgress() < 1 && crafting.permitsCurrentRecipe()) {
            igniteFuel();
        }
    }

    private void igniteFuel() {
        ItemStack fuel = FUEL.getItemStack(this);
        int duration = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
        if (duration <= 0) {
            return;
        }
        ItemStack remainder = fuel.getCraftingRemainingItem();
        if (!remainder.isEmpty() && fuel.getCount() > 1) {
            return;
        }
        burnDuration = duration;
        burnTime = duration;
        FUEL.setStackInSlot(this, remainder.isEmpty() ? fuel.copyWithCount(fuel.getCount() - 1) : remainder);
        setChanged();
    }

    @Override
    public boolean isActive() {
        return canAct() && burnTime > 0;
    }

    public float getBurnProgress() {
        return burnDuration == 0 ? 0 : burnTime / (float) burnDuration;
    }

    public float getCraftingProgress() {
        return crafting.getProgress();
    }

    private AlloySmeltingRecipe.Input recipeInput() {
        return new AlloySmeltingRecipe.Input(INPUTS.getItemStacks(getInventory()), 1);
    }

    private AlloySmelterBlockEntity.AlloySmeltingMachineTask createTask(Level level, AlloySmeltingRecipe.Input input,
        @Nullable AlloySmeltingRecipe recipe) {
        return new AlloySmelterBlockEntity.AlloySmeltingMachineTask(level, getInventory(), fuelWork, input, INPUTS, OUTPUT, recipe);
    }

    @Override
    public PrimitiveAlloySmelterMenu createMenu(int id, Inventory inventory, Player player) {
        return new PrimitiveAlloySmelterMenu(id, inventory, this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        burnTime = Math.max(0, tag.getInt(MachineNBTKeys.BURN_TIME));
        burnDuration = Math.max(burnTime, tag.getInt(MachineNBTKeys.BURN_DURATION));
        crafting.load(level == null ? RegistryAccess.EMPTY : level.registryAccess(), tag);
        if (level != null && !level.isClientSide) {
            updateRedstonePower();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(MachineNBTKeys.BURN_TIME, burnTime);
        tag.putInt(MachineNBTKeys.BURN_DURATION, burnDuration);
        crafting.save(level == null ? RegistryAccess.EMPTY : level.registryAccess(), tag);
    }

    private static CompoundTag migrateTask(CompoundTag saved) {
        var id = ResourceLocation.tryParse(saved.getString("RecipeId"));
        if (id == null || !EnderIO.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith(OLD_RECIPE_PATH)) {
            return saved;
        }
        var task = saved.copy();
        task.putString("RecipeId", EnderIO.rl(RECIPE_PATH + id.getPath().substring(OLD_RECIPE_PATH.length())).toString());
        if (task.getBoolean("HasDeterminedOutputs")) {
            var outputs = task.getList("Outputs", Tag.TAG_COMPOUND);
            if (outputs.size() != 1) {
                throw new IllegalArgumentException("Expected one saved alloy output for " + id);
            }
            var savedStack = outputs.getCompound(0).getCompound(CoreNBTKeys.ITEM);
            var outputId = ResourceLocation.tryParse(savedStack.getString("id"));
            var item = outputId == null ? null : ForgeRegistries.ITEMS.getValue(outputId);
            if (item == null) {
                throw new IllegalArgumentException("Unknown saved alloy output " + outputId);
            }
            savedStack.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
            if (StackDataCodecs.loadItem(savedStack).isEmpty()) {
                throw new IllegalArgumentException("Empty saved alloy output for " + id);
            }
        }
        return task;
    }
}

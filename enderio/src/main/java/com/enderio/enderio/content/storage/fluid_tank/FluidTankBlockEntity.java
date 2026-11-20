package com.enderio.enderio.content.storage.fluid_tank;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.fluid.FluidItemInteractive;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.foundation.util.SizedFluidIngredientHelper;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// TODO: Rewrite this with tasks?
//       Could implement a task for each thing it currently has in the If's
public abstract class FluidTankBlockEntity extends MachineBlockEntity implements FluidItemInteractive, FluidTankUser {

    public static class Standard extends FluidTankBlockEntity {
        public static final int CAPACITY = 16 * FluidType.BUCKET_VOLUME;

        public Standard(BlockPos worldPosition, BlockState blockState) {
            super(EIOBlockEntities.FLUID_TANK.get(), worldPosition, blockState);
        }

        @Override
        public MachineTankLayout getTankLayout() {
            return new MachineTankLayout.Builder().tank(TANK, CAPACITY).build();
        }
    }

    public static class Enhanced extends FluidTankBlockEntity {
        public static final int CAPACITY = 32 * FluidType.BUCKET_VOLUME;

        public Enhanced(BlockPos worldPosition, BlockState blockState) {
            super(EIOBlockEntities.PRESSURIZED_FLUID_TANK.get(), worldPosition, blockState);
        }

        @Override
        public MachineTankLayout getTankLayout() {
            return new MachineTankLayout.Builder().tank(TANK, CAPACITY).build();
        }

    }

    private final MachineFluidHandler fluidHandler;
    public static final TankAccess TANK = new TankAccess();

    public static final SingleSlotAccess FLUID_FILL_INPUT = new SingleSlotAccess();
    public static final SingleSlotAccess FLUID_FILL_OUTPUT = new SingleSlotAccess();
    public static final SingleSlotAccess FLUID_DRAIN_INPUT = new SingleSlotAccess();
    public static final SingleSlotAccess FLUID_DRAIN_OUTPUT = new SingleSlotAccess();

    public FluidTankBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState, true);
        fluidHandler = createFluidHandler();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Native loading restores fluid before the level is available to update its light.
        checkFluidLight();
        if (!level.isClientSide()) {
            updateMachineState(MachineState.EMPTY_TANK, getFluidTank().isEmpty());
        }
    }

    @Override
    public boolean isActive() {
        // No active state on tanks
        return false;
    }

    private TankRecipe.Input createRecipeInput() {
        return new TankRecipe.Input(FLUID_DRAIN_INPUT.getItemStack(getInventory()),
                FLUID_FILL_INPUT.getItemStack(getInventory()), TANK.getTank(this));
    }

    @Override
    public void serverTick() {
        if (canAct(5)) {
            fillInternal();
            drainInternal();
            tryTankRecipe();
            tryMendTool();
        }

        super.serverTick();
    }

    // region Inventory

    public boolean acceptItemFill(ItemStack item) {
        // bucket types
        IFluidHandlerItem fluidHandlerCap = item.copyWithCount(1).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandlerCap != null && !fluidHandlerCap.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return true;
        }

        // fill recipes
        if (level != null) {
            List<TankRecipe> allRecipes = level.getRecipeManager()
                    .getAllRecipesFor(EIORecipes.TANK.type().get());
            return allRecipes.stream()
                    .anyMatch((recipe) -> recipe.mode() == TankRecipe.Mode.EMPTY
                            && recipe.input().test(item));
        }

        return false;
    }

    public boolean acceptItemDrain(ItemStack item) {
        FluidStack fluid = TANK.getFluid(this);

        // Receive items that can accept our current fluid as input -or- if our current contents are empty.
        IFluidHandlerItem fluidHandlerCap = item.copyWithCount(1).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (fluidHandlerCap != null && (fluid.isEmpty() || fluidHandlerCap.fill(EnderFluidUtil.copyWithAmount(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE) > 0)) {
            return true;
        }

        // Mending

        if (item.isDamageableItem() && !fluid.isEmpty() && fluid.getFluid().is(com.enderio.enderio.foundation.tag.EIOTags.Fluids.EXPERIENCE)) {
            var mendingEnchantment = Enchantments.MENDING;

            if (item.getEnchantmentLevel(mendingEnchantment) > 0) {
                return true;
            }
        }

        // drain recipes
        if (level != null) {
            List<TankRecipe> allRecipes = level.getRecipeManager()
                    .getAllRecipesFor(EIORecipes.TANK.type().get());
            return allRecipes.stream()
                    .anyMatch((recipe) -> recipe.mode() == TankRecipe.Mode.FILL
                            && recipe.input().test(item));
        }

        return false;
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .inputSlot((slot, stack) -> acceptItemFill(stack))
                .slotAccess(FLUID_FILL_INPUT)
                .outputSlot()
                .slotAccess(FLUID_FILL_OUTPUT)
                .inputSlot((slot, stack) -> acceptItemDrain(stack))
                .slotAccess(FLUID_DRAIN_INPUT)
                .outputSlot()
                .slotAccess(FLUID_DRAIN_OUTPUT)
                .build();
    }

    // endregion

    @Override
    public MachineFluidHandler getFluidHandler() {
        return this.fluidHandler;
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                checkFluidLight();
                setChanged();
                super.onContentsChanged(slot);
                updateMachineState(MachineState.EMPTY_TANK, TANK.getFluidAmount(this) <= 0);
                // Native chunk loading restores fluid NBT before assigning the level.
                if (level != null) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
                }
            }
        };
    }

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
    }


    private void fillInternal() {
        InternalTankTasks.fillInternal(this, TANK, FLUID_FILL_INPUT, FLUID_FILL_OUTPUT);
    }

    private void drainInternal() {
        InternalTankTasks.drainInternal(this, TANK, FLUID_DRAIN_INPUT, FLUID_DRAIN_OUTPUT);
    }

    private void tryMendTool() {
        InternalTankTasks.tryMendTool(this, TANK, FLUID_DRAIN_INPUT, FLUID_DRAIN_OUTPUT);
    }

    // endregion

    private void tryTankRecipe() {
        var input = createRecipeInput();
        if (input.fillItem().isEmpty() && input.emptyItem().isEmpty()) {
            return;
        }
        // A datapack reload can replace the matching recipe while a tank is paused.
        var recipe = level.getRecipeManager().getRecipeFor(EIORecipes.TANK.type().get(), input, level).orElse(null);
        if (recipe == null) {
            return;
        }

        ItemStack recipeResultStack = recipe.output().copy();
        switch (recipe.mode()) {
        case EMPTY -> {
            if (!FLUID_FILL_OUTPUT.insertItem(this, recipeResultStack, true).isEmpty()) {
                return;
            }

            // Get the first matching fluid that fits in the tank.
            List<FluidStack> possibleFluids = SizedFluidIngredientHelper.getFluidStacksInPreferredOrder(recipe.fluid());
            for (FluidStack fluidToTry : possibleFluids) {
                int filled = TANK.fill(this, fluidToTry, IFluidHandler.FluidAction.SIMULATE);
                if (filled == recipe.fluid().amount()) {
                    TANK.fill(this, fluidToTry, IFluidHandler.FluidAction.EXECUTE);
                    FLUID_FILL_INPUT.extractItem(this, 1, false);
                    FLUID_FILL_OUTPUT.insertItem(this, recipeResultStack, false);
                    break;
                }
            }
        }
        case FILL -> {
            if (!FLUID_DRAIN_OUTPUT.insertItem(this, recipeResultStack, true).isEmpty()) {
                return;
            }

            var drained = TANK.drain(this, recipe.fluid().amount(), IFluidHandler.FluidAction.SIMULATE);
            if (!recipe.fluid().test(drained) || drained.getAmount() != recipe.fluid().amount()) {
                return;
            }

            TANK.drain(this, recipe.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
            FLUID_DRAIN_INPUT.extractItem(this, 1, false);
            FLUID_DRAIN_OUTPUT.insertItem(this, recipeResultStack, false);
        }
        default -> throw new NotImplementedException();
        }
    }

    private void checkFluidLight() {
        if (level != null) {
            level.getLightEngine().checkBlock(worldPosition);
        }
    }

    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FluidTankMenu(containerId, playerInventory, this);
    }

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
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);
        saveTank(registries, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);
    }

    // endregion
}

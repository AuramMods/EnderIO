package com.enderio.enderio.gametests.util;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.foundation.io.energy.IMachineEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.function.Consumer;

public class EnderGameTestHelper {
    private final GameTestHelper delegate;

    public EnderGameTestHelper(GameTestHelper delegate) {
        this.delegate = delegate;
    }

    public ServerLevel getLevel() { return delegate.getLevel(); }
    public GameTestSequence startSequence() { return delegate.startSequence(); }
    public BlockPos absolutePos(BlockPos pos) { return delegate.absolutePos(pos); }
    public BlockState getBlockState(BlockPos pos) { return delegate.getBlockState(pos); }
    public void setBlock(int x, int y, int z, BlockState state) { delegate.setBlock(x, y, z, state); }
    public void setBlock(int x, int y, int z, Block block) { delegate.setBlock(x, y, z, block); }
    public void setBlock(BlockPos pos, BlockState state) { delegate.setBlock(pos, state); }
    public void setBlock(BlockPos pos, Block block) { delegate.setBlock(pos, block); }
    public void destroyBlock(BlockPos pos) { delegate.destroyBlock(pos); }
    public void randomTick(BlockPos pos) { delegate.randomTick(pos); }
    public void assertBlockPresent(Block block, BlockPos pos) { delegate.assertBlockPresent(block, pos); }
    public void assertBlockPresent(Block block, int x, int y, int z) { delegate.assertBlockPresent(block, x, y, z); }
    public void assertItemEntityPresent(Item item, BlockPos pos, double radius) { delegate.assertItemEntityPresent(item, pos, radius); }
    public void assertContainerEmpty(int x, int y, int z) { delegate.assertContainerEmpty(new BlockPos(x, y, z)); }

    public <T extends BlockEntity> T getBlockEntity(int x, int y, int z, Class<T> type) {
        var entity = delegate.getBlockEntity(new BlockPos(x, y, z));
        if (!type.isInstance(entity)) throw new GameTestAssertException("Expected " + type.getName() + " at " + x + "," + y + "," + z);
        return type.cast(entity);
    }

    public <T> void assertValueEqual(T actual, T expected, String description) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new GameTestAssertException(description + ": expected " + expected + ", got " + actual);
        }
    }

    private <T> @Nullable T capability(Capability<T> capability, BlockPos absolute, @Nullable Direction side) {
        var entity = getLevel().getBlockEntity(absolute);
        return entity == null ? null : entity.getCapability(capability, side).orElse(null);
    }


    public void changeIoConfig(int x, int y, int z, Consumer<IOConfigurable> configurer) {
        var blockEntity = getLevel().getBlockEntity(absolutePos(new BlockPos(x, y, z)));
        if (blockEntity instanceof IOConfigurable ioConfigurable) {
            configurer.accept(ioConfigurable);
        } else {
            throw new GameTestAssertException("No IOConfigurable at " + x + "," + y + "," + z);
        }
    }

    // region Item Storage

    public void insertIntoContainer(int x, int y, int z, Item item, int count) {
        insertIntoContainer(x, y, z, new ItemStack(item, count));
    }

    public void insertIntoContainer(int x, int y, int z, ItemStack itemStack) {
        var itemHandler = capability(ForgeCapabilities.ITEM_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (itemHandler == null) {
            throw new GameTestAssertException("No item handler at " + x + "," + y + "," + z);
        }

        int count = itemStack.getCount();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            int toInsert = Math.min(count, itemHandler.getSlotLimit(i));
            var stackToInsert = itemStack.copyWithCount(toInsert);
            var remainder = itemHandler.insertItem(i, stackToInsert, false);
            count -= toInsert - remainder.getCount();
            if (count <= 0) {
                return;
            }
        }

        if (count > 0) {
            throw new GameTestAssertException(
                "Could not insert " + itemStack.getCount() + " " + itemStack.getItem() + " into container at " + x + "," + y + "," + z);
        }
    }

    public void assertContainerHasExactly(int x, int y, int z, Item item, int count) {
        var itemHandler = capability(ForgeCapabilities.ITEM_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (itemHandler == null) {
            throw new GameTestAssertException("No item handler at " + x + "," + y + "," + z);
        }

        int foundCount = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            var stack = itemHandler.getStackInSlot(i);
            if (stack.is(item)) {
                foundCount += stack.getCount();
            }
        }

        if (foundCount != count) {
            throw new GameTestAssertException("Expected " + count + " of " + item + " in container at " + x + "," + y
                + "," + z + " but found " + foundCount);
        }
    }

    public void assertContainerHasAtleast(int x, int y, int z, Item item, int count) {
        var itemHandler = capability(ForgeCapabilities.ITEM_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (itemHandler == null) {
            throw new GameTestAssertException("No item handler at " + x + "," + y + "," + z);
        }

        int foundCount = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            var stack = itemHandler.getStackInSlot(i);
            if (stack.is(item)) {
                foundCount += stack.getCount();
            }
        }

        if (foundCount < count) {
            throw new GameTestAssertException("Expected " + count + " of " + item + " in container at " + x + "," + y
                + "," + z + " but found " + foundCount);
        }
    }

    /**
     * Insert an item into a specific slot of a container.
     */
    public void insertItemIntoSlot(int x, int y, int z, int slot, ItemStack itemStack) {
        var itemHandler = capability(ForgeCapabilities.ITEM_HANDLER, absolutePos(new BlockPos(x, y, z)), null);
        if (itemHandler == null) {
            throw new GameTestAssertException("No item handler at " + x + "," + y + "," + z);
        }

        var remainder = itemHandler.insertItem(slot, itemStack, false);
        if (!remainder.isEmpty()) {
            throw new GameTestAssertException(
                "Could not insert " + itemStack.getCount() + " " + itemStack.getItem() + " into slot " + slot + " at " + x + "," + y + "," + z +
                    ", " + remainder.getCount() + " items remained");
        }
    }

    /**
     * Get the item stack in a specific slot of a container.
     */
    public ItemStack getItemInSlot(int x, int y, int z, int slot) {
        var itemHandler = capability(ForgeCapabilities.ITEM_HANDLER, absolutePos(new BlockPos(x, y, z)), null);
        if (itemHandler == null) {
            throw new GameTestAssertException("No item handler at " + x + "," + y + "," + z);
        }

        return itemHandler.getStackInSlot(slot);
    }

    /**
     * Assert that a specific slot contains a specific item.
     */
    public void assertSlotHasItem(int x, int y, int z, int slot, Item expectedItem) {
        ItemStack stack = getItemInSlot(x, y, z, slot);
        if (!stack.is(expectedItem)) {
            throw new GameTestAssertException(
                "Expected " + expectedItem + " in slot " + slot + " at " + x + "," + y + "," + z +
                    ", but found: " + (stack.isEmpty() ? "empty" : stack.getItem()));
        }
    }

    /**
     * Assert that a specific slot is empty.
     */
    public void assertSlotHasNoItem(int x, int y, int z, int slot) {
        ItemStack stack = getItemInSlot(x, y, z, slot);
        if (!stack.isEmpty()) {
            throw new GameTestAssertException(
                "Expected empty slot " + slot + " at " + x + "," + y + "," + z +
                    ", but found: " + stack.getItem());
        }
    }

    // endregion

    public void fillContainer(int x, int y, int z, FluidStack stack) {
        var fluidHandler = capability(ForgeCapabilities.FLUID_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (fluidHandler == null) {
            throw new GameTestAssertException("No fluid handler at " + x + "," + y + "," + z);
        }

        int filled = fluidHandler.fill(stack, IFluidHandler.FluidAction.EXECUTE);

        if (filled < stack.getAmount()) {
            throw new GameTestAssertException(
                "Could not fill tank with all " + stack.getAmount() + " of the fluid into container at " + x + "," + y + "," + z);
        }
    }

    public void fillContainer(int x, int y, int z, Fluid fluid, int amount) {
        fillContainer(x, y, z, new FluidStack(fluid, amount));
    }

    public long getAmountInHandler(int x, int y, int z, Fluid fluid) {
        var fluidHandler = capability(ForgeCapabilities.FLUID_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (fluidHandler == null) {
            throw new GameTestAssertException("No fluid handler at " + x + "," + y + "," + z);
        }

        long totalAmount = 0;
        for (int i = 0; i < fluidHandler.getTanks(); i++) {
            if (fluidHandler.getFluidInTank(i).getFluid() == fluid) {
                totalAmount += fluidHandler.getFluidInTank(i).getAmount();
            }
        }

        return totalAmount;
    }

    public void assertContainerHasExactly(int x, int y, int z, FluidStack stack) {
        var fluidHandler = capability(ForgeCapabilities.FLUID_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (fluidHandler == null) {
            throw new GameTestAssertException("No fluid handler at " + x + "," + y + "," + z);
        }

        int foundAmount = fluidHandler.drain(copyWithAmount(stack, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE).getAmount();

        if (foundAmount != stack.getAmount()) {
            throw new GameTestAssertException("Expected " + stack.getAmount() + " of " + stack + " in tank at " + x + "," + y
                + "," + z + " but found " + foundAmount);
        }
    }

    public void assertContainerHasExactly(int x, int y, int z, Fluid fluid, int amount) {
        var fluidHandler = capability(ForgeCapabilities.FLUID_HANDLER, absolutePos(new BlockPos(x, y, z)),
            null);
        if (fluidHandler == null) {
            throw new GameTestAssertException("No fluid handler at " + x + "," + y + "," + z);
        }

        int foundAmount = fluidHandler.drain(new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE).getAmount();

        if (foundAmount != amount) {
            throw new GameTestAssertException("Expected " + amount + " of " + fluid + " in tank at " + x + "," + y
                + "," + z + " but found " + foundAmount);
        }
    }

    /**
     * Provide energy to a block entity by directly accessing its energy capability.
     */
    public void provideEnergyExact(int x, int y, int z, int amount) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        // Short path for MachineEnergyHandler to avoid insertion limits.
        if (energyHandler instanceof IMachineEnergyStorage machineEnergyStorage) {
            int inserted = machineEnergyStorage.addEnergy(amount);
            if (inserted != amount) {
                throw new GameTestAssertException("Could not insert all " + amount + " energy into block at " + x + "," + y + "," + z + ", only inserted " + inserted);
            }

            return;
        }

        int inserted = energyHandler.receiveEnergy(amount, false);
        if (inserted != amount) {
            throw new GameTestAssertException("Could not insert all " + amount + " energy into block at " + x + "," + y + "," + z + ", only inserted " + inserted);
        }
    }

    /**
     * Provide energy to a block entity by directly accessing its energy capability.
     */
    public void provideEnergy(int x, int y, int z, int amount) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        // Short path for MachineEnergyHandler to avoid insertion limits.
        if (energyHandler instanceof IMachineEnergyStorage machineEnergyStorage) {
            machineEnergyStorage.addEnergy(amount);
            return;
        }

        energyHandler.receiveEnergy(amount, false);
    }

    /**
     * Assert that a block has exactly the expected amount of energy stored.
     */
    public void assertEnergyStored(int x, int y, int z, int expectedAmount) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        int stored = energyHandler.getEnergyStored();
        if (stored != expectedAmount) {
            throw new GameTestAssertException("Expected " + expectedAmount + " energy in block at " + x + "," + y + "," + z + ", but found " + stored);
        }
    }

    /**
     * Assert that a block has at least the expected minimum amount of energy stored.
     */
    public void assertEnergyStoredAtLeast(int x, int y, int z, int minAmount) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        int stored = energyHandler.getEnergyStored();
        if (stored < minAmount) {
            throw new GameTestAssertException("Expected at least " + minAmount + " energy in block at " + x + "," + y + "," + z + ", but found " + stored);
        }
    }

    /**
     * Assert that a block has at most the expected maximum amount of energy stored.
     */
    public void assertEnergyStoredAtMost(int x, int y, int z, int maxAmount) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        int stored = energyHandler.getEnergyStored();
        if (stored > maxAmount) {
            throw new GameTestAssertException("Expected at most " + maxAmount + " energy in block at " + x + "," + y + "," + z + ", but found " + stored);
        }
    }

    /**
     * Get the amount of energy stored in a block.
     */
    public int getEnergyStored(int x, int y, int z) {
        var energyHandler = capability(ForgeCapabilities.ENERGY, absolutePos(new BlockPos(x, y, z)), null);
        if (energyHandler == null) {
            throw new GameTestAssertException("No energy handler at " + x + "," + y + "," + z);
        }

        return energyHandler.getEnergyStored();
    }
    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        var copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}

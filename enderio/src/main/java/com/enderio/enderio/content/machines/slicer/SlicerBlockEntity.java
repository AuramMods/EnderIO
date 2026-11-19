package com.enderio.enderio.content.machines.slicer;

import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.client.SoundHandler;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.task.CraftingMachineTask;
import com.enderio.enderio.foundation.task.PoweredCraftingMachineTask;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.Nullable;

public class SlicerBlockEntity extends PoweredMachineBlockEntity {

    public static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.SLICER_CAPACITY);
    public static final QuadraticScalable USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.SLICER_USAGE);

    public static final SingleSlotAccess OUTPUT = new SingleSlotAccess();
    public static final MultiSlotAccess INPUTS = new MultiSlotAccess();
    public static final SingleSlotAccess AXE = new SingleSlotAccess();
    public static final SingleSlotAccess SHEARS = new SingleSlotAccess();

    private final CraftingMachineTaskHost<SlicingRecipe, SlicingRecipe.Input> craftingTaskHost;

    public SlicerBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.SLICE_AND_SPLICE.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, CAPACITY, USAGE);

        craftingTaskHost = new CraftingMachineTaskHost<>(this, this::hasEnergy, EIORecipes.SLICING.type().get(),
                this::createTask, this::createRecipeInput) {
            @Override
            protected @Nullable CraftingMachineTask<SlicingRecipe, SlicingRecipe.Input> getNewTask() {
                if (!hasTools()) {
                    return null;
                }

                return super.getNewTask();
            }
        };
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SlicerMenu(containerId, inventory, this);
    }

    @Override
    public void serverTick() {
        super.serverTick();

        if (canAct() && hasTools()) {
            craftingTaskHost.tick();
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ProgressMachineBlock.POWERED)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            SoundHandler.playSound(pos, EIOSounds.SLICER.get(), SoundSource.BLOCKS, MachinesConfig.CLIENT.MACHINE_VOLUME.get(), 1.0f, random, x, y, z);


            Direction direction = state.getValue(ProgressMachineBlock.FACING);
            Direction.Axis axis = direction.getAxis();
            double r = 0.52;
            double ss = random.nextDouble() * 0.8 - 0.4;
            double dx = axis == Direction.Axis.X ? direction.getStepX() * r : ss;
            double dy = random.nextDouble() * 6.0 / 16.0 + 7.0 / 16.0;
            double dz = axis == Direction.Axis.Z ? direction.getStepZ() * r : ss;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
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
        return MachineInventoryLayout.builder()
                .setStackLimit(1) // Force all input slots to have 1 output
                .inputSlot(6, this::isValidInput)
                .slotAccess(INPUTS)
                .inputSlot(this::validAxe)
                .slotAccess(AXE)
                .inputSlot((slot, stack) -> stack.getItem() instanceof ShearsItem)
                .slotAccess(SHEARS)
                .setStackLimit(64) // Reset originalStack limit
                .outputSlot()
                .slotAccess(OUTPUT)
                .capacitor()
                .build();
    }

    private boolean isValidInput(int index, ItemStack stack) {
        if (level == null) {
            return false;
        }
        return SlicerRecipeManager.isSlicerValid(level.getRecipeManager(), stack, index);
    }

    private boolean validAxe(int slot, ItemStack stack) {
        if (stack.getItem() instanceof AxeItem axeItem) {
            return axeItem.getTier() != Tiers.WOOD;
        }
        return false;
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        craftingTaskHost.newTaskAvailable();
    }

    private SlicingRecipe.Input createRecipeInput() {
        return new SlicingRecipe.Input(INPUTS.getItemStacks(getInventory()));
    }

    // endregion

    // region Crafting Task

    private net.minecraftforge.common.util.FakePlayer toolPlayer;

    private net.minecraftforge.common.util.FakePlayer getToolPlayer(ServerLevel level) {
        if (toolPlayer == null || toolPlayer.level() != level) {
            toolPlayer = new net.minecraftforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(getMachineOwnerOrRandom(), "enderio:slicer:" + worldPosition));
        }
        toolPlayer.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        return toolPlayer;
    }

    public float getCraftingProgress() {
        return craftingTaskHost.getProgress();
    }

    private boolean hasTools() {
        return !AXE.getItemStack(this).isEmpty() && !SHEARS.getItemStack(this).isEmpty();
    }

    @Override
    public boolean isActive() {
        return canAct() && hasTools() && hasEnergy() && craftingTaskHost.hasTask();
    }

    protected PoweredCraftingMachineTask<SlicingRecipe, SlicingRecipe.Input> createTask(Level level,
            SlicingRecipe.Input recipeInput, @Nullable SlicingRecipe recipe) {
        return new PoweredCraftingMachineTask<>(level, getInventory(), getEnergyStorage(), recipeInput, OUTPUT,
                recipe) {
            @Override
            protected void consumeInputs(SlicingRecipe recipe) {
                // Deduct ingredients
                MachineInventory inv = getInventory();
                for (SingleSlotAccess access : INPUTS.getAccesses()) {
                    access.getItemStack(inv).shrink(1);
                }

                if (level instanceof ServerLevel serverLevel) {
                    AXE.getItemStack(inv).hurtAndBreak(1, getToolPlayer(serverLevel), item -> {
                    });
                    SHEARS.getItemStack(inv).hurtAndBreak(1, getToolPlayer(serverLevel), item -> {
                    });
                }
            }
        };
    }

    // endregion

    // region Serialization

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        craftingTaskHost.save(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        craftingTaskHost.load(lookupProvider, tag);
    }

    @Override
    protected void readItemData(ItemStack stack) {
        super.readItemData(stack);
        var carried = EIODataComponents.SLICER_TASK.get(stack);
        if (carried == null) {
            carried = BlockItem.getBlockEntityData(stack);
        }
        if (carried == null) {
            craftingTaskHost.newTaskAvailable();
            return;
        }

        // Bind the saved recipe to the restored inputs after the portable inventory has been applied.
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        craftingTaskHost.load(lookupProvider, carried);
    }

    @Override
    protected void writeItemData(ItemStack stack) {
        super.writeItemData(stack);
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        var carried = new CompoundTag();
        craftingTaskHost.save(lookupProvider, carried);
        if (carried.isEmpty()) {
            EIODataComponents.SLICER_TASK.reset(stack);
        } else {
            EIODataComponents.SLICER_TASK.set(stack, carried);
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove("Task");
    }

    // endregion
}

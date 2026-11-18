package com.enderio.enderio.content.machines.obelisks.xp;

import com.enderio.core.common.capability.EnderFluidUtil;
import net.minecraft.world.item.ItemStack;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.entity.MachineBlockEntity;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class XPObeliskBlockEntity extends MachineBlockEntity implements FluidTankUser {

    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();

    private static final Logger LOGGER = LogUtils.getLogger();

    public XPObeliskBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.XP_OBELISK.get(), worldPosition, blockState, true);
        fluidHandler = createFluidHandler();
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new XPObeliskMenu(containerId, playerInventory, this);
    }

    @Override
    public MachineTankLayout getTankLayout() {
        return new MachineTankLayout.Builder()
                .tank(TANK, Integer.MAX_VALUE, fluidStack -> fluidStack.getFluid().is(com.enderio.enderio.foundation.tag.EIOTags.Fluids.EXPERIENCE))
                .build();
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
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

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public void addLevelsToPlayer(Player player, int levelsToAdd) {
        long playerExperience = ExperienceUtil.getPlayerTotalXp(player);
        long targetExperience = ExperienceUtil.getTotalXpFromLevel(player.experienceLevel + levelsToAdd);
        addPlayerXp(player, targetExperience - playerExperience);
    }

    public void removeLevelsFromPlayer(Player player, int levelsToRemove) {
        long playerExperience = ExperienceUtil.getPlayerTotalXp(player);
        long targetExperience = ExperienceUtil
                .getTotalXpFromLevel(Math.max(0, player.experienceLevel - levelsToRemove));
        removePlayerXp(player, playerExperience - targetExperience);
    }

    public void addAllXpToPlayer(Player player) {
        long experienceToGive = TANK.getFluidAmount(this) / ExperienceUtil.EXP_TO_FLUID;
        addPlayerXp(player, experienceToGive);
    }

    public void removeAllXpFromPlayer(Player player) {
        long playerExperience = ExperienceUtil.getPlayerTotalXp(player);
        removePlayerXp(player, playerExperience);
    }

    private void addPlayerXp(Player player, long experience) {
        if (experience < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }

        // Clamp against the available whole points before draining any fluid.
        int xpToAdd = (int) Math.min(experience, TANK.getFluidAmount(this) / ExperienceUtil.EXP_TO_FLUID);
        TANK.drain(this, xpToAdd * ExperienceUtil.EXP_TO_FLUID, IFluidHandler.FluidAction.EXECUTE);

        // Add the XP to the player
        // Workaround some floating point problems when adding all the exp at once.
        // If we add it all at once, the experienceProgress gets messed up and then the
        // next extract is wonky.
        while (xpToAdd > 0) {
            int xp = Mth.clamp((int) Math.floor(
                    (1 - player.experienceProgress) * ExperienceUtil.getXpNeededForNextLevel(player.experienceLevel)),
                    0, xpToAdd);

            // If we can't add the rest of this level's progress, move on.
            if (xp <= 0) {
                xp = Mth.clamp(ExperienceUtil.getXpNeededForNextLevel(player.experienceLevel + 1), 0, xpToAdd);
            }

            if (xp <= 0) {
                LOGGER.error("xp <= 0 in addPlayerXp. experienceLevel: {}, experienceProgress: {}, xpToAdd: {}, xp: {}",
                        player.experienceLevel, player.experienceProgress, xpToAdd, xp);
                throw new IllegalStateException("xp <= 0 in addPlayerXp.");
            }

            player.giveExperiencePoints(xp);
            xpToAdd -= xp;
        }
    }

    private void removePlayerXp(Player player, long experience) {
        if (experience < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }

        var tank = getFluidTank();
        int space = Math.max(0, tank.getCapacity() - tank.getFluidAmount());
        int pointsThatFit = (int) Math.min(experience, space / ExperienceUtil.EXP_TO_FLUID);
        if (pointsThatFit == 0) return;
        int volume = pointsThatFit * ExperienceUtil.EXP_TO_FLUID;
        var currentFluid = tank.getFluid();
        var deposit = currentFluid.isEmpty() ? new FluidStack(EIOFluids.XP_JUICE.source().get(), volume)
                : EnderFluidUtil.copyWithAmount(currentFluid, volume);
        int filled = tank.fill(deposit, IFluidHandler.FluidAction.EXECUTE);

        // Remove the XP from the player
        // Workaround some floating point problems when adding all the exp at once.
        // If we add it all at once, the experienceProgress gets messed up and then the
        // next extract is wonky.
        int xpToRemove = filled / ExperienceUtil.EXP_TO_FLUID;
        while (xpToRemove > 0) {
            int xp = Mth.clamp(
                    (int) Math.floor(
                            player.experienceProgress * ExperienceUtil.getXpNeededForNextLevel(player.experienceLevel)),
                    0, xpToRemove);

            // If we can't remove the rest of this level's progress, move on.
            if (xp <= 0) {
                xp = Mth.clamp(ExperienceUtil.getXpNeededForNextLevel(player.experienceLevel - 1), 0, xpToRemove);
            }

            if (xp <= 0) {
                LOGGER.error(
                        "xp <= 0 in removePlayerXp. experienceLevel: {}, experienceProgress: {}, xpToRemove: {}, xp: {}",
                        player.experienceLevel, player.experienceProgress, xpToRemove, xp);
                throw new IllegalStateException("xp <= 0 in removePlayerXp.");
            }

            player.giveExperiencePoints(-xp);
            xpToRemove -= xp;
        }
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
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        saveTank(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);
    }

    // endregion
}

package com.enderio.modded_conduits.common.modules.refinedstorage;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.ConduitApi;
import com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Set;

/** RS owns network traversal; the conduit owns which physical faces may participate. */
public final class ConduitRSNode extends NetworkNode {
    public static final ResourceLocation ID = EnderIO.rl("rs_conduit");
    private Set<Direction> connectedSides = Set.of();
    private boolean attached;

    public ConduitRSNode(Level level, BlockPos pos) {
        super(level, pos.immutable());
    }

    public void attach(Set<Direction> sides) {
        connectedSides = Set.copyOf(sides);
        attached = true;
    }

    public void detach() {
        attached = false;
        connectedSides = Set.of();
    }

    @Override public boolean canConduct(Direction direction) { return attached && connectedSides.contains(direction); }
    @Override public boolean canReceive(Direction direction) { return canConduct(direction); }
    @Override public boolean isActive() { return attached; }
    // The source branch's GridNetworkNode has zero idle energy use.
    @Override public int getEnergyUsage() { return 0; }
    @Override public ResourceLocation getId() { return ID; }
    @Override public ItemStack getItemStack() {
        var conduit = level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(RefinedStorageCommonModule.ConduitKeys.RS);
        return ConduitApi.INSTANCE.getConduitItem(conduit, 1);
    }
}

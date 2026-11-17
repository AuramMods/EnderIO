package com.enderio.enderio.compat.ftb_ultimine;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.client.content.conduits.model.facades.FacadeUtil;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

public enum ConduitBlockSelectionHandler {
    INSTANCE;

    public Result customSelectionCheck(Player player, BlockPos origPos, BlockPos pos, BlockState origState, BlockState state) {
        var level = player.level();

        var origBlockEntity = level.getBlockEntity(origPos);
        if (!origState.is(EIOBlocks.CONDUIT_BUNDLE.get()) ||
            !(origBlockEntity instanceof ConduitBundleBlockEntity origConduitBundle)) {
            return Result.DEFAULT;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (!state.is(EIOBlocks.CONDUIT_BUNDLE.get()) ||
            !(blockEntity instanceof ConduitBundleBlockEntity conduitBundle)) {
            return Result.DEFAULT;
        }

        if (origConduitBundle.hasFacade() && FacadeUtil.areFacadesVisible(player)) {
            if (!conduitBundle.hasFacade()) {
                return Result.DENY;
            }

            // We will be aiming at a facade
            if (origConduitBundle.getFacadeBlock().equals(conduitBundle.getFacadeBlock())) {
                return Result.ALLOW;
            }

            return Result.DENY;
        }

        // Find the aimed conduit
        HitResult hitResult = player.pick(player.getBlockReach() + 5, 1, false);

        // We're breaking a conduit, make sure the network is shared
        Holder<Conduit<?, ?>> conduit = origConduitBundle.getShape().getConduit(origPos, hitResult);
        if (conduit == null) {
            return Result.DENY;
        }

        if (!conduitBundle.hasConduitStrict(conduit)) {
            return Result.DENY;
        }

        var origNode = origConduitBundle.getConduitNode(conduit);
        var node = conduitBundle.getConduitNode(conduit);
        if (origNode == null || node == null) return Result.DENY;
        var origNetwork = origNode.getNetwork();
        var network = node.getNetwork();
        if (origNetwork == network) {
            return Result.ALLOW;
        }

        return Result.DENY;
    }
}

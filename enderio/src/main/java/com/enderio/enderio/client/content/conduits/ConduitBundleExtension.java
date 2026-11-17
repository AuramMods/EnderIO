package com.enderio.enderio.client.content.conduits;

import com.enderio.enderio.api.conduits.bundle.ConduitBundle;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

public class ConduitBundleExtension implements IClientBlockExtensions {

    public static final ConduitBundleExtension INSTANCE = new ConduitBundleExtension();

    private ConduitBundleExtension() {
    }

    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        if (!(target instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        if (level.getBlockEntity(blockHitResult.getBlockPos()) instanceof ConduitBundleBlockEntity conduitBundle) {
            if (conduitBundle.hasFacade() && ClientFacadeVisibility.areFacadesVisible()) {
                return false;
            }

            var conduit = conduitBundle.getShape().getConduit(blockHitResult.getBlockPos(), target);
            if (conduit != null) {
                ConduitBreakParticle.addCrackEffects(blockHitResult.getBlockPos(), state, conduit.value(),
                        blockHitResult.getDirection());
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        // Use vanilla particles if we have a visible facade.
        // Conduit break particles are handled by the BE.
        if (level.getBlockEntity(pos) instanceof ConduitBundle conduitBundle) {
            return !(conduitBundle.hasFacade() && ClientFacadeVisibility.areFacadesVisible());
        }

        // Not a bundle
        return false;
    }
}

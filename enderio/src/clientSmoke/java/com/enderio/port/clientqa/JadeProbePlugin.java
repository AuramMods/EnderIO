package com.enderio.port.clientqa;

import com.enderio.enderio.compat.jade.EIOJadePlugin;
import net.minecraft.core.registries.BuiltInRegistries;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.Identifiers;
import snownee.jade.api.WailaPlugin;

/** Observe Jade after it draws the real overlay. The probe adds no tooltip content. */
@WailaPlugin
public final class JadeProbePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addAfterRenderCallback((tooltip, bounds, graphics, accessor) -> {
            if (accessor instanceof BlockAccessor block) {
                ClientJadeSmoke.observe(block.getPosition(), new ClientJadeSmoke.Overlay(
                    BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(),
                    BuiltInRegistries.ITEM.getKey(block.getFakeBlock().getItem()).toString(),
                    block.getBlockEntity() != null, block.isServerConnected(),
                    block.getServerData().getString("enderio_soulbound"),
                    block.getServerData().getString("id"), tooltip.getMessage(),
                    !tooltip.get(EIOJadePlugin.SOUL_BOUND_COMPONENT).isEmpty(),
                    !tooltip.get(Identifiers.UNIVERSAL_ENERGY_STORAGE).isEmpty()));
            }
        });
    }
}

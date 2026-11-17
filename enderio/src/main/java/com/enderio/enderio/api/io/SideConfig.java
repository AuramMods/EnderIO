package com.enderio.enderio.api.io;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Capability for getting the IO mode of the side of a block.
 */
public interface SideConfig {
    Capability<SideConfig> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    /**
     * Get the IO mode of this side.
     */
    IOMode getMode();

    /**
     * Set the IO mode of this side.
     */
    void setMode(IOMode mode);

    /**
     * Cycle the IO mode of this side.
     */
    void cycleMode();
}

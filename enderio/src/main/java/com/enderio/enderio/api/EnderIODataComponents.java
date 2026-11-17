package com.enderio.enderio.api;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.core.common.item.RegistryItemDataKey;
import org.jetbrains.annotations.ApiStatus;

/**
 * Typed native item data available for reuse by addons.
 */
@ApiStatus.AvailableSince("8.0.5")
public class EnderIODataComponents {

    /**
     * Intended for the enderio:conduit item - specifies which conduit to place.
     */
    public static final RegistryItemDataKey<Conduit<?, ?>> CONDUIT = new RegistryItemDataKey<>(
        EnderIOAPI.rl("conduit"), EnderIORegistries.Keys.CONDUIT);
}

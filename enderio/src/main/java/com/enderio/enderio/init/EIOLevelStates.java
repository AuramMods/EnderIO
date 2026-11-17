package com.enderio.enderio.init;

import com.enderio.core.common.capability.ServerLevelState;
import com.enderio.enderio.content.fire_crafting.FireCraftingManager;
import com.enderio.enderio.content.machines.obelisks.aversion.AversionObeliskManager;
import com.enderio.enderio.content.machines.obelisks.inhibitor.InhibitorObeliskManager;
import com.enderio.enderio.content.machines.obelisks.relocator.RelocatorObeliskManager;

/** Non-persistent managers, rebuilt as blocks and worlds load. */
public final class EIOLevelStates {
    public static final ServerLevelState<AversionObeliskManager> AVERSION_OBELISK_MANAGER =
        new ServerLevelState<>(AversionObeliskManager::new);
    public static final ServerLevelState<InhibitorObeliskManager> INHIBITOR_OBELISK_MANAGER =
        new ServerLevelState<>(InhibitorObeliskManager::new);
    public static final ServerLevelState<RelocatorObeliskManager> RELOCATOR_OBELISK_MANAGER =
        new ServerLevelState<>(RelocatorObeliskManager::new);
    public static final ServerLevelState<FireCraftingManager> FIRE_CRAFTING_MANAGER =
        new ServerLevelState<>(FireCraftingManager::new);

    private EIOLevelStates() {}
}

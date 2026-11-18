package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.common.network.menu.FloatSyncSlot;
import com.enderio.enderio.foundation.menu.MachineMenu;
import com.enderio.enderio.foundation.menu.MachineSlot;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public class PrimitiveAlloySmelterMenu extends MachineMenu<PrimitiveAlloySmelterBlockEntity> {
    private final FloatSyncSlot burnProgress;
    private final FloatSyncSlot craftingProgress;

    public PrimitiveAlloySmelterMenu(int id, Inventory inventory, PrimitiveAlloySmelterBlockEntity entity) {
        super(EIOMenus.PRIMITIVE_ALLOY_SMELTER.get(), id, inventory, entity);
        addSlots();
        burnProgress = addSyncSlot(FloatSyncSlot.readOnly(entity::getBurnProgress));
        craftingProgress = addSyncSlot(FloatSyncSlot.readOnly(entity::getCraftingProgress));
    }

    public PrimitiveAlloySmelterMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        super(EIOMenus.PRIMITIVE_ALLOY_SMELTER.get(), id, inventory, data, EIOBlockEntities.PRIMITIVE_ALLOY_SMELTER.get());
        addSlots();
        burnProgress = addSyncSlot(FloatSyncSlot.standalone());
        craftingProgress = addSyncSlot(FloatSyncSlot.standalone());
    }

    private void addSlots() {
        addSlot(new MachineSlot(getMachineInventory(), PrimitiveAlloySmelterBlockEntity.FUEL, 40, 53));
        addSlot(new MachineSlot(getMachineInventory(), PrimitiveAlloySmelterBlockEntity.INPUTS.get(0), 20, 17));
        addSlot(new MachineSlot(getMachineInventory(), PrimitiveAlloySmelterBlockEntity.INPUTS.get(1), 40, 17));
        addSlot(new MachineSlot(getMachineInventory(), PrimitiveAlloySmelterBlockEntity.INPUTS.get(2), 60, 17));
        addSlot(new MachineSlot(getMachineInventory(), PrimitiveAlloySmelterBlockEntity.OUTPUT, 116, 35));
        addPlayerInventorySlots(8, 84);
    }

    public float getBurnProgress() {
        return burnProgress.get();
    }

    public float getCraftingProgress() {
        return craftingProgress.get();
    }
}

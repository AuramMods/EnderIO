package com.enderio.enderio.foundation.io;

import com.enderio.enderio.foundation.MachineNBTKeys;
import net.minecraft.nbt.CompoundTag;

/** Reads earlier Ender IO machine properties without requiring NeoForge's attachment API. */
public final class LegacyMachineData {
    private LegacyMachineData() {}

    public static CompoundTag withNativeFields(CompoundTag source) {
        var result = source.copy();
        // Forge 6.x used a different capitalization for the world-save I/O field.
        copyIfMissing(result, source, MachineNBTKeys.IO_CONFIG, "IoConfig");
        var attachments = source.getCompound("neoforge:attachments");
        copyIfMissing(result, attachments, MachineNBTKeys.IO_CONFIG, "enderio:io_config");
        copyIfMissing(result, attachments, MachineNBTKeys.REDSTONE_CONTROL, "enderio:redstone_control");
        copyIfMissing(result, attachments, MachineNBTKeys.ACTION_RANGE, "enderio:action_range");
        if (!result.contains(MachineNBTKeys.IS_RANGE_VISIBLE) && result.contains(MachineNBTKeys.ACTION_RANGE)) {
            var range = result.getCompound(MachineNBTKeys.ACTION_RANGE);
            if (range.contains("isVisible")) result.putBoolean(MachineNBTKeys.IS_RANGE_VISIBLE, range.getBoolean("isVisible"));
        }
        return result;
    }

    private static void copyIfMissing(CompoundTag target, CompoundTag source, String key, String id) {
        if (!target.contains(key) && source.contains(id)) target.put(key, source.get(id).copy());
    }
}

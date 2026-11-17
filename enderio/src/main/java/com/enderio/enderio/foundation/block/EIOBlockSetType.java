package com.enderio.enderio.foundation.block;

import com.enderio.enderio.foundation.sound.EIOSoundTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class EIOBlockSetType {
    public static final BlockSetType SILENT = BlockSetType.register(new BlockSetType("enderio:block.silent", true, EIOSoundTypes.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY, SoundEvents.EMPTY));
}

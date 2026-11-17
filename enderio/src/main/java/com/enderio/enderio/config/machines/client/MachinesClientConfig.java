package com.enderio.enderio.config.machines.client;

import net.minecraftforge.common.ForgeConfigSpec;

public class MachinesClientConfig {

    public final MachinesClientBlocksConfig BLOCKS;

    public final ForgeConfigSpec.DoubleValue IO_CONFIG_NEIGHBOUR_TRANSPARENCY;
    public final ForgeConfigSpec.DoubleValue MACHINE_VOLUME;

    public MachinesClientConfig(ForgeConfigSpec.Builder builder) {
        builder.push("general");
        MACHINE_VOLUME = builder.comment("Volume of machine sounds [0-1]").defineInRange("machineVolume", 1.0, 0.0,1.0);
        builder.pop();

        BLOCKS = new MachinesClientBlocksConfig(builder);

        builder.push("ioconfig");
        IO_CONFIG_NEIGHBOUR_TRANSPARENCY = builder.comment("Neighbour Transparency [0-1]").defineInRange("neighbourTransparency", 0.4, 0.0, 1.0);
        builder.pop();
    }
}

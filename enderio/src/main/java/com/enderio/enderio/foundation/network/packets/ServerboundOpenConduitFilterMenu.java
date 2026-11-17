package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ServerboundOpenConduitFilterMenu(int containerId, int slot) {

    public static final ResourceLocation ID = EnderIOAPI.rl("client_open_conduit_filter_menu");

    public static final PacketCodec<ServerboundOpenConduitFilterMenu> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundOpenConduitFilterMenu::containerId, PacketCodecs.INT, ServerboundOpenConduitFilterMenu::slot,
            ServerboundOpenConduitFilterMenu::new);

}

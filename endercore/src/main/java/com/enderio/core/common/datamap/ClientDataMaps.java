package com.enderio.core.common.datamap;

import com.enderio.core.EnderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = EnderCore.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientDataMaps {
    private ClientDataMaps() {}

    public static <R, V> @Nullable V get(RegistryDataMap<R, V> type, R object) {
        return type.get(Minecraft.getInstance().level, object);
    }

    public static <R, V> java.util.Map<net.minecraft.resources.ResourceLocation, V> values(RegistryDataMap<R, V> type) {
        var level = Minecraft.getInstance().level;
        return level == null ? java.util.Map.of() : type.values(level.getRecipeManager());
    }

    static void disconnectMalformed() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.getConnection().disconnect(Component.literal("Invalid Ender IO data maps"));
    }

    @SubscribeEvent
    public static void tagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        // Forge sends the map snapshot before its tag packet. Cached consumers
        // must refresh once the corresponding client tag bindings are installed.
        if (event.getUpdateCause() == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var owner = level.getRecipeManager();
                DataMaps.replace(owner, new DataMaps.Snapshot(DataMaps.snapshot(owner).maps()));
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) DataMaps.clear(event.getPlayer().level().getRecipeManager());
    }
}

package com.enderio.enderio.compat.auram;

import com.enderio.enderio.EnderIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Mod.EventBusSubscriber(modid = EnderIO.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RfTransferBridge {
    private static volatile BiFunction<ServerLevel, long[], IntConsumer> registerRoute;

    private RfTransferBridge() {}

    @SubscribeEvent
    public static void enqueue(InterModEnqueueEvent event) {
        InterModComms.sendTo("auram", "rf_routes_v1",
                () -> (Consumer<BiFunction<ServerLevel, long[], IntConsumer>>) bridge -> registerRoute = bridge);
    }

    public static boolean enabled() {
        return registerRoute != null;
    }

    public static IntConsumer observe(ServerLevel level, long[] route) {
        return registerRoute.apply(level, route);
    }
}

package com.enderio.port.clientqa;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Entry point for the packaged test jar, on the client or the dedicated server. */
@Mod("enderio_client_qa")
public final class PortSmoke {
    public PortSmoke() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            MinecraftForge.EVENT_BUS.register(new NaturalSpawnObserver());
            MinecraftForge.EVENT_BUS.register(new AttractorObserver());
            MinecraftForge.EVENT_BUS.register(new UltimineFixtureEvents());
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSmoke::new);
        }
    }
}

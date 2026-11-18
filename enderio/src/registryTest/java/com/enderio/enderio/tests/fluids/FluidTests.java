package com.enderio.enderio.tests.fluids;

import com.enderio.enderio.init.EIOFluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.enderio.enderio.registrytests.ForgeServerExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ForgeServerExtension.class)
public class FluidTests {
    @Test
    public void ensureFluidBucketsHaveEmptyBucketRemainder(MinecraftServer server) {
        for (var fluid : EIOFluids.FLUIDS.fluidsRegister().getEntries()) {
            Item bucket = fluid.get().getBucket();
            if (bucket == Items.AIR) {
                continue;
            }

            var craftingRemainder = bucket.getDefaultInstance().getCraftingRemainingItem();
            Assertions.assertEquals(Items.BUCKET, craftingRemainder.getItem());
        }
    }
}

package com.enderio.enderio.init;

import com.enderio.enderio.content.paint.PaintingTrigger;
import com.enderio.enderio.content.tools.hang_glider.UseGliderTrigger;
import com.google.common.base.Suppliers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import java.util.function.Supplier;

public final class EIOCriterions {
    public static final Supplier<UseGliderTrigger> USE_GLIDER = Suppliers.memoize(UseGliderTrigger::new);
    public static final Supplier<PaintingTrigger> PAINTING_TRIGGER = Suppliers.memoize(PaintingTrigger::new);
    private EIOCriterions() {}
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            CriteriaTriggers.register(USE_GLIDER.get());
            CriteriaTriggers.register(PAINTING_TRIGGER.get());
        }));
    }
}

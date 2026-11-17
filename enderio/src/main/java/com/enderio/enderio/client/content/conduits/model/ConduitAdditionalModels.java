package com.enderio.enderio.client.content.conduits.model;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.client.content.conduits.model.modifier.ConduitModelModifiers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "enderio", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ConduitAdditionalModels {
    private static final List<ResourceLocation> MODEL_LOCATIONS = new ArrayList<>();
    private static final Map<ResourceLocation, BakedModel> MODELS = new HashMap<>();

    public static final ResourceLocation CONDUIT_CONNECTOR = loc("block/conduit_connector");
    public static final ResourceLocation CONDUIT_FACADE_OVERLAY = loc("block/conduit_facade_overlay");
    public static final ResourceLocation CONDUIT_CONNECTION = loc("block/conduit_connection");
    public static final ResourceLocation CONDUIT_CORE = loc("block/conduit_core");
    public static final ResourceLocation BOX = loc("block/box/1x1x1");
    public static final ResourceLocation CONDUIT_CONNECTION_BOX = loc("block/conduit_connection_box");
    public static final ResourceLocation CONDUIT_IO_IN = loc("block/io/input");
    public static final ResourceLocation CONDUIT_IO_IN_OUT = loc("block/io/in_out");
    public static final ResourceLocation CONDUIT_IO_OUT = loc("block/io/output");
    public static final ResourceLocation CONDUIT_IO_REDSTONE = loc("block/io/redstone");

    private static ResourceLocation loc(String modelName) {
        ResourceLocation loc = EnderIO.rl(modelName);
        MODEL_LOCATIONS.add(loc);
        return loc;
    }

    public static BakedModel modelOf(ResourceLocation location) {
        return MODELS.get(location);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation model : MODEL_LOCATIONS) {
            event.register(model);
        }

        // Ensure conduit model modifiers are ready, then load all model dependencies.
        ConduitModelModifiers.init();
        ConduitModelModifiers.getAllModelDependencies().forEach(event::register);
    }

    @SubscribeEvent
    public static void bakingModelsFinished(ModelEvent.BakingCompleted event) {
        for (ResourceLocation modelLocation : MODEL_LOCATIONS) {
            MODELS.put(modelLocation, event.getModels().get(modelLocation));
        }
    }
}

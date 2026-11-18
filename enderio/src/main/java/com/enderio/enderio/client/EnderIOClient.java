package com.enderio.enderio.client;

import com.enderio.core.client.item.FluidBarDecorator;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.conduits.model.RegisterConduitModelModifiersEvent;
import com.enderio.enderio.api.conduits.screen.RegisterConduitScreenTypesEvent;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.api.travel.RegisterTravelRenderersEvent;
import com.enderio.enderio.client.content.conduits.ConduitFacadeColor;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.client.content.conduits.gui.screen_type.ConduitScreenTypes;
import com.enderio.enderio.client.content.conduits.gui.screen_type.EnergyConduitScreenType;
import com.enderio.enderio.client.content.conduits.gui.screen_type.FluidConduitScreenType;
import com.enderio.enderio.client.content.conduits.gui.screen_type.ItemConduitScreenType;
import com.enderio.enderio.client.content.conduits.gui.screen_type.RedstoneConduitScreenType;
import com.enderio.enderio.client.content.conduits.model.ConduitItemModelLoader;
import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleGeometry;
import com.enderio.enderio.client.content.conduits.model.facades.FacadeItemGeometry;
import com.enderio.enderio.client.content.conduits.model.modifier.RedstoneConduitModelModifier;
import com.enderio.enderio.client.content.enderface.EnderfaceRenderer;
import com.enderio.enderio.client.content.filters.fluid.ClientEnderFluidFilterTooltip;
import com.enderio.enderio.client.content.filters.fluid.EnderFluidFilterScreen;
import com.enderio.enderio.client.content.filters.item.EnderItemFilterScreen;
import com.enderio.enderio.client.content.filters.item.LimitedItemFilterScreen;
import com.enderio.enderio.client.content.filters.soul.EnderSoulFilterScreen;
import com.enderio.enderio.client.content.filters.item.ClientEnderItemFilterTooltip;
import com.enderio.enderio.client.content.filters.item.ClientExistingItemFilterTooltip;
import com.enderio.enderio.client.content.filters.item.ClientLimitedItemFilterTooltip;
import com.enderio.enderio.client.content.filters.redstone.RedstoneCountFilterScreen;
import com.enderio.enderio.client.content.filters.redstone.RedstoneDoubleChannelFilterScreen;
import com.enderio.enderio.client.content.filters.redstone.RedstoneTimerFilterScreen;
import com.enderio.enderio.client.content.filters.soul.ClientEnderSoulFilterTooltip;
import com.enderio.enderio.client.content.glass.GlassIconDecorator;
import com.enderio.enderio.client.content.machines.AthenaCapacitorBankModel;
import com.enderio.enderio.client.content.machines.IOOverlayBakedModel;
import com.enderio.enderio.client.content.machines.gui.screen.AlloySmelterScreen;
import com.enderio.enderio.client.content.machines.gui.screen.PrimitiveAlloySmelterScreen;
import com.enderio.enderio.client.content.machines.gui.screen.AttractorObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.AversionObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.CapacitorBankScreen;
import com.enderio.enderio.client.content.machines.gui.screen.CrafterScreen;
import com.enderio.enderio.client.content.machines.gui.screen.DrainScreen;
import com.enderio.enderio.client.content.machines.gui.screen.EnchanterScreen;
import com.enderio.enderio.client.content.machines.gui.screen.FarmingStationScreen;
import com.enderio.enderio.client.content.machines.gui.screen.FluidTankScreen;
import com.enderio.enderio.client.content.machines.gui.screen.ImpulseHopperScreen;
import com.enderio.enderio.client.content.machines.gui.screen.InhibitorObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.NiardScreen;
import com.enderio.enderio.client.content.machines.gui.screen.PaintingMachineScreen;
import com.enderio.enderio.client.content.machines.gui.screen.PoweredSpawnerScreen;
import com.enderio.enderio.client.content.machines.gui.screen.RelocatorObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.client.content.machines.gui.screen.SlicerScreen;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.client.content.machines.gui.screen.SoulEngineScreen;
import com.enderio.enderio.client.content.machines.gui.screen.StirlingGeneratorScreen;
import com.enderio.enderio.client.content.machines.gui.screen.TravelAnchorScreen;
import com.enderio.enderio.client.content.machines.gui.screen.VacuumChestScreen;
import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.client.content.machines.gui.screen.WeatherObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.WiredChargerScreen;
import com.enderio.enderio.client.content.machines.gui.screen.WirelessChargerScreen;
import com.enderio.enderio.client.content.machines.gui.screen.XPObeliskScreen;
import com.enderio.enderio.client.content.machines.gui.screen.XPVacuumScreen;
import com.enderio.enderio.client.content.machines.renderer.blockentity.CapacitorBankBER;
import com.enderio.enderio.client.content.machines.renderer.blockentity.FluidTankBER;
import com.enderio.enderio.client.content.machines.renderer.blockentity.NiardBER;
import com.enderio.enderio.client.content.machines.renderer.blockentity.ObeliskBER;
import com.enderio.enderio.client.content.misc_blocks.EnderSkullRenderer;
import com.enderio.enderio.client.content.paint.PaintedBlockColor;
import com.enderio.enderio.client.content.paint.PaintedSandRenderer;
import com.enderio.enderio.client.content.paint.model.PaintedBlockGeometry;
import com.enderio.enderio.client.content.tools.ActiveGliderRenderLayer;
import com.enderio.enderio.client.content.tools.CoordinateMenuScreen;
import com.enderio.enderio.client.content.travel.TravelAnchorHud;
import com.enderio.enderio.client.content.travel.TravelAnchorRenderer;
import com.enderio.enderio.client.content.travel.TravelTargetRendering;
import com.enderio.enderio.client.foundation.particle.RangeParticle;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.filters.item.existing.ExistingItemFilter;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilter;
import com.enderio.enderio.content.filters.soul.EnderSoulFilter;
import com.enderio.enderio.content.fun.EnderiosItem;
import com.enderio.enderio.content.misc_blocks.skull.EnderSkullBlock;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.content.tools.vials.VoidVialItem;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOConduitTypes;
import com.enderio.enderio.init.EIOEntities;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIOMenus;
import com.enderio.enderio.init.EIOParticles;
import com.enderio.enderio.init.EIOTravelTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = "enderio", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class EnderIOClient {

    private static final Map<Item, ResourceLocation> HANG_GLIDER_MODEL_LOCATION = new HashMap<>();
    public static final Map<Item, BakedModel> GLIDER_MODELS = new HashMap<>();

    public static final ResourceLocation TRAVEL_ANCHOR_BACKDROP_MODEL_LOCATION =
        ResourceLocation.fromNamespaceAndPath(EnderIOAPI.MOD_ID, "block/travel_anchor_backdrop");

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        TravelTargetRendering.init();
        ConduitScreenTypes.init();

        event.enqueueWork(() -> {
            registerScreens();
            if (net.minecraftforge.fml.ModList.get().isLoaded("embeddium")) {
                com.enderio.enderio.compat.embeddium.EmbeddiumFacadeRendering.init();
            }
            //switch to item model component in 1.21.2
            ItemProperties.register(EIOItems.SOUL_VIAL.get(), SoulVialItem.FILLED_MODEL_PROPERTY,
                (stack, level, player, seed) -> SoulBoundUtils.isBound(stack) ? 1 : 0);

            ItemProperties.register(EIOItems.VOID_VIAL.get(), VoidVialItem.FILLED_MODEL_PROPERTY,
                (stack, level, player, seed) -> VoidVialItem.isFilled(stack) ? 1 : 0);

            ItemProperties.register(EIOItems.ENDERIOS.asItem(), EnderiosItem.INVERTED_PROPERTY,
                (ClampedItemPropertyFunction) (itemStack, clientLevel, livingEntity, seed) -> {
                    Component name = itemStack.hasCustomHoverName() ? itemStack.getHoverName() : null;
                    if (name != null && name.getContents() instanceof LiteralContents literal && literal.text().equalsIgnoreCase("soiredne")) {
                        return 1;
                    }
                    return 0;
                });

            // Register item property for conduit probe state switching
            ItemProperties.register(EIOItems.CONDUIT_PROBE.get(), ConduitProbeItem.PROBE_STATE_PREDICATE,
                (stack, level, player, seed) -> {
                    ConduitProbeItem.State state = ConduitProbeItem.getState(stack);
                    return state == ConduitProbeItem.State.COPY_PASTE ? 1.0f : 0.0f;
                });

            // Register fluid render types
            for (var fluid : EIOFluids.FLUIDS.fluidsRegister().getEntries()) {
                ItemBlockRenderTypes.setRenderLayer(fluid.get(), RenderType.translucent());
            }
        });
    }

    @SubscribeEvent
    public static void registerClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(EnderItemFilter.class, ClientEnderItemFilterTooltip::new);
        event.register(ExistingItemFilter.class, ClientExistingItemFilterTooltip::new);
        event.register(LimitedItemFilter.class, ClientLimitedItemFilterTooltip::new);
        event.register(EnderSoulFilter.class, ClientEnderSoulFilterTooltip::new);
        event.register(EnderFluidFilter.class, ClientEnderFluidFilterTooltip::new);
    }

    private static void registerScreens() {
        // Filter screens
        MenuScreens.register(EIOMenus.BASIC_ITEM_FILTER.get(), EnderItemFilterScreen::new);
        MenuScreens.register(EIOMenus.ADVANCED_ITEM_FILTER.get(), EnderItemFilterScreen::new);
        MenuScreens.register(EIOMenus.BIG_ITEM_FILTER.get(), EnderItemFilterScreen::new);
        MenuScreens.register(EIOMenus.BIG_ADVANCED_ITEM_FILTER.get(), EnderItemFilterScreen::new);
        MenuScreens.register(EIOMenus.LIMITED_ITEM_FILTER.get(), LimitedItemFilterScreen::new);
        MenuScreens.register(EIOMenus.BASIC_FLUID_FILTER.get(), EnderFluidFilterScreen::new);
        MenuScreens.register(EIOMenus.BASIC_SOUL_FILTER.get(), EnderSoulFilterScreen::new);
        MenuScreens.register(EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER.get(), RedstoneDoubleChannelFilterScreen::new);
        MenuScreens.register(EIOMenus.REDSTONE_TIMER_FILTER.get(), RedstoneTimerFilterScreen::new);
        MenuScreens.register(EIOMenus.REDSTONE_COUNT_FILTER.get(), RedstoneCountFilterScreen::new);

        // Machines
        MenuScreens.register(EIOMenus.ALLOY_SMELTER.get(), AlloySmelterScreen::new);
        MenuScreens.register(EIOMenus.PRIMITIVE_ALLOY_SMELTER.get(), PrimitiveAlloySmelterScreen::new);
        MenuScreens.register(EIOMenus.SAG_MILL.get(), SagMillScreen::new);
        MenuScreens.register(EIOMenus.STIRLING_GENERATOR.get(), StirlingGeneratorScreen::new);
        MenuScreens.register(EIOMenus.SLICE_N_SPLICE.get(), SlicerScreen::new);
        MenuScreens.register(EIOMenus.IMPULSE_HOPPER.get(), ImpulseHopperScreen::new);
        MenuScreens.register(EIOMenus.SOUL_BINDER.get(), SoulBinderScreen::new);
        MenuScreens.register(EIOMenus.POWERED_SPAWNER.get(), PoweredSpawnerScreen::new);
        MenuScreens.register(EIOMenus.VACUUM_CHEST.get(), VacuumChestScreen::new);
        MenuScreens.register(EIOMenus.XP_VACUUM.get(), XPVacuumScreen::new);
        MenuScreens.register(EIOMenus.CRAFTER.get(), CrafterScreen::new);
        MenuScreens.register(EIOMenus.DRAIN.get(), DrainScreen::new);
        MenuScreens.register(EIOMenus.NIARD.get(), NiardScreen::new);
        MenuScreens.register(EIOMenus.WIRED_CHARGER.get(), WiredChargerScreen::new);
        MenuScreens.register(EIOMenus.WIRELESS_CHARGER.get(), WirelessChargerScreen::new);
        MenuScreens.register(EIOMenus.PAINTING_MACHINE.get(), PaintingMachineScreen::new);
        MenuScreens.register(EIOMenus.SOUL_ENGINE.get(), SoulEngineScreen::new);
        MenuScreens.register(EIOMenus.TRAVEL_ANCHOR.get(), TravelAnchorScreen::new);
        MenuScreens.register(EIOMenus.XP_OBELISK.get(), XPObeliskScreen::new);
        MenuScreens.register(EIOMenus.FARMING_STATION.get(), FarmingStationScreen::new);
        MenuScreens.register(EIOMenus.INHIBITOR_OBELISK.get(), InhibitorObeliskScreen::new);
        MenuScreens.register(EIOMenus.AVERSION_OBELISK.get(), AversionObeliskScreen::new);
        MenuScreens.register(EIOMenus.RELOCATOR_OBELISK.get(), RelocatorObeliskScreen::new);
        MenuScreens.register(EIOMenus.ATTRACTOR_OBELISK.get(), AttractorObeliskScreen::new);
        MenuScreens.register(EIOMenus.WEATHER_OBELISK.get(), WeatherObeliskScreen::new);
        MenuScreens.register(EIOMenus.VAT.get(), VatScreen::new);

        // Storage
        MenuScreens.register(EIOMenus.CAPACITOR_BANK.get(), CapacitorBankScreen::new);
        MenuScreens.register(EIOMenus.FLUID_TANK.get(), FluidTankScreen::new);

        // Misc screens
        MenuScreens.register(EIOMenus.COORDINATE.get(), CoordinateMenuScreen::new);
        MenuScreens.register(EIOMenus.CONDUIT_MENU.get(), ConduitScreen::new);
        MenuScreens.register(EIOMenus.ENCHANTER.get(), EnchanterScreen::new);
    }

    @SubscribeEvent
    public static void modelRenderer(EntityRenderersEvent.RegisterRenderers event) {
        for (var capBank : EIOBlockEntities.CAPACITOR_BANKS.values()) {
            event.registerBlockEntityRenderer(capBank.get(), CapacitorBankBER::new);
        }

        event.registerBlockEntityRenderer(EIOBlockEntities.NIARD.get(), NiardBER::new);
        event.registerBlockEntityRenderer(EIOBlockEntities.XP_OBELISK.get(), c -> new ObeliskBER(EIOItems.EXPERIENCE_ROD::get));
        event.registerBlockEntityRenderer(EIOBlockEntities.INHIBITOR_OBELISK.get(), c -> new ObeliskBER(() -> Items.ENDER_PEARL));
        event.registerBlockEntityRenderer(EIOBlockEntities.AVERSION_OBELISK.get(), c -> new ObeliskBER(EIOBlocks.ENDERMAN_HEAD::asItem));
        event.registerBlockEntityRenderer(EIOBlockEntities.RELOCATOR_OBELISK.get(), c -> new ObeliskBER(() -> Items.PRISMARINE));
        event.registerBlockEntityRenderer(EIOBlockEntities.ATTRACTOR_OBELISK.get(), c -> new ObeliskBER(EIOItems.ELECTROMAGNET::get));
        event.registerBlockEntityRenderer(EIOBlockEntities.WEATHER_OBELISK.get(), c -> new ObeliskBER(() -> Items.FIREWORK_ROCKET));

        // TODO:  Custom model and BER for mind killer maybe?
        event.registerBlockEntityRenderer(EIOBlockEntities.MIND_KILLER.get(), c -> new ObeliskBER(() -> Items.ZOMBIE_HEAD));

        event.registerBlockEntityRenderer(EIOBlockEntities.FLUID_TANK.get(), FluidTankBER::new);
        event.registerBlockEntityRenderer(EIOBlockEntities.PRESSURIZED_FLUID_TANK.get(), FluidTankBER::new);

        event.registerBlockEntityRenderer(EIOBlockEntities.ENDER_SKULL.get(), EnderSkullRenderer::new);

        event.registerEntityRenderer(EIOEntities.PAINTED_SAND.get(), PaintedSandRenderer::new);
    }

    @SubscribeEvent
    public static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register(ConduitFacadeColor.INSTANCE, EIOBlocks.CONDUIT_BUNDLE.get());

        event.register(PaintedBlockColor.INSTANCE,
            EIOBlocks.PAINTED_FENCE.get(),
            EIOBlocks.PAINTED_FENCE_GATE.get(),
            EIOBlocks.PAINTED_SAND.get(),
            EIOBlocks.PAINTED_STAIRS.get(),
            EIOBlocks.PAINTED_CRAFTING_TABLE.get(),
            EIOBlocks.PAINTED_REDSTONE_BLOCK.get(),
            EIOBlocks.PAINTED_TRAPDOOR.get(),
            EIOBlocks.PAINTED_WOODEN_PRESSURE_PLATE.get(),
            EIOBlocks.PAINTED_SLAB.get(),
            EIOBlocks.PAINTED_GLOWSTONE.get(),
            EIOBlocks.PAINTED_WALL.get());

        for (var glassBlocks : EIOBlocks.GLASS_BLOCKS.values()) {
            for (var entry : glassBlocks.COLORS.entrySet()) {
                event.register((state, level, pos, tintIndex) -> entry.getKey().getMapColor().col, entry.getValue().get());
            }
        }
    }

    @SubscribeEvent
    public static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        event.register(ConduitFacadeColor.INSTANCE,
            EIOItems.CONDUIT_FACADE.get(),
            EIOItems.TRANSPARENT_CONDUIT_FACADE.get(),
            EIOItems.HARDENED_CONDUIT_FACADE.get(),
            EIOItems.TRANSPARENT_HARDENED_CONDUIT_FACADE.get());

        event.register(PaintedBlockColor.INSTANCE,
            EIOBlocks.PAINTED_FENCE.get(),
            EIOBlocks.PAINTED_FENCE_GATE.get(),
            EIOBlocks.PAINTED_SAND.get(),
            EIOBlocks.PAINTED_STAIRS.get(),
            EIOBlocks.PAINTED_CRAFTING_TABLE.get(),
            EIOBlocks.PAINTED_REDSTONE_BLOCK.get(),
            EIOBlocks.PAINTED_TRAPDOOR.get(),
            EIOBlocks.PAINTED_WOODEN_PRESSURE_PLATE.get(),
            EIOBlocks.PAINTED_SLAB.get(),
            EIOBlocks.PAINTED_GLOWSTONE.get(),
            EIOBlocks.PAINTED_WALL.get());

        for (var glassBlocks : EIOBlocks.GLASS_BLOCKS.values()) {
            for (var entry : glassBlocks.COLORS.entrySet()) {
                event.register((stack, tintIndex) -> entry.getKey().getMapColor().col, entry.getValue().asItem());
            }
        }
    }

    @SubscribeEvent
    public static void additionalModels(ModelEvent.RegisterAdditional event) {
        Set<ResourceLocation> gliderModels = Minecraft.getInstance()
                .getResourceManager()
                .listResources("models/enderio_glider", rl -> rl.getPath().endsWith(".json"))
                .keySet();

        for (ResourceLocation gliderModelPath : gliderModels) {
            Optional<Item> gliderItem = findGliderForModelRL(gliderModelPath);
            if (gliderItem.isPresent()) {
                ResourceLocation modelLookupLocation = ResourceLocation
                        .fromNamespaceAndPath(gliderModelPath.getNamespace(), gliderModelPath.getPath()
                                .substring("models/".length(), gliderModelPath.getPath().length() - 5));

                event.register(modelLookupLocation);
                HANG_GLIDER_MODEL_LOCATION.put(gliderItem.get(), modelLookupLocation);
            }
        }

        event.register(TRAVEL_ANCHOR_BACKDROP_MODEL_LOCATION);
    }

    @SubscribeEvent
    public static void itemDecorators(RegisterItemDecorationsEvent event) {
        // Register tools
        event.register(EIOItems.LEVITATION_STAFF.get(), FluidBarDecorator.INSTANCE);

        // Register all glass blocks
        EIOBlocks.GLASS_BLOCKS.values()
                .forEach(blocks -> blocks.getAllBlocks()
                        .forEach(block -> event.register(block.get(), GlassIconDecorator.INSTANCE)));
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new ActiveGliderRenderLayer(playerRenderer));
            }
        }
    }

    @SubscribeEvent
    public static void bakingCompleted(ModelEvent.BakingCompleted event) {
        GLIDER_MODELS.clear();
        HANG_GLIDER_MODEL_LOCATION.forEach((item, modelRL) -> {
            BakedModel bakedModel = event.getModels().get(modelRL);
            if (bakedModel != null) {
                GLIDER_MODELS.put(item, bakedModel);
            }
        });
        HANG_GLIDER_MODEL_LOCATION.clear();
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(EIOParticles.RANGE_PARTICLE.get(), RangeParticle.Provider::new);
        event.registerSpriteSet(EIOParticles.EXPERIENCE_EFFECT.get(), sprites -> {
            var provider = new net.minecraft.client.particle.SpellParticle.Provider(sprites);
            return (type, level, x, y, z, xSpeed, ySpeed, zSpeed) -> {
                var particle = provider.createParticle(type, level, x, y, z, xSpeed, ySpeed, zSpeed);
                if (particle != null) particle.setColor(0.27450980F, 0.88627451F, 0.29411765F);
                return particle;
            };
        });
        event.registerSpriteSet(EIOParticles.MACHINE_DUST.get(),
            com.enderio.enderio.client.foundation.particle.MachineDustParticle.Provider::new);
    }

    private static Optional<Item> findGliderForModelRL(ResourceLocation rl) {
        String namespace = rl.getNamespace();
        String path = rl.getPath().substring("models/enderio_glider/".length(), rl.getPath().length() - 5);
        return Optional.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    @SubscribeEvent
    public static void addAthenaBankOverlays(ModelEvent.ModifyBakingResult event) {
        if (ModList.get().isLoaded("athena")) {
            AthenaCapacitorBankModel.addOverlays(event.getModels());
        }
    }

    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("painted_block", new PaintedBlockGeometry.Loader());
        event.register("io_overlay", new IOOverlayBakedModel.Loader());
        event.register("conduit", new ConduitBundleGeometry.Loader());
        event.register("conduit_item", new ConduitItemModelLoader());
        event.register("facades_item", new FacadeItemGeometry.Loader());
    }

    @SubscribeEvent
    public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EnderSkullRenderer.ENDER_SKULL,
                EnderSkullRenderer.EnderSkullModel::createMobHeadLayer);
    }

    @SubscribeEvent
    public static void registerEnderSkulls(EntityRenderersEvent.CreateSkullModels event) {
        event.registerSkullModel(EnderSkullBlock.EIOSkulls.ENDERMAN, new EnderSkullRenderer.EnderSkullModel(
                event.getEntityModelSet().bakeLayer(EnderSkullRenderer.ENDER_SKULL)));
        SkullBlockRenderer.SKIN_BY_TYPE.put(EnderSkullBlock.EIOSkulls.ENDERMAN,
                ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png"));
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "anchor_hud", TravelAnchorHud.INSTANCE);
    }

    @SubscribeEvent
    public static void registerTravelRenderers(RegisterTravelRenderersEvent event) {
        event.register(EIOTravelTargets.TRAVEL_ANCHOR_TYPE.get(), TravelAnchorRenderer::new);
        event.register(EIOTravelTargets.ENDERFACE_TYPE.get(), EnderfaceRenderer::new);
    }

    // region Conduits

    @SubscribeEvent
    public static void registerConduitCoreModelModifiers(RegisterConduitModelModifiersEvent event) {
        event.register(EIOConduitTypes.REDSTONE.get(), RedstoneConduitModelModifier::new);
    }

    @SubscribeEvent
    public static void registerConduitScreenTypes(RegisterConduitScreenTypesEvent event) {
        event.register(EIOConduitTypes.ENERGY.get(), new EnergyConduitScreenType());
        event.register(EIOConduitTypes.FLUID.get(), new FluidConduitScreenType());
        event.register(EIOConduitTypes.REDSTONE.get(), new RedstoneConduitScreenType());
        event.register(EIOConduitTypes.ITEM.get(), new ItemConduitScreenType());
    }

    // endregion
}

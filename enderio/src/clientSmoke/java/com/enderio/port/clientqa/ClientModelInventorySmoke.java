package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.conduits.model.ConduitAdditionalModels;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.enderio.content.conduits.type.energy.EnergyConduitConnectionConfig;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Exercises the packaged model bake and the actual inventory renderer. */
final class ClientModelInventorySmoke {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Map<String, Object> result = new LinkedHashMap<>();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private CompletableFuture<Void> reload;
    private InventoryPreview preview;
    private int frames;
    private boolean done;

    ClientModelInventorySmoke() {
        result.put("checks", checks);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || minecraft.getOverlay() != null) return;
        try {
            if (preview != null && preview.frames >= 5) {
                var capture = ClientSmoke.capture("model-inventory.png");
                checks.put("hidden_muted_capture", Boolean.TRUE.equals(capture.get("passed")));
                result.put("capture", capture);
                finish();
            } else if (minecraft.screen instanceof TitleScreen && ++frames >= 30) {
                if (reload == null) {
                    result.put("startup", audit());
                    reload = minecraft.reloadResourcePacks();
                } else if (reload.isDone()) {
                    reload.join();
                    var reloaded = audit();
                    result.put("reloaded", reloaded);
                    checks.put("same_inventory_after_resource_reload", reloaded.equals(result.get("startup")));
                    preview = new InventoryPreview(samples());
                    minecraft.setScreen(preview);
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            result.put("error", failure.toString());
            finish();
        }
    }

    private void finish() {
        done = true;
        result.put("passed", !result.containsKey("error") && checks.values().stream().allMatch(Boolean::booleanValue));
        ClientSmoke.complete("client_model_inventory", result);
    }

    private Map<String, Object> audit() {
        checkConduitArrowDepth();
        var manager = minecraft.getModelManager();
        var missing = manager.getMissingModel();
        var missingSprite = missing.getParticleIcon().contents().name();
        var blocks = new LinkedHashMap<String, Integer>();
        var invisibleStates = new ArrayList<String>();
        var missingBlocks = new ArrayList<String>();
        var blockParticles = new ArrayList<String>();
        for (var id : BuiltInRegistries.BLOCK.keySet().stream().filter(key -> key.getNamespace().equals("enderio")).sorted().toList()) {
            var states = BuiltInRegistries.BLOCK.get(id).getStateDefinition().getPossibleStates();
            blocks.put(id.toString(), states.size());
            for (var state : states) {
                if (state.getRenderShape() == RenderShape.INVISIBLE) {
                    invisibleStates.add(state.toString());
                    continue;
                }
                var model = manager.getBlockModelShaper().getBlockModel(state);
                if (model == missing) missingBlocks.add(state.toString());
                else if (model.getParticleIcon().contents().name().equals(missingSprite)) blockParticles.add(state.toString());
            }
        }

        var items = new LinkedHashMap<String, String>();
        var missingItems = new ArrayList<String>();
        var itemParticles = new ArrayList<String>();
        for (var id : BuiltInRegistries.ITEM.keySet().stream().filter(key -> key.getNamespace().equals("enderio")).sorted().toList()) {
            var model = itemModel(new ItemStack(BuiltInRegistries.ITEM.get(id)));
            var sprite = model.getParticleIcon().contents().name();
            items.put(id.toString(), sprite.toString());
            if (model == missing) missingItems.add(id.toString());
            else if (sprite.equals(missingSprite)) itemParticles.add(id.toString());
        }

        var variants = new LinkedHashMap<String, String>();
        var tooltips = new LinkedHashMap<String, List<String>>();
        for (var sample : samples()) {
            var model = itemModel(sample.stack());
            var sprite = model.getParticleIcon().contents().name().toString();
            var quads = model.getQuads(null, null, RandomSource.create(42));
            boolean matches = model != missing && sprite.equals("enderio:" + sample.sprite()) && !quads.isEmpty()
                && quads.stream().noneMatch(quad -> quad.getSprite().contents().name().equals(missingSprite));
            checks.merge("textured_" + sample.name(), matches, (before, after) -> before && after);
            variants.put(sample.name(), sprite);
            var lines = sample.stack().getTooltipLines(null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
            tooltips.put(sample.name(), lines);
        }
        var language = minecraft.getLanguageManager().getSelected();
        if (language.equals("fr_fr")) {
            checkTooltip(tooltips, "filled_soul_vial", "Vache");
            checkTooltip(tooltips, "filled_void_vial", "100/3200 mb de Jus d'EXP", "Stocké : 0 niveaux + 5 d'expérience");
            checkTooltip(tooltips, "probe", "Mode Sonde");
            checkTooltip(tooltips, "copy_probe", "Mode Copier/Coller", "Contient les données du conduit copié :", "- Conduit d'énergie");
        } else {
            checkTooltip(tooltips, "filled_soul_vial", "Cow");
            checkTooltip(tooltips, "filled_void_vial", "100/3200 mb of XP Juice", "Stored: 0 levels + 5 experience");
            checkTooltip(tooltips, "probe", "Mode Probe");
            checkTooltip(tooltips, "copy_probe", "Mode Copy/Paste", "Contains copied conduit data:", "- Energy Conduit");
        }
        checkTooltip(tooltips, "copy_probe", "- portqa:unknown_conduit");
        checks.merge("registered_block_models", !blocks.isEmpty() && missingBlocks.isEmpty(), (before, after) -> before && after);
        checks.merge("registered_item_models", !items.isEmpty() && missingItems.isEmpty(), (before, after) -> before && after);
        return Map.of("blocks", blocks, "invisibleStates", invisibleStates, "missingBlocks", missingBlocks,
            "missingBlockParticles", blockParticles, "items", items, "missingItems", missingItems,
            "missingItemParticles", itemParticles, "variants", variants, "tooltips", tooltips, "language", language);
    }

    private void checkConduitArrowDepth() {
        float layerSpacing = 1 / 256f;
        var variants = List.of(ConduitAdditionalModels.CONDUIT_IO_IN, ConduitAdditionalModels.CONDUIT_IO_OUT,
            ConduitAdditionalModels.CONDUIT_IO_IN_OUT);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var face = faceDepths(ConduitAdditionalModels.CONDUIT_CONNECTION_BOX, side);
            for (var variant : variants) {
                var arrows = faceDepths(variant, side).stream().sorted().toList();
                int layers = variant.equals(ConduitAdditionalModels.CONDUIT_IO_IN_OUT) ? 2 : 1;
                boolean separated = face.size() == 1 && arrows.size() == layers;
                for (int layer = 0; separated && layer < layers; layer++) {
                    float expectedDepth = face.get(0) + (layer + 1) * layerSpacing;
                    separated = Math.abs(arrows.get(layer) - expectedDepth) < 0.00001f;
                }
                checks.merge("depth_" + variant.getPath() + "_" + side.getName(), separated, (before, after) -> before && after);
            }
        }
    }

    private static List<Float> faceDepths(ResourceLocation model, Direction side) {
        int axis = side.getAxis() == Direction.Axis.X ? 0 : 2;
        return ConduitAdditionalModels.modelOf(model).getQuads(null, null, RandomSource.create(42)).stream()
            .filter(quad -> quad.getDirection() == side)
            .map(quad -> Float.intBitsToFloat(quad.getVertices()[IQuadTransformer.POSITION + axis]) * side.getAxisDirection().getStep())
            .toList();
    }

    private void checkTooltip(Map<String, List<String>> tooltips, String sample, String... lines) {
        checks.merge("tooltip_" + sample, tooltips.get(sample).containsAll(List.of(lines)), (before, after) -> before && after);
    }

    private BakedModel itemModel(ItemStack stack) {
        return minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 42);
    }

    private static List<Sample> samples() {
        var soul = SoulVialItem.forSoul(Soul.of(EntityType.COW));
        var voidVial = new ItemStack(EIOItems.VOID_VIAL.get());
        EIODataComponents.ITEM_FLUID_CONTENT.set(voidVial, new FluidStack(EIOFluids.XP_JUICE.source().get(), 100));
        var cereal = new ItemStack(EIOItems.ENDERIOS.get());
        cereal.setHoverName(Component.literal("SOIREDNE"));
        var probe = new ItemStack(EIOItems.CONDUIT_PROBE.get());
        EIODataComponents.PROBE_STATE.set(probe, ConduitProbeItem.State.COPY_PASTE);
        EIODataComponents.PROBE_CONFIG.set(probe, new ConduitProbeItem.ProbeConfigData(
            Map.of(ResourceLocation.parse("enderio:energy"), EnergyConduitConnectionConfig.DEFAULT,
                ResourceLocation.parse("portqa:unknown_conduit"), EnergyConduitConnectionConfig.DEFAULT)));
        var samples = new ArrayList<>(List.of(
            new Sample("empty_soul_vial", "item/soul_vial", new ItemStack(EIOItems.SOUL_VIAL.get())),
            new Sample("filled_soul_vial", "item/soul_vial_filled", soul),
            new Sample("empty_void_vial", "item/void_vial", new ItemStack(EIOItems.VOID_VIAL.get())),
            new Sample("filled_void_vial", "item/void_vial_filled", voidVial),
            new Sample("enderios", "item/enderios", new ItemStack(EIOItems.ENDERIOS.get())),
            new Sample("soiredne", "item/soiredne", cereal),
            new Sample("probe", "item/conduit_probe_probe", new ItemStack(EIOItems.CONDUIT_PROBE.get())),
            new Sample("copy_probe", "item/conduit_probe_copy", probe)));
        for (var tier : SolarPanelTier.values()) {
            var block = EIOBlocks.SOLAR_PANELS.get(tier).get();
            var name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            samples.add(new Sample(name, "block/" + name.substring(0, name.indexOf('_')) + "_top", new ItemStack(block)));
        }
        return samples;
    }

    private record Sample(String name, String sprite, ItemStack stack) {}

    private static final class InventoryPreview extends Screen {
        private final List<Sample> samples;
        private int frames;

        private InventoryPreview(List<Sample> samples) {
            super(Component.literal("Ender IO inventory model fixtures"));
            this.samples = samples;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff20232b);
            graphics.drawCenteredString(font, title, width / 2, 12, 0xffffff);
            for (int i = 0; i < samples.size(); i++) {
                var sample = samples.get(i);
                int x = 16 + (i % 2) * (width / 2);
                int y = 30 + (i / 2) * 38;
                graphics.renderFakeItem(sample.stack(), x, y);
                graphics.drawString(font, sample.stack().getHoverName(), x + 21, y, 0xffffff);
                var tooltip = sample.stack().getTooltipLines(null, TooltipFlag.NORMAL);
                for (int line = 1; line < Math.min(tooltip.size(), 4); line++) {
                    graphics.drawString(font, font.plainSubstrByWidth(tooltip.get(line).getString(), width / 2 - 40),
                        x + 21, y + 2 + 9 * line, 0xb5bdca);
                }
            }
            frames++;
        }
    }
}

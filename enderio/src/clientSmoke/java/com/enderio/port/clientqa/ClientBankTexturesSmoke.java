package com.enderio.port.clientqa;

import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Checks both selected sprites and the drawn capacitor-bank corner after a live tier change. */
final class ClientBankTexturesSmoke {
    private static final String PACK = "mod/enderio:resourcepacks/athena";
    private static final List<String> PHASES = List.of("matching", "mixed", "restored", "reloaded");
    private static final BlockPos CORNER = new BlockPos(5248, 80, 8);

    record Face(BlockPos pos, String tier, String overlay, List<String> matching, List<String> mixed) {
        ResourceLocation id() {
            return ResourceLocation.parse("enderio:" + tier + "_capacitor_bank");
        }
    }

    private static final List<Face> FACES = List.of(
        new Face(new BlockPos(5247, 79, 8), "advanced", "pull",
            List.of("empty", "horizontal", "particle", "vertical"), List.of("center", "horizontal", "particle", "vertical")),
        new Face(new BlockPos(5248, 79, 8), "advanced", "push_pull",
            List.of("empty", "horizontal", "particle", "vertical"), List.of("horizontal", "horizontal", "particle", "particle")),
        new Face(new BlockPos(5246, 79, 8), "vibrant", "push",
            List.of("particle", "particle", "vertical", "vertical"), List.of("particle", "particle", "vertical", "vertical")),
        new Face(new BlockPos(5245, 79, 8), "basic", "disabled",
            List.of("particle", "particle", "particle", "particle"), List.of("particle", "particle", "particle", "particle")));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final boolean athena;
    private final boolean embeddium;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private CompletableFuture<Void> reload;
    private boolean connecting;
    private boolean joined;
    private boolean done;
    private int phaseIndex;
    private int readyTick = -1;
    private int[] matchedCorner;

    ClientBankTexturesSmoke(boolean athena, boolean embeddium) {
        this.athena = athena;
        this.embeddium = embeddium;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            String phase = PHASES.get(phaseIndex);
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 155_000_000_000L) {
                throw new AssertionError("Bank textures did not reach " + phase);
            }
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                String address = System.getProperty("enderio.clientqa.server");
                connecting = true;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO bank textures", address, true), false);
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            if (!joined) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                checks.put("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                checks.put("expected_renderer", ModList.get().isLoaded("athena") == athena
                    && ModList.get().isLoaded("embeddium") == embeddium && !ModList.get().isLoaded("oculus"));
                var origin = SectionPos.of(FACES.get(0).pos());
                var diagonal = SectionPos.of(CORNER);
                checks.put("diagonal_crosses_chunk_and_vertical_render_section",
                    origin.x() + 1 == diagonal.x() && origin.y() + 1 == diagonal.y() && origin.z() == diagonal.z());
                minecraft.options.hideGui = true;
                joined = true;
                signal("connected");
            }
            if (!ready(phase)) return;
            if (phase.equals("reloaded")) {
                if (reload == null) {
                    reload = minecraft.reloadResourcePacks();
                    readyTick = -1;
                    return;
                }
                if (!reload.isDone()) return;
                reload.join();
            }
            observe(phase);
            signal(phase);
            readyTick = -1;
            if (++phaseIndex == PHASES.size()) finish(null);
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean ready(String phase) {
        Block corner = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
            phase.equals("mixed") ? "enderio:vibrant_capacitor_bank" : "enderio:advanced_capacitor_bank"));
        if (!Files.exists(output.resolve("server-" + phase)) || minecraft.getOverlay() != null
            || minecraft.player.distanceToSqr(5247.5, 78, 14.5) > 0.01
            || !minecraft.level.getBlockState(CORNER).is(corner)
            || FACES.stream().anyMatch(face -> !(minecraft.level.getBlockEntity(face.pos()) instanceof CapacitorBankBlockEntity))) {
            return false;
        }
        if (readyTick == -1) readyTick = minecraft.player.tickCount;
        return minecraft.player.tickCount - readyTick >= 30;
    }

    private void observe(String phase) throws IOException {
        var frame = ClientSmoke.capture(phase + ".png", 50);
        captures.put(phase, frame);
        checks.put("hidden_muted_" + phase, Boolean.TRUE.equals(frame.get("passed")));
        for (var face : FACES) {
            inspectFace(phase, face, frame);
        }
        var image = ImageIO.read(output.resolve(phase + ".png").toFile());
        int centerX = image.getWidth() / 2;
        int centerY = image.getHeight() / 2;
        int width = image.getWidth() / 36;
        int height = image.getHeight() / 23;
        int[] crop = image.getRGB(centerX + 5, centerY - height + 5, width, height, null, 0, width);
        if (matchedCorner == null) {
            matchedCorner = crop;
            checks.put("advanced_corner_has_rendered_detail", Arrays.stream(crop).distinct().count() > 3);
        } else {
            int changed = 0;
            for (int i = 0; i < crop.length; i++) {
                if (crop[i] != matchedCorner[i]) changed++;
            }
            frame.put("changedAdvancedCornerPixels", changed);
            checks.put("drawn_advanced_corner_" + phase, athena && phase.equals("mixed") ? changed > 10 : changed == 0);
        }
    }

    private void inspectFace(String phase, Face face, Map<String, Object> frame) {
        var bank = (CapacitorBankBlockEntity) minecraft.level.getBlockEntity(face.pos());
        var state = bank.getBlockState();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(minecraft.level, face.pos(), state, bank.getModelData());
        var solid = model.getQuads(state, Direction.SOUTH, RandomSource.create(42), data, RenderType.solid()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
        var expected = (phase.equals("mixed") ? face.mixed() : face.matching()).stream()
            .map(part -> part.equals("particle") ? "enderio:block/" + face.id().getPath()
                : "enderio:block/ctm/" + face.id().getPath() + "_" + part).sorted().toList();
        if (!athena) expected = List.of("enderio:block/" + face.id().getPath());
        var overlays = model.getQuads(state, null, RandomSource.create(42), data, RenderType.cutout()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
        var path = ResourceLocation.parse("enderio:blockstates/" + face.id().getPath() + ".json");
        var packId = minecraft.getResourceManager().getResource(path).orElseThrow().sourcePackId();
        String label = phase + "_" + face.pos().getX();
        frame.put("bank_" + face.pos().getX(), Map.of("solid", solid, "overlays", overlays, "pack", packId));
        checks.put("selected_bank_pack_" + label, PACK.equals(packId) == athena);
        checks.put("expected_south_face_" + label, solid.equals(expected));
        checks.put("expected_cutout_overlay_" + label, overlays.equals(List.of("enderio:block/overlay/" + face.overlay()))
            && model.getRenderTypes(state, RandomSource.create(42), data).contains(RenderType.cutout()));
    }

    private void signal(String name) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".json"), "{}");
    }

    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_bank_textures", result);
    }
}

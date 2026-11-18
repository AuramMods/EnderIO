package com.enderio.port.clientqa;

import com.google.gson.GsonBuilder;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Automated observations of the real renderer; never included in distributable jars. */
@Mod("enderio_client_qa")
public final class ClientSmoke {
    private final long started = System.nanoTime();
    private int readyFrames;
    private long readySince;
    private boolean finished;

    public ClientSmoke() {
        switch (System.getProperty("enderio.clientqa.scenario", "title")) {
            case "singleplayer" -> new ClientWorldSmoke();
            case "multiplayer" -> new ClientMultiplayerSmoke();
            case "travel" -> new ClientTravelSmoke();
            case "carried-tanks" -> new ClientCarriedTankSmoke();
            case "extraction-upgrades" -> new ClientExtractionUpgradeSmoke();
            case "legacy-lights" -> new ClientLegacyLightSmoke();
            case "primitive-smelter" -> new ClientPrimitiveSmelterSmoke();
            default -> MinecraftForge.EVENT_BUS.addListener(this::render);
        }
        System.out.println("QA_EVT {\"event\":\"client_qa_loaded\",\"status\":\"info\"}");
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            if (readySince == 0) readySince = System.nanoTime();
            if (++readyFrames < 30 || System.nanoTime() - readySince < 4_000_000_000L) return;
        } else if ((System.nanoTime() - started) / 1_000_000_000L < 120) {
            return;
        }
        finished = true;
        try {
            var result = capture("title-screen.png");
            boolean passed = Boolean.TRUE.equals(result.get("passed"))
                && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null;
            result.put("passed", passed);
            complete("client_title_render", result);
        } catch (Throwable failure) {
            failure.printStackTrace();
            complete("client_title_render", new LinkedHashMap<>(Map.of("passed", false, "error", failure.toString())));
        }
    }

    static Map<String, Object> capture(String imageName) throws java.io.IOException {
        var minecraft = Minecraft.getInstance();
        var result = new LinkedHashMap<String, Object>();
        var output = Path.of(System.getProperty("enderio.clientqa.output"));
        Files.createDirectories(output);
        long window = minecraft.getWindow().getWindow();
        result.put("screen", minecraft.screen == null ? "null" : minecraft.screen.getClass().getName());
        result.put("visible", GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != 0);
        result.put("focused", GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0);
        result.put("fullscreen", GLFW.glfwGetWindowMonitor(window) != 0);
        result.put("cursorCaptured", GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL);
        result.put("narrator", minecraft.options.narrator().get().name());
        var volumes = new LinkedHashMap<String, Float>();
        for (var sound : SoundSource.values()) volumes.put(sound.getName(), minecraft.options.getSoundSourceVolume(sound));
        result.put("volumes", volumes);
        result.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
        var loadedMods = new LinkedHashMap<String, Object>();
        for (var mod : ModList.get().getMods()) {
            loadedMods.put(mod.getModId(), Map.of("version", mod.getVersion().toString(),
                "file", mod.getOwningFile().getFile().getFilePath().toString()));
        }
        result.put("loadedMods", loadedMods);
        var requiredMods = System.getProperty("enderio.clientqa.requiredMods", "endercore,enderio").split(",");
        var models = new LinkedHashMap<String, Boolean>();
        var modelManager = minecraft.getModelManager();
        // Only top-level baked block-state/item models are stored in ModelManager;
        // querying a raw parent model ID would incorrectly report it as missing.
        for (var block : new net.minecraft.world.level.block.Block[] {EIOBlocks.ENCHANTER.get(), EIOBlocks.POWERED_SPAWNER.get()}) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = modelManager.getBlockModelShaper().getBlockModel(state);
                models.put(BuiltInRegistries.BLOCK.getKey(block) + " " + state.getValues(), model != modelManager.getMissingModel());
            }
        }
        result.put("modelsLoaded", models);
        try (var screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            screenshot.writeToFile(output.resolve(imageName));
            result.put("width", screenshot.getWidth());
            result.put("height", screenshot.getHeight());
            result.put("distinctPixels", java.util.Arrays.stream(screenshot.getPixelsRGBA()).distinct().count());
        }
        boolean passed = minecraft.options.narrator().get() == NarratorStatus.OFF
            && volumes.values().stream().allMatch(volume -> volume == 0.0F)
            && models.values().stream().allMatch(Boolean::booleanValue)
            && java.util.Arrays.stream(requiredMods).allMatch(loadedMods::containsKey)
            && Boolean.FALSE.equals(result.get("visible")) && Boolean.FALSE.equals(result.get("focused"))
            && Boolean.FALSE.equals(result.get("fullscreen")) && Boolean.FALSE.equals(result.get("cursorCaptured"))
            && ((Long) result.get("distinctPixels")) > 100;
        result.put("passed", passed);
        return result;
    }

    static void complete(String event, Map<String, Object> result) {
        try {
            var output = Path.of(System.getProperty("enderio.clientqa.output"));
            Files.createDirectories(output);
            Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            System.out.println("QA_EVT " + new GsonBuilder().create().toJson(Map.of(
                "event", event, "status", Boolean.TRUE.equals(result.get("passed")) ? "pass" : "fail",
                "result", output.resolve("result.json").toString(), "checks", result.getOrDefault("checks", Map.of()))));
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            Minecraft.getInstance().stop();
        }
    }
}

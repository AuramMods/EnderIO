package com.enderio.port.clientqa;

import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOSounds;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class ClientSteelConstructionSmoke {
    private enum Stage { TITLE, JOIN, POSITION, MEASURE, VERIFY, PARTICLES, DONE }
    private enum AmbientPhase { ENABLED, DISABLED, RESTORED }
    private record Trial(String name, double x, int y, boolean climb, boolean crouch, boolean steel) {
        Block ladder() {
            return steel ? EIOBlocks.DARK_STEEL_LADDER.get() : Blocks.LADDER;
        }
    }

    private static final List<Trial> TRIALS = List.of(
        new Trial("vanilla-climb", 4201.5, 84, true, false, false),
        new Trial("steel-climb", 4205.5, 84, true, false, true),
        new Trial("vanilla-descent", 4201.5, 96, false, false, false),
        new Trial("steel-descent", 4205.5, 96, false, false, true),
        new Trial("steel-crouch", 4205.5, 91, false, true, true));
    private static final int TRIAL_TICKS = 12;
    private static final BlockPos OBSIDIAN = new BlockPos(4224, 91, 11);
    private static final AABB OBSIDIAN_DUST_AREA = new AABB(4218.8, 85.8, 10.8, 4230.2, 97.2, 12.3);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Double> travel = new LinkedHashMap<>();
    private final Map<String, Integer> ambientPeaks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int trialIndex;
    private double startY;
    private int firstTick;
    private int firstViewTick = -1;
    private AmbientPhase ambientPhase;
    private int ambientStart;
    private int ambientPeak;

    ClientSteelConstructionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen screen) {
                throw new AssertionError("Steel construction disconnected in " + stage + ": " + screen.getNarrationMessage().getString());
            }
            if (System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Steel construction timed out in " + stage + ": " + trialIndex);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("packaged_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    minecraft.options.keyShift.setDown(true);
                    minecraft.options.hideGui = true;
                    signal("connected", Map.of());
                    stage = Stage.POSITION;
                }
                case POSITION -> {
                    var trial = TRIALS.get(trialIndex);
                    if (!ready(trial.name()) || Math.abs(player.getX() - trial.x()) > 0.1
                        || Math.abs(player.getY() - trial.y()) > 0.25 || !player.onClimbable()
                        || !minecraft.level.getBlockState(player.blockPosition()).is(trial.ladder())) return;
                    startY = player.getY();
                    firstTick = player.tickCount;
                    minecraft.options.keyUp.setDown(trial.climb());
                    minecraft.options.keyShift.setDown(trial.crouch());
                    stage = Stage.MEASURE;
                }
                case MEASURE -> {
                    var trial = TRIALS.get(trialIndex);
                    if (player.tickCount - firstTick < TRIAL_TICKS) return;
                    double endY = player.getY();
                    travel.put(trial.name(), endY - startY);
                    minecraft.options.keyUp.setDown(false);
                    minecraft.options.keyShift.setDown(true);
                    check("stays_on_" + trial.name(), player.onClimbable()
                        && minecraft.level.getBlockState(player.blockPosition()).is(trial.ladder()));
                    signal(trial.name(), Map.of("startY", startY, "endY", endY, "ticks", player.tickCount - firstTick));
                    trialIndex++;
                    stage = trialIndex < TRIALS.size() ? Stage.POSITION : Stage.VERIFY;
                }
                case VERIFY -> {
                    if (!ready("verify") || player.distanceToSqr(4203.5, 92, 13.5) > 0.1) return;
                    if (firstViewTick < 0) firstViewTick = player.tickCount;
                    if (player.tickCount - firstViewTick < 8) return;
                    check("vanilla_ladder_climbs", travel.get("vanilla-climb") > 0.5);
                    check("dark_steel_ladder_climbs_faster", travel.get("steel-climb") > travel.get("vanilla-climb") + 1.25);
                    check("vanilla_ladder_descends", travel.get("vanilla-descent") < -0.5);
                    check("dark_steel_ladder_descends_faster", travel.get("steel-descent") < travel.get("vanilla-descent") - 1.25);
                    check("crouch_holds_dark_steel_position", Math.abs(travel.get("steel-crouch")) < 0.15);
                    verifyModels();
                    var capture = ClientSmoke.capture("dark-steel-ladder.png");
                    captures.put("dark-steel-ladder", capture);
                    check("silent_hidden_capture", Boolean.TRUE.equals(capture.get("passed")));
                    String tip = EIOCommonLang.DARK_STEEL_LADDER_FASTER.getString();
                    boolean ladderTip = new ItemStack(EIOBlocks.DARK_STEEL_LADDER.asItem()).getTooltipLines(player, TooltipFlag.NORMAL)
                        .stream().anyMatch(line -> line.getString().equals(tip));
                    boolean alloyTip = new ItemStack(EIOBlocks.DARK_STEEL_BLOCK.asItem()).getTooltipLines(player, TooltipFlag.NORMAL)
                        .stream().anyMatch(line -> line.getString().equals(tip));
                    checks.put("ladder_shows_the_climbing_hint", ladderTip);
                    checks.put("solid_dark_steel_has_no_ladder_hint", !alloyTip);
                    if (!ladderTip || alloyTip) {
                        throw new AssertionError("Climbing tooltip targets ladder=" + ladderTip + ", solid block=" + alloyTip);
                    }
                    verifySounds();
                    signal("ladder-verified", Map.of());
                    stage = Stage.PARTICLES;
                }
                case PARTICLES -> observeObsidian();
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void observeObsidian() throws IOException {
        var player = minecraft.player;
        var particlesSetting = BaseConfig.CLIENT.MACHINE_PARTICLES;
        if (ambientPhase == null) {
            if (!ready("obsidian") || player.distanceToSqr(4224.5, 90, 15.5) > 0.1
                || !minecraft.level.getBlockState(OBSIDIAN).is(EIOBlocks.REINFORCED_OBSIDIAN.get())
                || !minecraft.level.getBlockState(OBSIDIAN.north()).is(Blocks.SMOOTH_QUARTZ)) return;
            check("default_machine_particles_enabled", particlesSetting.get());
            minecraft.options.particles().set(ParticleStatus.ALL);
            ambientPhase = AmbientPhase.ENABLED;
            ambientStart = player.tickCount;
        }

        int elapsed = player.tickCount - ambientStart;
        if (ambientPhase == AmbientPhase.DISABLED && elapsed < 50) return;
        var dust = obsidianDust();
        ambientPeak = Math.max(ambientPeak, dust.size());
        check("dust_only_emits_from_the_exposed_face", dust.stream().allMatch(particle -> particle.getBoundingBox().getCenter().z > 11.8));
        if (ambientPhase == AmbientPhase.DISABLED) {
            check("disabled_machine_particles_keep_the_wall_clear", dust.isEmpty());
            if (elapsed < 100) return;
            captureAmbient("obsidian-particles-disabled");
            ambientPeaks.put("disabled", ambientPeak);
            particlesSetting.set(true);
            ambientPhase = AmbientPhase.RESTORED;
        } else {
            boolean nearView = dust.stream().map(particle -> particle.getBoundingBox().getCenter())
                .anyMatch(center -> center.x > 4221.5 && center.x < 4227.5 && center.y > 88.5 && center.y < 94.5);
            if (elapsed > 150) throw new AssertionError("No live Reinforced Obsidian dust in " + ambientPhase);
            if (elapsed < 50 || !nearView) return;
            if (ambientPhase == AmbientPhase.RESTORED) {
                check("reenabling_particles_restores_dust", true);
                captureAmbient("obsidian-particles-restored");
                ambientPeaks.put("restored", ambientPeak);
                finish(null);
                return;
            }
            check("reinforced_obsidian_smolders_in_the_stock_client", true);
            captureAmbient("obsidian-particles-enabled");
            ambientPeaks.put("enabled", ambientPeak);
            particlesSetting.set(false);
            ambientPhase = AmbientPhase.DISABLED;
        }
        ambientPeak = 0;
        ambientStart = player.tickCount;
    }

    private List<DustParticle> obsidianDust() {
        Map<ParticleRenderType, Queue<Particle>> particles = ObfuscationReflectionHelper.getPrivateValue(
            ParticleEngine.class, minecraft.particleEngine, "f_107289_");
        return particles.values().stream().flatMap(Queue::stream).filter(DustParticle.class::isInstance)
            .map(DustParticle.class::cast).filter(particle -> OBSIDIAN_DUST_AREA.contains(particle.getBoundingBox().getCenter())).toList();
    }

    private void captureAmbient(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_capture", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void verifySounds() {
        var soundManager = minecraft.getSoundManager();
        var resources = minecraft.getResourceManager();
        var random = RandomSource.create(0);
        for (var entry : List.of(EIOSounds.STIRLING, EIOSounds.SLICER, EIOSounds.SAG_MILL, EIOSounds.SOUL_BINDER, EIOSounds.VAT)) {
            var definition = soundManager.getSoundEvent(entry.get().getLocation());
            check("native_audio_asset_" + entry.getId().getPath(), definition != null
                && resources.getResource(definition.getSound(random).getPath()).isPresent());
        }
    }

    private void verifyModels() {
        var blocks = List.of(EIOBlocks.DARK_STEEL_LADDER.get(), EIOBlocks.DARK_STEEL_BARS.get(), EIOBlocks.DARK_STEEL_DOOR.get(),
            EIOBlocks.DARK_STEEL_TRAPDOOR.get(), EIOBlocks.END_STEEL_BARS.get(), EIOBlocks.REINFORCED_OBSIDIAN.get());
        var missing = minecraft.getModelManager().getMissingModel();
        int rendered = 0;
        for (var block : blocks) {
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = minecraft.getBlockRenderer().getBlockModel(state);
                if (model == missing || model.getParticleIcon() == missing.getParticleIcon()) {
                    throw new AssertionError("Missing state model or texture for " + state);
                }
                rendered++;
            }
            var itemModel = minecraft.getItemRenderer().getModel(new ItemStack(block), minecraft.level, null, 0);
            check("registered_inventory_model_" + id, itemModel != missing && itemModel.getParticleIcon() != missing.getParticleIcon());
        }
        check("all_201_steel_state_models_baked", rendered == 201);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO steel construction", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-ready-" + name));
    }

    private void signal(String name, Map<String, ?> data) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        var temporary = Files.writeString(signals.resolve(name + ".tmp"), new Gson().toJson(data));
        Files.move(temporary, signals.resolve(name + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyShift.setDown(false);
        if (ambientPhase == AmbientPhase.DISABLED) BaseConfig.CLIENT.MACHINE_PARTICLES.set(true);
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("travel", travel);
        result.put("ambientDustPeaks", ambientPeaks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_steel_construction", result);
    }
}

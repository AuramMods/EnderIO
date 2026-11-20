package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.FluidTankScreen;
import com.enderio.enderio.client.foundation.particle.MachineDustParticle;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

final class ClientGameplayParticlesSmoke {
    private enum Stage { TITLE, JOIN, ROD_USE, ROD_EFFECT, MILL_IDLE, MILL_ACTIVE, MILL_MINIMAL, MILL_RESTORED, MILL_FINISHED, DONE }
    private record RodTrial(String name, ParticleStatus detail, boolean showEffect) {}

    private static final List<RodTrial> ROD_TRIALS = List.of(new RodTrial("rod-all", ParticleStatus.ALL, true),
        new RodTrial("rod-minimal", ParticleStatus.MINIMAL, false), new RodTrial("rod-restored", ParticleStatus.ALL, true),
        new RodTrial("rod-rejected", ParticleStatus.ALL, false));
    private static final BlockPos TANK = new BlockPos(0, 80, 0);
    private static final BlockPos MILL = new BlockPos(6, 80, 8);
    private static final AABB ROD_AREA = new AABB(TANK).inflate(1.6);
    private static final AABB MILL_FACE = new AABB(5.7, 79.8, 8.95, 7.3, 82.2, 9.65);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int trialIndex;
    private int since = -1;
    private Particle tracked;
    private double firstY;
    private ParticleStatus originalDetail;

    ClientGameplayParticlesSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen screen) {
                throw new AssertionError("Disconnected at " + stage + ": " + screen.getNarrationMessage().getString());
            }
            if (System.nanoTime() - started > 177_000_000_000L) {
                throw new AssertionError("Gameplay particle client stopped at " + stage + " / rod trial " + trialIndex);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                String address = System.getProperty("enderio.clientqa.server");
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO particle effects", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.ROD_EFFECT && ROD_TRIALS.get(trialIndex).name().equals("rod-rejected")
                && minecraft.screen instanceof FluidTankScreen) {
                check("insufficient_xp_falls_through_to_the_native_tank_menu", true);
                player.closeContainer();
                return;
            }
            if (minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("survival_uses_packaged_dedicated_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    originalDetail = minecraft.options.particles().get();
                    minecraft.options.hideGui = true;
                    stage = Stage.ROD_USE;
                    signal("connected");
                }
                case ROD_USE, ROD_EFFECT -> observeRod();
                case MILL_IDLE, MILL_ACTIVE, MILL_MINIMAL, MILL_RESTORED, MILL_FINISHED -> observeMill();
                default -> { }
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish(error);
        }
    }

    private void observeRod() throws IOException {
        var player = minecraft.player;
        var trial = ROD_TRIALS.get(trialIndex);
        var effects = particles(SpellParticle.class, ROD_AREA);
        if (stage == Stage.ROD_USE) {
            if (!ready(trial.name()) || player.distanceToSqr(0.5, 80, 3.5) > 0.1 || !effects.isEmpty()
                || !player.getMainHandItem().is(EIOItems.EXPERIENCE_ROD.get()) || !minecraft.level.getBlockState(TANK).is(EIOBlocks.FLUID_TANK.get())) return;
            minecraft.options.particles().set(trial.detail());
            var hit = new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, TANK, false);
            check(trial.name() + "_uses_the_native_rod", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            enter(Stage.ROD_EFFECT);
            return;
        }

        int elapsed = elapsed();
        if (trial.showEffect()) {
            if (elapsed > 70) throw new AssertionError(trial.name() + " never showed a rising green particle");
            if (effects.isEmpty()) return;
            check(trial.name() + "_receives_one_effect", effects.size() == 1);
            var effect = effects.get(0);
            if (tracked == null) {
                tracked = effect;
                firstY = y(effect);
            }
            double rise = y(effect) - firstY;
            if (effect != tracked || rise < 0.07) return;
            var color = color(effect);
            check(trial.name() + "_keeps_the_baseline_green", color.distanceToSqr(new Vec3(0.27450980, 0.88627451, 0.29411765)) < 0.000001);
            check(trial.name() + "_uses_an_animated_spell_sprite", sprite(effect).startsWith("minecraft:effect_"));
            observations.put(trial.name(), Map.of("rise", rise, "sprite", sprite(effect)));
            capture(trial.name());
        } else {
            check(trial.name() + "_keeps_the_native_particle_queue_clear", effects.isEmpty());
            if (elapsed < 42) return;
            if (trial.name().equals("rod-rejected")) {
                check("insufficient_xp_falls_through_to_the_native_tank_menu",
                    checks.containsKey("insufficient_xp_falls_through_to_the_native_tank_menu"));
            }
            observations.put(trial.name(), Map.of("ticksWithoutAnEffect", elapsed));
        }
        signal(trial.name());
        enter(++trialIndex < ROD_TRIALS.size() ? Stage.ROD_USE : Stage.MILL_IDLE);
    }

    private void observeMill() throws IOException {
        var state = minecraft.level.getBlockState(MILL);
        if (minecraft.player.distanceToSqr(6.5, 80, 11.5) > 0.1 || !state.is(EIOBlocks.SAG_MILL.get())) return;
        var dust = particles(MachineDustParticle.class, MILL_FACE);
        var debris = particles(TerrainParticle.class, MILL_FACE);
        boolean powered = state.getValue(ProgressMachineBlock.POWERED);
        switch (stage) {
            case MILL_IDLE -> {
                if (!ready("mill-idle") || powered) return;
                check("idle_mill_emits_neither_effect", dust.isEmpty() && debris.isEmpty());
                if (elapsed() < 28) return;
                signal("mill-idle");
                enter(Stage.MILL_ACTIVE);
            }
            case MILL_ACTIVE, MILL_RESTORED -> {
                if (!ready("mill-started") || !powered) return;
                int age = elapsed();
                if (age > 125) throw new AssertionError("Powered SAG Mill did not render both particle types in " + stage);
                if (tracked == null || !dust.contains(tracked)) {
                    tracked = dust.isEmpty() ? null : dust.get(0);
                    if (tracked != null) firstY = y(tracked);
                    return;
                }
                double rise = y(tracked) - firstY;
                if (rise < 0.03 || debris.isEmpty()) return;
                String phase = stage == Stage.MILL_ACTIVE ? "mill-active" : "mill-restored";
                check(phase + "_uses_the_native_smoke_sprite", sprite((TextureSheetParticle) tracked).startsWith("minecraft:generic_"));
                var shade = color(tracked);
                check(phase + "_uses_the_purple_gray_dust_color", Math.abs(shade.x - shade.y - 9 / 255.0) < 0.00001
                    && Math.abs(shade.z - shade.x - 8 / 255.0) < 0.00001);
                check(phase + "_emits_rising_dust_and_debris_outside_its_front", rise >= 0.03 && !debris.isEmpty());
                observations.put(phase, Map.of("rise", rise, "dustCount", dust.size(), "debrisCount", debris.size()));
                capture(phase);
                if (stage == Stage.MILL_ACTIVE) {
                    minecraft.options.particles().set(ParticleStatus.MINIMAL);
                    enter(Stage.MILL_MINIMAL);
                } else {
                    signal("mill-restored");
                    enter(Stage.MILL_FINISHED);
                }
            }
            case MILL_MINIMAL -> {
                int age = elapsed();
                if (age < 45) return;
                check("minimal_suppresses_both_effects_while_the_client_mill_stays_powered", powered && dust.isEmpty() && debris.isEmpty());
                if (age < 75) return;
                if (!Files.exists(output.resolve("signals/mill-minimal.json"))) {
                    observations.put("mill-minimal", Map.of("ticksWithoutEffects", age - 45));
                    capture("mill-minimal");
                    signal("mill-minimal");
                }
                if (!ready("mill-resume")) return;
                minecraft.options.particles().set(ParticleStatus.ALL);
                enter(Stage.MILL_RESTORED);
            }
            case MILL_FINISHED -> {
                if (!ready("mill-finished") || powered) return;
                int age = elapsed();
                if (age < 45) return;
                check("completed_mill_stops_both_effects_with_all_particles_enabled", dust.isEmpty() && debris.isEmpty());
                if (age < 64) return;
                observations.put("mill-finished", Map.of("ticksWithoutEffects", age - 45));
                finish(null);
            }
            default -> { }
        }
    }

    private <T extends Particle> List<T> particles(Class<T> type, AABB area) {
        Map<ParticleRenderType, Queue<Particle>> live = ObfuscationReflectionHelper.getPrivateValue(
            ParticleEngine.class, minecraft.particleEngine, "f_107289_");
        return live.values().stream().flatMap(Queue::stream).filter(type::isInstance).map(type::cast)
            .filter(candidate -> area.contains(candidate.getBoundingBox().getCenter())).toList();
    }

    private static Vec3 color(Particle particle) {
        float red = ObfuscationReflectionHelper.getPrivateValue(Particle.class, particle, "f_107227_");
        float green = ObfuscationReflectionHelper.getPrivateValue(Particle.class, particle, "f_107228_");
        float blue = ObfuscationReflectionHelper.getPrivateValue(Particle.class, particle, "f_107229_");
        return new Vec3(red, green, blue);
    }

    private static String sprite(TextureSheetParticle particle) {
        TextureAtlasSprite sprite = ObfuscationReflectionHelper.getPrivateValue(TextureSheetParticle.class, particle, "f_108321_");
        return sprite.contents().name().toString();
    }

    private static double y(Particle particle) {
        return particle.getBoundingBox().getCenter().y;
    }

    private int elapsed() {
        if (since < 0) since = minecraft.player.tickCount;
        return minecraft.player.tickCount - since;
    }

    private void enter(Stage next) {
        stage = next;
        since = -1;
        tracked = null;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var file = Files.writeString(directory.resolve(name + ".tmp"), "{}");
        Files.move(file, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_guarded_capture", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable error) {
        stage = Stage.DONE;
        if (originalDetail != null) minecraft.options.particles().set(originalDetail);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", error == null);
        result.put("checks", checks);
        result.put("observations", observations);
        result.put("captures", captures);
        if (error != null) result.put("error", error.toString());
        ClientSmoke.complete("client_gameplay_particles", result);
    }
}

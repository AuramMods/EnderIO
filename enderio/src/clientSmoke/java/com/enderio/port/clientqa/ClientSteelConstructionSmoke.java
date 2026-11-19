package com.enderio.port.clientqa;

import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.init.EIOBlocks;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientSteelConstructionSmoke {
    private enum Stage { TITLE, JOIN, POSITION, MEASURE, VERIFY, DONE }
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

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Double> travel = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int trialIndex;
    private double startY;
    private int firstTick;
    private int firstViewTick = -1;

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
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
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
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("travel", travel);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_steel_construction", result);
    }
}

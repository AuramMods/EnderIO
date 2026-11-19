package com.enderio.port.clientqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientDecorativeRedstoneSmoke {
    private enum Stage { TITLE, JOIN, PLATES_UP, PLATES_DOWN, LEVERS, FIRE, FIRED, DONE }
    private record Sample(BlockPos pos, ResourceLocation id) {
        boolean matches(BlockState state) {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
        }
    }
    private record QuadAppearance(int vertices, ResourceLocation sprite) {}

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int readyAt = -1;
    private List<Sample> levers = List.of();
    private List<Sample> plates = List.of();

    ClientDecorativeRedstoneSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen screen) {
                throw new AssertionError("Decorative redstone disconnected in " + stage + ": " + screen.getNarrationMessage().getString());
            }
            if (System.nanoTime() - started > 165_000_000_000L) {
                throw new AssertionError("Decorative redstone timed out in " + stage);
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
                    var layout = JsonParser.parseString(Files.readString(output.resolve("layout.json"))).getAsJsonObject();
                    levers = samples(layout.getAsJsonArray("levers"));
                    plates = samples(layout.getAsJsonArray("plates"));
                    verifyModels();
                    minecraft.options.hideGui = true;
                    signal("connected");
                    stage = Stage.PLATES_UP;
                }
                case PLATES_UP -> {
                    if (!ready("plates-up") || player.distanceToSqr(4103.5, 87, 13.5) > 0.1
                        || !allMatch(plates, false) || !settled()) return;
                    capture("plates-up");
                    signal("plates-up");
                    stage = Stage.PLATES_DOWN;
                }
                case PLATES_DOWN -> {
                    if (!ready("plates-down") || !allMatch(plates, true) || !settled()) return;
                    capture("plates-down");
                    signal("plates-down");
                    stage = Stage.LEVERS;
                }
                case LEVERS -> {
                    if (!ready("levers") || player.distanceToSqr(4102.5, 86, 2.5) > 0.1
                        || !allMatch(levers, false) || !settled()) return;
                    capture("levers-at-rest");
                    signal("levers");
                    stage = Stage.FIRE;
                }
                case FIRE -> {
                    if (!ready("fire") || player.distanceToSqr(4102.5, 80, 9.5) > 0.1) return;
                    for (var lever : levers) {
                        var pos = lever.pos();
                        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 0.2, 0), Direction.UP, pos, false);
                        check("native_use_" + lever.id().getPath(),
                            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    }
                    signal("uses-sent");
                    stage = Stage.FIRED;
                }
                case FIRED -> {
                    if (!ready("fired") || player.distanceToSqr(4102.5, 86, 2.5) > 0.1
                        || !allMatch(levers, true) || !settled()) return;
                    capture("levers-pulled");
                    signal("fired");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        var address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO decorative redstone", address, true), false);
        stage = Stage.JOIN;
    }

    private static List<Sample> samples(JsonArray scene) {
        var samples = new ArrayList<Sample>();
        for (var cell : scene) {
            var value = cell.getAsJsonObject();
            samples.add(new Sample(new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(), value.get("z").getAsInt()),
                ResourceLocation.parse(value.get("id").getAsString())));
        }
        return List.copyOf(samples);
    }

    private void verifyModels() {
        var modelManager = minecraft.getModelManager();
        var absent = modelManager.getMissingModel().getParticleIcon().contents().name();
        var cells = new ArrayList<>(levers);
        cells.addAll(plates);
        check("all_26_registered_decorative_blocks", cells.stream().map(Sample::id).distinct().count() == 26);
        int states = 0;
        for (var cell : cells) {
            var block = BuiltInRegistries.BLOCK.get(cell.id());
            var idle = block.defaultBlockState();
            var pressed = idle.hasProperty(WeightedPressurePlateBlock.POWER)
                ? idle.setValue(WeightedPressurePlateBlock.POWER, 1)
                : idle.setValue(PressurePlateBlock.POWERED, true);
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = minecraft.getBlockRenderer().getBlockModel(state);
                var geometry = appearance(state);
                if (model == modelManager.getMissingModel() || geometry.isEmpty()
                    || geometry.stream().anyMatch(quad -> quad.sprite().equals(absent))) {
                    throw new AssertionError("Missing textured geometry for " + cell.id() + " " + state.getValues());
                }
                states++;
            }
            check("different_pressed_geometry_" + cell.id().getPath(), !appearance(idle).equals(appearance(pressed)));
            var itemModel = minecraft.getItemRenderer().getModel(new ItemStack(block), minecraft.level, null, 0);
            check("baked_inventory_model_" + cell.id().getPath(), itemModel != modelManager.getMissingModel()
                && !itemModel.getParticleIcon().contents().name().equals(absent));
        }
        check("all_300_textured_blockstates", states == 300);
    }

    private List<QuadAppearance> appearance(BlockState state) {
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var random = RandomSource.create(42);
        var quads = new ArrayList<BakedQuad>(model.getQuads(state, null, random));
        for (var face : Direction.values()) quads.addAll(model.getQuads(state, face, random));
        return quads.stream().map(quad -> new QuadAppearance(Arrays.hashCode(quad.getVertices()),
            quad.getSprite().contents().name())).toList();
    }

    private boolean allMatch(List<Sample> cells, boolean activated) {
        return cells.stream().allMatch(cell -> {
            var state = minecraft.level.getBlockState(cell.pos());
            if (!cell.matches(state)) return false;
            if (state.hasProperty(WeightedPressurePlateBlock.POWER)) {
                return (state.getValue(WeightedPressurePlateBlock.POWER) > 0) == activated;
            }
            return state.getValue(LeverBlock.POWERED) == activated;
        });
    }

    private boolean settled() {
        if (readyAt < 0) readyAt = minecraft.player.tickCount;
        if (minecraft.player.tickCount - readyAt < 12) return false;
        readyAt = -1;
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-ready-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        var temporary = Files.writeString(signals.resolve(name + ".tmp"), "{}");
        Files.move(temporary, signals.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var capture = ClientSmoke.capture(name + ".png");
        captures.put(name, capture);
        check(name + "_silent_capture", Boolean.TRUE.equals(capture.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_decorative_redstone", result);
    }
}

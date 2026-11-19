package com.enderio.port.clientqa;

import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ClientUltimineSmoke {
    private enum Stage { TITLE, CONNECT, TARGET, PREVIEW, MINING, DONE }

    private record Trial(String name, BlockPos origin) {
        Set<BlockPos> expectedPreview() {
            return Set.of(origin, origin.east());
        }
    }

    private static final Trial[] TRIALS = {
        new Trial("facades", new BlockPos(2879, 80, 8)),
        new Trial("conduits", new BlockPos(2879, 80, 21))
    };

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> previews = new LinkedHashMap<>();
    private final boolean withUltimine;
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int trialIndex;
    private int ticks;
    private KeyMapping key;
    private Field shapeBlocks;

    ClientUltimineSmoke(boolean withUltimine) {
        this.withUltimine = withUltimine;
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (++ticks > 400 && stage != Stage.TITLE) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> connect();
                case CONNECT -> joined();
                case TARGET -> target();
                case PREVIEW -> preview();
                case MINING -> mine();
                case DONE -> { }
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
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Ultimine test", address, true), false);
        enter(Stage.CONNECT);
    }

    private void joined() throws ReflectiveOperationException, IOException {
        if (minecraft.player == null || minecraft.screen != null) return;
        var connection = minecraft.getConnection().getConnection();
        check("real_dedicated_survival_connection", minecraft.getSingleplayerServer() == null
            && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress
            && !minecraft.player.isCreative() && !minecraft.player.isSpectator());
        check("expected_ultimine_installation", ModList.get().isLoaded("ftbultimine") == withUltimine);
        if (withUltimine) {
            key = FTBUltimineClient.keyBinding;
            shapeBlocks = FTBUltimineClient.class.getDeclaredField("shapeBlocks");
            shapeBlocks.setAccessible(true);
        }
        signal("connected");
        enter(Stage.TARGET);
    }

    private void target() {
        if (!Files.exists(output.resolve("server-" + trialIndex)) || minecraft.screen != null
            || !minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)
            || !minecraft.player.hasEffect(MobEffects.DIG_SPEED) || hit() == null) return;
        if (key != null) key.setDown(true);
        enter(Stage.PREVIEW);
    }

    private void preview() throws ReflectiveOperationException, IOException {
        Trial trial = TRIALS[trialIndex];
        if (shapeBlocks != null) {
            var blocks = (List<?>) shapeBlocks.get(FTBUltimine.instance.proxy);
            Set<?> positions = Set.copyOf(blocks);
            previews.put(trial.name(), positions.stream().map(Object::toString).sorted().toList());
            if (!positions.equals(trial.expectedPreview()) && ticks < 110) return;
            check(trial.name() + "_preview_selects_only_matching_neighbors", positions.equals(trial.expectedPreview()));
        }
        var hit = hit();
        if (hit == null || minecraft.screen != null) return;
        var capture = ClientSmoke.capture(trial.name() + "-before.png");
        captures.put(trial.name(), capture);
        check(trial.name() + "_client_is_hidden_and_muted", Boolean.TRUE.equals(capture.get("passed")));
        check(trial.name() + "_started_survival_mining", minecraft.gameMode.startDestroyBlock(trial.origin(), hit.getDirection()));
        enter(Stage.MINING);
    }

    private void mine() throws IOException {
        Trial trial = TRIALS[trialIndex];
        if (ticks < 30) return;
        if (key != null) key.setDown(false);
        signal(trial.name());
        if (++trialIndex == TRIALS.length) {
            finish(null);
        } else {
            enter(Stage.TARGET);
        }
    }

    private BlockHitResult hit() {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(TRIALS[trialIndex].origin()) ? hit : null;
    }

    private void enter(Stage next) {
        stage = next;
        ticks = 0;
    }

    private void signal(String name) throws IOException {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Path temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        if (key != null) key.setDown(false);
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("previews", previews);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_ultimine", result);
    }
}

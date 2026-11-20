package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.conduits.model.facades.FacadeUtil;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import com.google.gson.Gson;
import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.client.FTBUltimineClient;
import dev.ftb.mods.ftbultimine.net.ModeChangedPacket;
import dev.ftb.mods.ftbultimine.shape.ShapeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
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
import java.util.stream.Collectors;

final class ClientUltimineSmoke {
    private enum Stage { TITLE, CONNECT, TARGET, PREVIEW, MINING, DONE }

    private record Trial(String name, BlockPos origin) {
        Set<BlockPos> expectedPreview() {
            return Set.of(origin, origin.east());
        }
    }

    private record ShapeTrial(String name, int[] origin, List<int[]> preview, String shape, boolean ghost,
                              boolean protectOrigin, boolean last) {
        BlockPos originPos() { return position(origin); }
        Set<BlockPos> positions() { return preview.stream().map(ShapeTrial::position).collect(Collectors.toSet()); }
        private static BlockPos position(int[] xyz) { return new BlockPos(xyz[0], xyz[1], xyz[2]); }
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
    private final Map<String, String> modes = new LinkedHashMap<>();
    private final boolean withUltimine;
    private final boolean directional;
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int trialIndex;
    private int ticks;
    private KeyMapping key;
    private Field shapeBlocks;
    private Field shapeIndex;
    private ShapeTrial shapeTrial;

    ClientUltimineSmoke(boolean withUltimine) {
        this(withUltimine, false);
    }

    ClientUltimineSmoke(boolean withUltimine, boolean directional) {
        this.withUltimine = withUltimine;
        this.directional = directional;
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (++ticks > 800 && stage != Stage.TITLE) throw new AssertionError("Timed out in " + stage + " at trial " + trialIndex);
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
        if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
        var connection = minecraft.getConnection().getConnection();
        check("real_dedicated_survival_connection", minecraft.getSingleplayerServer() == null
            && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress
            && !minecraft.player.isCreative() && !minecraft.player.isSpectator());
        check("expected_ultimine_installation", ModList.get().isLoaded("ftbultimine") == withUltimine);
        if (withUltimine) {
            key = FTBUltimineClient.keyBinding;
            shapeBlocks = FTBUltimineClient.class.getDeclaredField("shapeBlocks");
            shapeBlocks.setAccessible(true);
            if (directional) {
                shapeIndex = FTBUltimineClient.class.getDeclaredField("shapeIdx");
                shapeIndex.setAccessible(true);
            }
        }
        signal("connected");
        enter(Stage.TARGET);
    }

    private void target() throws IOException {
        if (directional && shapeTrial == null) {
            var request = output.resolve("server-" + trialIndex + ".json");
            if (!Files.exists(request)) return;
            shapeTrial = new Gson().fromJson(Files.readString(request), ShapeTrial.class);
        }
        if ((!directional && !Files.exists(output.resolve("server-" + trialIndex))) || minecraft.screen != null
            || !minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)
            || !minecraft.player.hasEffect(MobEffects.DIG_SPEED) || hit() == null) return;
        if (directional && minecraft.player.getOffhandItem().is(EIOItems.YETA_WRENCH.get()) != shapeTrial.ghost()) return;
        if (key != null) key.setDown(true);
        enter(Stage.PREVIEW);
    }

    private void preview() throws ReflectiveOperationException, IOException {
        if (shapeBlocks != null) {
            if (directional) {
                String mode = ShapeRegistry.getShape(shapeIndex.getInt(FTBUltimine.instance.proxy)).getName();
                modes.put(name(), mode);
                if (!mode.equals(shapeTrial.shape())) {
                    if (ticks % 10 == 4) new ModeChangedPacket(true).sendToServer();
                    return;
                }
            }
            var blocks = (List<?>) shapeBlocks.get(FTBUltimine.instance.proxy);
            Set<?> positions = Set.copyOf(blocks);
            previews.put(name(), positions.stream().map(Object::toString).sorted().toList());
            var expected = directional ? shapeTrial.positions() : TRIALS[trialIndex].expectedPreview();
            if (!positions.equals(expected) && ticks < 160) return;
            check(name() + "_preview_selects_only_matching_neighbors", positions.equals(expected));
        }
        var hit = hit();
        if (hit == null || minecraft.screen != null) return;
        if (directional) {
            check(name() + "_starts_on_the_south_face", hit.getDirection() == Direction.SOUTH);
            check(name() + "_uses_the_expected_facade_visibility", FacadeUtil.areFacadesVisible(minecraft.player) != shapeTrial.ghost());
        }
        var capture = ClientSmoke.capture(name() + "-before.png");
        captures.put(name(), capture);
        check(name() + "_client_is_hidden_and_muted", Boolean.TRUE.equals(capture.get("passed")));
        check(name() + "_started_survival_mining", minecraft.gameMode.startDestroyBlock(origin(), hit.getDirection()));
        enter(Stage.MINING);
    }

    private void mine() throws IOException {
        if (ticks < 30) return;
        if (key != null) key.setDown(false);
        if (directional && shapeTrial.protectOrigin()) {
            var entity = minecraft.level.getBlockEntity(origin());
            boolean restored = entity instanceof ConduitBundleBlockEntity bundle && bundle.getFacadeBlock() == Blocks.OAK_PLANKS
                && bundle.getConduits().size() == 2;
            if (!restored && ticks < 100) return;
            check(name() + "_client_restores_the_canceled_facade_and_both_conduits", restored);
        }
        signal(name());
        trialIndex++;
        if (directional ? shapeTrial.last() : trialIndex == TRIALS.length) {
            finish(null);
        } else {
            shapeTrial = null;
            enter(Stage.TARGET);
        }
    }

    private BlockHitResult hit() {
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(origin()) ? hit : null;
    }

    private String name() {
        return directional ? shapeTrial.name() : TRIALS[trialIndex].name();
    }

    private BlockPos origin() {
        return directional ? shapeTrial.originPos() : TRIALS[trialIndex].origin();
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
        if (directional) result.put("modes", modes);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_ultimine", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientJadeSmoke {
    private enum Stage { TITLE, CONNECT, BOUND, CHANGED, UNBOUND, COVERED, WRENCH, COVERED_AGAIN, DONE }

    record Overlay(String block, String fakeItem, boolean blockEntity, boolean connected,
                   String soul, String serverType, String text, boolean soulLine, boolean energyLine) {}

    private static final BlockPos BOUND = new BlockPos(2784, 80, 8);
    private static final BlockPos UNBOUND = BOUND.east(3);
    private static final BlockPos CONDUIT = BOUND.east(6);
    private static final String ENGINE = "enderio:soul_engine";
    private static final String PAINT = "minecraft:oak_planks";

    private static BlockPos observedPosition;
    private static Overlay observedOverlay;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean jade;
    private final boolean serverJade;
    private Stage stage = Stage.TITLE;
    private int readyTick = -1;

    ClientJadeSmoke(boolean jade, boolean serverJade) {
        this.jade = jade;
        this.serverJade = serverJade;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    static void observe(BlockPos position, Overlay overlay) {
        observedPosition = position;
        observedOverlay = overlay;
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 140_000_000_000L) {
                throw new AssertionError("Jade client did not complete stage " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Jade", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("expected_jade_installation", ModList.get().isLoaded("jade") == jade);
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.BOUND;
                }
                case BOUND -> {
                    if (!ready("server-bound", BOUND) || !soulArrived("minecraft:blaze")) return;
                    inspectEngine("bound-engine", "Blaze");
                    signal("bound");
                    stage = Stage.CHANGED;
                }
                case CHANGED -> {
                    if (!ready("server-changed", BOUND) || !soulArrived("minecraft:phantom")) return;
                    inspectEngine("changed-engine", "Phantom");
                    signal("changed");
                    stage = Stage.UNBOUND;
                }
                case UNBOUND -> {
                    if (!ready("server-unbound", UNBOUND) || !soulArrived("")) return;
                    inspectEngine("unbound-engine", "");
                    signal("unbound");
                    stage = Stage.COVERED;
                }
                case COVERED -> {
                    if (!ready("server-covered", CONDUIT)) return;
                    inspectConduit("painted-conduit", true);
                    minecraft.player.getInventory().selected = 8;
                    next(Stage.WRENCH);
                }
                case WRENCH -> {
                    if (!ready("server-covered", CONDUIT)
                        || !minecraft.player.getMainHandItem().is(EIOItems.YETA_WRENCH.get())) return;
                    inspectConduit("wrench-conduit", false);
                    minecraft.player.getInventory().selected = 0;
                    next(Stage.COVERED_AGAIN);
                }
                case COVERED_AGAIN -> {
                    if (!ready("server-covered", CONDUIT)) return;
                    inspectConduit("restored-paint", true);
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean ready(String marker, BlockPos target) {
        if (!Files.exists(output.resolve(marker)) || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target)) return false;
        if (readyTick < 0) {
            readyTick = minecraft.player.tickCount;
            minecraft.gui.getChat().clearMessages(false);
        }
        return minecraft.player.tickCount - readyTick >= 20
            && (!jade || target.equals(observedPosition));
    }

    private boolean soulArrived(String expected) {
        return !serverJade || observedOverlay.serverType().equals(ENGINE) && observedOverlay.soul().equals(expected);
    }

    private void inspectEngine(String name, String soulName) throws IOException {
        capture(name);
        if (!jade) {
            check(name + "_native_target_without_jade", minecraft.level.getBlockEntity(observedHitPos()) instanceof SoulEngineBlockEntity engine
                && (soulName.isEmpty() ? !engine.hasSoul() : engine.hasSoul()
                    && BuiltInRegistries.ENTITY_TYPE.get(engine.getBoundSoul().entityTypeId()).getDescription().getString().equals(soulName)));
            return;
        }
        var view = observedOverlay;
        check(name + "_jade_server_status", view.connected() == serverJade);
        check(name + "_jade_draws_soul_engine_title", view.block().equals(ENGINE) && view.blockEntity()
            && view.text().contains("Soul Engine"));
        boolean showSoul = serverJade && !soulName.isEmpty();
        check(name + "_exact_bound_soul_line", view.soulLine() == showSoul
            && (showSoul ? view.text().contains("Bound Soul: " + soulName) : !view.text().contains("Bound Soul:")));
        check(name + "_uses_current_soul_only", !view.text().contains("Bound Soul: " + ("Blaze".equals(soulName) ? "Phantom" : "Blaze")));
    }

    private void inspectConduit(String name, boolean covered) throws IOException {
        capture(name);
        var pos = observedHitPos();
        var clone = minecraft.level.getBlockState(pos).getCloneItemStack(minecraft.hitResult, minecraft.level, pos, minecraft.player);
        check(name + "_native_pick_result", covered ? clone.is(Items.OAK_PLANKS)
            : clone.is(EIOItems.CONDUIT.get()) && "enderio:energy".equals(String.valueOf(EnderIODataComponents.CONDUIT.getId(clone))));
        if (!jade) return;
        var view = observedOverlay;
        if (covered) {
            check(name + "_jade_only_receives_the_paint", view.block().equals(PAINT) && view.fakeItem().equals(PAINT)
                && !view.blockEntity() && view.serverType().isEmpty());
            check(name + "_jade_draws_paint_without_conduit_data", view.text().contains("Oak Planks")
                && !view.text().contains("Energy Conduit") && !view.energyLine() && !view.soulLine());
        } else {
            check(name + "_jade_draws_the_actual_energy_conduit", view.block().equals("enderio:conduit") && view.blockEntity()
                && view.fakeItem().equals("minecraft:air") && view.text().contains("Energy Conduit"));
            check(name + "_jade_uses_the_revealed_conduit_data",
                view.serverType().equals(serverJade ? "enderio:conduit" : ""));
        }
    }

    private BlockPos observedHitPos() {
        return ((BlockHitResult) minecraft.hitResult).getBlockPos();
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        if (jade) frame.put("jadeOverlay", observedOverlay);
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
    }

    private void next(Stage target) {
        readyTick = -1;
        stage = target;
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
        readyTick = -1;
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable error) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", error == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (observedOverlay != null) result.put("lastJadeOverlay", observedOverlay);
        if (error != null) result.put("error", error.toString());
        ClientSmoke.complete("client_jade", result);
    }
}

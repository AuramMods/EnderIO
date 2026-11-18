package com.enderio.port.clientqa;

import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientSolarCarrySmoke {
    private enum Stage { TITLE, CONNECT, MINING, PICKUP, PLACEMENT, DONE }

    private static final BlockPos SOURCE = new BlockPos(24, 80, 24);
    private static final BlockPos DESTINATION = new BlockPos(27, 80, 24);
    private static final int CHARGE = 23789;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientSolarCarrySmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 165_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO local solar item test", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            var panel = EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get();
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (minecraft.player.distanceToSqr(24.5, 80, 27.5) > 0.1
                        || !minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)
                        || !minecraft.level.getBlockState(SOURCE).is(panel)) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    check("normal_survival_mining_action", minecraft.gameMode.startDestroyBlock(SOURCE, Direction.SOUTH));
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(SOURCE)) return;
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    if (minecraft.player.distanceToSqr(27.5, 80, 27.5) > 0.1) return;
                    var inventory = minecraft.player.getInventory();
                    int slot = 0;
                    while (slot < 9 && !inventory.getItem(slot).is(panel.asItem())) {
                        slot++;
                    }
                    if (slot == 9) return;
                    var pickedUp = inventory.getItem(slot);
                    var soul = EIODataComponents.SOUL.get(pickedUp);
                    check("collected_exactly_one_charged_panel", pickedUp.getCount() == 1
                        && EIODataComponents.ENERGY.getOrDefault(pickedUp, 0) == CHARGE
                        && soul != null && soul.entityType() == EntityType.PHANTOM);
                    inventory.selected = slot;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(DESTINATION), Direction.UP, DESTINATION.below(), false));
                    signal("placement");
                    stage = Stage.PLACEMENT;
                }
                case PLACEMENT -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    check("placement_consumes_the_picked_up_stack", minecraft.player.getMainHandItem().isEmpty()
                        && minecraft.level.isEmptyBlock(SOURCE) && minecraft.level.getBlockState(DESTINATION).is(panel));
                    var capture = ClientSmoke.capture("solar-replaced.png");
                    captures.put("solar-replaced", capture);
                    check("silent_hidden_replacement", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void signal(String name) throws Exception {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_solar_carry", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.SoulEngineScreen;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineMenu;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSoulEngineSmoke {
    private enum Stage { TITLE, JOIN, PLACE, PLACED, OPEN, MENU, LAVA, WATER, DONE }

    private static final BlockPos ENGINE = new BlockPos(1452, 80, 8);
    private static final BlockPos CARRIED = new BlockPos(1459, 80, 9);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientSoulEngineSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 195_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Soul Engine test", address, true), false);
                    stage = Stage.JOIN;
                }
                case JOIN -> {
                    if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    minecraft.player.getInventory().selected = 0;
                    var stack = minecraft.player.getMainHandItem();
                    if (!near(1459.5, 12.5) || !stack.is(EIOBlocks.SOUL_ENGINE_ITEM.get())
                        || !minecraft.level.getBlockState(CARRIED.below()).is(Blocks.STONE)) return;
                    check("carried_legacy_item_has_synced_blaze_soul", Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).entityType() == EntityType.BLAZE);
                    if (use(new BlockHitResult(Vec3.atBottomCenterOf(CARRIED), Direction.UP, CARRIED.below(), false)).consumesAction()) {
                        stage = Stage.PLACED;
                    }
                }
                case PLACED -> {
                    if (!minecraft.player.getMainHandItem().isEmpty() || !hasBlaze(CARRIED)) return;
                    signal("placed");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!near(1452.5, 11.5) || !minecraft.player.getMainHandItem().isEmpty()
                        || !hasBlaze(CARRIED) || !hasBlaze(ENGINE)) return;
                    check("replanted_engine_syncs_blaze_to_client", hasBlaze(CARRIED));
                    use(new BlockHitResult(new Vec3(1452.5, 80.5, 9), Direction.SOUTH, ENGINE, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SoulEngineScreen)
                        || !(minecraft.player.containerMenu instanceof SoulEngineMenu menu)
                        || menu.getEnergyStorage() == null || menu.getFluidTank() == null || menu.getBurnRate() == 0
                        || menu.getEnergyPerMillibucket() == 0 || menu.getRedstoneControl() == null
                        || !menu.getSlot(0).hasItem()) return;
                    var fuel = menu.getFluidTank().contents();
                    check("native_menu_syncs_bound_blaze_tagged_lava_capacitor_and_charge", menu.isCapacitorInstalled()
                        && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()) && menu.getEnergyStorage().energyStored() == 12345
                        && fuel.getFluid().isSame(Fluids.LAVA) && fuel.getAmount() == 3 && fuel.getTag() != null
                        && fuel.getTag().getInt("PortSoulEngine") == 70 && menu.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE);
                    check("server_generation_multiplier_is_synced_to_menu", menu.getBurnRate() == 1 && menu.getEnergyPerMillibucket() == 1200);
                    capture("soul-engine-saved");
                    menu.setRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
                    signal("enabled");
                    stage = Stage.LAVA;
                }
                case LAVA -> {
                    if (!Files.exists(output.resolve("server-lava"))) return;
                    var menu = (SoulEngineMenu) minecraft.player.containerMenu;
                    if (menu.getEnergyStorage().energyStored() != 15945 || !menu.getFluidTank().contents().isEmpty()
                        || !hasState(menu, MachineState.EMPTY_TANK)) return;
                    check("resumed_lava_cycle_syncs_exact_energy_and_empty_tank", menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE
                        && !hasState(menu, MachineState.NOT_SOULBOUND));
                    signal("lava-seen");
                    stage = Stage.WATER;
                }
                case WATER -> {
                    if (!Files.exists(output.resolve("server-water"))) return;
                    var menu = (SoulEngineMenu) minecraft.player.containerMenu;
                    var rule = EngineSoul.ENGINE.matches(EntityType.BLAZE).orElse(null);
                    if (rule == null || !rule.fluid().equals("minecraft:water") || menu.getEnergyStorage().energyStored() != 18645
                        || menu.getEnergyPerMillibucket() != 1350) return;
                    check("open_menu_receives_reloaded_rule_and_server_energy_rate", rule.tickpermb() == 4 && menu.getBurnRate() == 1
                        && menu.getFluidTank().contents().isEmpty() && !hasState(menu, MachineState.NOT_SOULBOUND));
                    capture("soul-engine-reloaded");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean near(double x, double z) {
        return minecraft.player.distanceToSqr(x, 80, z) < 0.1;
    }

    private boolean hasBlaze(BlockPos pos) {
        return minecraft.level.getBlockEntity(pos) instanceof SoulEngineBlockEntity engine && engine.getEntityType() == EntityType.BLAZE;
    }

    private static boolean hasState(SoulEngineMenu menu, MachineState expected) {
        return menu.getMachineStates().stream().anyMatch(state -> state.type() == expected.type()
            && state.component().getString().equals(expected.component().getString()));
    }

    private InteractionResult use(BlockHitResult hit) {
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_render_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_soul_engine", result);
    }
}

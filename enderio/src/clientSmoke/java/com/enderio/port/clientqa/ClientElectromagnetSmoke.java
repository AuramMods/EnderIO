package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.keymaps.KeymapHandler;
import com.enderio.enderio.compat.curios.CuriosCompat;
import com.enderio.enderio.init.EIOItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientElectromagnetSmoke {
    private enum Stage { TITLE, JOIN, START, ACTIVE, STOPPED, RESTORED, VERIFY, DONE }

    private static final int START_CHARGE = 50000;
    private static final int ORDINARY_RESERVE = 34000;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final boolean curios = ModList.get().isLoaded("curios");
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;

    ClientElectromagnetSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 160_000_000_000L) {
                throw new AssertionError("Electromagnet stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.screen != null) return;
            var inventory = player.getInventory();
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = reopened ? Stage.RESTORED : Stage.START;
                }
                case START -> {
                    if (!ready("start") || player.distanceToSqr(4168.5, 80, 8.5) > 0.1) return;
                    var active = chosen();
                    if (active.isEmpty() || ItemStackEnergy.getEnergyStored(active) != START_CHARGE) return;
                    check("the_charged_magnet_starts_off", !active.hasFoil());
                    toggle();
                    stage = Stage.ACTIVE;
                }
                case ACTIVE -> {
                    var active = chosen();
                    if (!active.hasFoil()) return;
                    if (!checks.containsKey("saw_active_charge")) {
                        check("the_native_key_activates_the_expected_magnet", !curios
                            || (!inventory.getItem(2).hasFoil() && ItemStackEnergy.getEnergyStored(inventory.getItem(2)) == ORDINARY_RESERVE));
                        check("saw_active_charge", ItemStackEnergy.getEnergyStored(active) > 0);
                        capture("active-electromagnet");
                    }
                    if (!ready("stop") || inventory.countItem(Items.DIAMOND) != 7) return;
                    check("attraction_and_pickup_synchronize_to_the_player", inventory.countItem(Items.APPLE) == 0
                        && inventory.countItem(Items.COPPER_INGOT) == 0 && ItemStackEnergy.getEnergyStored(active) < START_CHARGE);
                    toggle();
                    stage = Stage.STOPPED;
                }
                case STOPPED -> {
                    var active = chosen();
                    if (active.isEmpty() || active.hasFoil()) return;
                    check("the_second_key_press_disables_the_same_stack", ItemStackEnergy.getEnergyStored(active) < START_CHARGE);
                    capture("stopped-electromagnet");
                    signal("stopped");
                    stage = Stage.VERIFY;
                }
                case RESTORED -> {
                    if (!ready("restored")) return;
                    int savedCharge = Integer.parseInt(Files.readString(output.resolve("saved-charge.txt")));
                    var restored = chosen();
                    if (restored.isEmpty() || ItemStackEnergy.getEnergyStored(restored) != savedCharge) return;
                    check("the_reconnected_client_receives_the_disabled_magnet_and_original_pickup", !restored.hasFoil()
                        && inventory.countItem(Items.DIAMOND) == 7 && inventory.countItem(Items.APPLE) == 0
                        && inventory.countItem(Items.COPPER_INGOT) == 0);
                    capture("restored-electromagnet");
                    finish(null);
                }
                case VERIFY -> {
                    if (ready("finish")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private ItemStack chosen() {
        if (curios) {
            return CuriosCompat.getActiveCurios(minecraft.player, stack -> stack.is(EIOItems.ELECTROMAGNET.get()))
                .filter(stacks -> !stacks.isEmpty()).map(stacks -> stacks.get(0)).orElse(ItemStack.EMPTY);
        }
        return minecraft.player.getInventory().getItem(2);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Electromagnet", address, true), false);
        stage = Stage.JOIN;
    }

    private static void toggle() {
        KeyMapping.click(InputConstants.getKey(KeymapHandler.TOGGLE_MAGNET_KEY.get().saveString()));
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_electromagnet", result);
    }
}

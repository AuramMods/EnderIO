package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.keymaps.KeymapHandler;
import com.enderio.enderio.compat.curios.CuriosCompat;
import com.enderio.enderio.init.EIODataComponents;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientCuriosSmoke {
    private enum Stage { TITLE, CONNECT, CHARGE, MAGNET, AIM, RELEASE, BLINK, RESTORED, SERVER, DONE }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restart = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final boolean curios = ModList.get().isLoaded("curios");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> capture = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientCuriosSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 150_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO local Curios test", address, true), false);
                stage = Stage.CONNECT;
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || minecraft.level == null || minecraft.player == null
            || minecraft.screen != null || stage == Stage.DONE) return;
        try {
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_connection", minecraft.getSingleplayerServer() == null
                        && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = restart ? Stage.RESTORED : Stage.CHARGE;
                }
                case CHARGE -> {
                    ItemStack ordinary = minecraft.player.getInventory().getItem(1);
                    if (!ordinary.is(EIOItems.TRAVEL_STAFF.get()) || energy(ordinary) != 100000) return;
                    if (curios && energy(equipped(EIOItems.TRAVEL_STAFF.get())) <= 0) return;
                    check("wireless_charge_synchronized", curios || CuriosCompat.getActiveCurios(minecraft.player, null).isEmpty());
                    signal("charged");
                    stage = Stage.MAGNET;
                }
                case MAGNET -> {
                    ItemStack ordinary = minecraft.player.getInventory().getItem(2);
                    if (!ordinary.is(EIOItems.ELECTROMAGNET.get()) || energy(ordinary) <= 0) return;
                    if (curios && energy(equipped(EIOItems.ELECTROMAGNET.get())) != 50000) return;
                    KeyMapping.click(InputConstants.getKey(KeymapHandler.TOGGLE_MAGNET_KEY.get().saveString()));
                    stage = Stage.AIM;
                }
                case AIM -> {
                    ItemStack ordinary = minecraft.player.getInventory().getItem(2);
                    boolean toggled = isOn(curios ? equipped(EIOItems.ELECTROMAGNET.get()) : ordinary);
                    if (!toggled || minecraft.player.distanceToSqr(52.9, 80, 0.5) > 0.04) return;
                    check("magnet_key_mutation_synchronized", !curios || !isOn(ordinary));
                    check("travel_key_used_with_empty_hands", minecraft.player.getMainHandItem().isEmpty()
                        && minecraft.player.getOffhandItem().isEmpty());
                    KeymapHandler.TRAVEL_STAFF_KEY.get().setDown(true);
                    stage = Stage.RELEASE;
                }
                case RELEASE -> {
                    KeymapHandler.TRAVEL_STAFF_KEY.get().setDown(false);
                    stage = Stage.BLINK;
                }
                case BLINK -> {
                    if (minecraft.player.distanceToSqr(56.5, 80, 0.5) > 0.04) return;
                    ItemStack ordinary = minecraft.player.getInventory().getItem(1);
                    if (curios && energy(equipped(EIOItems.TRAVEL_STAFF.get())) != 5000) return;
                    if (!curios && energy(ordinary) != 8000) return;
                    check("travel_key_consumes_one_staff_charge", energy(ordinary) == (curios ? 9000 : 8000));
                    signal("blink");
                    finishAfterServer();
                }
                case RESTORED -> {
                    if (energy(equipped(EIOItems.TRAVEL_STAFF.get())) != 5000
                        || !isOn(equipped(EIOItems.ELECTROMAGNET.get()))) return;
                    check("equipped_staff_charge_and_magnet_toggle_restored", curios
                        && energy(minecraft.player.getInventory().getItem(1)) == 9000);
                    signal("restored");
                    finishAfterServer();
                }
                case SERVER -> {
                    if (Files.exists(output.resolve("server-verified"))) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private ItemStack equipped(net.minecraft.world.item.Item item) {
        return CuriosCompat.getActiveCurios(minecraft.player, stack -> stack.is(item))
            .filter(stacks -> !stacks.isEmpty()).map(stacks -> stacks.get(0)).orElse(ItemStack.EMPTY);
    }

    private int energy(ItemStack stack) {
        return stack.isEmpty() ? 0 : ItemStackEnergy.getEnergyStored(stack);
    }

    private boolean isOn(ItemStack stack) {
        return !stack.isEmpty() && Boolean.TRUE.equals(EIODataComponents.TOGGLED.get(stack));
    }

    private void signal(String name) throws Exception {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void finishAfterServer() throws Exception {
        capture.putAll(ClientSmoke.capture("curios-world.png"));
        check("quiet_hidden_world_capture", Boolean.TRUE.equals(capture.get("passed")));
        stage = Stage.SERVER;
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        KeymapHandler.TRAVEL_STAFF_KEY.get().setDown(false);
        if (failure != null) failure.printStackTrace();
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("capture", capture);
        result.put("curios", curios);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_curios", result);
    }
}

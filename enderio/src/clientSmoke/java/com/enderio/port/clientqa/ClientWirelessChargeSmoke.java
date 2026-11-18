package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.machines.gui.screen.WirelessChargerScreen;
import com.enderio.enderio.content.machines.wireless_charger.WirelessChargerMenu;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientWirelessChargeSmoke {
    private enum Stage { TITLE, CONNECTED, MENU, CHANGED, DONE }
    private static final BlockPos CHARGER = new BlockPos(20, 80, 10);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final long started = System.nanoTime();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> capture = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientWirelessChargeSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Server disconnected during " + stage);
            if (System.nanoTime() - started > 175_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local wireless charger test", address, true), false);
                    stage = Stage.CONNECTED;
                }
                case CONNECTED -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (minecraft.player.distanceToSqr(20.5, 80, 12.5) > 1
                        || !minecraft.level.getBlockState(CHARGER).is(EIOBlocks.WIRELESS_CHARGER.get())) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_tcp_connection", minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    var staff = minecraft.player.getInventory().getItem(0);
                    if (!staff.is(EIOItems.TRAVEL_STAFF.get()) || ItemStackEnergy.getEnergyStored(staff) <= 0) return;
                    check("wireless_staff_charge_arrives_in_inventory", staff.getTag() != null && staff.getTag().getInt("PortSolar") == 59);
                    minecraft.player.getInventory().selected = 1;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(20.5, 80.5, 11), Direction.SOUTH, CHARGER, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof WirelessChargerScreen)
                        || !(minecraft.player.containerMenu instanceof WirelessChargerMenu menu)
                        || menu.getMaxRange() != 48 || menu.getBlockEntity().getRange() != 48 || menu.getEnergyStorage() == null) return;
                    check("extended_native_charger_menu", menu.isCapacitorInstalled() && menu.getEnergyStorage().maxEnergyStored() == 128000
                        && menu.getEnergyStorage().energyStored() < 32000);
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WirelessChargerMenu.DECREASE_BUTTON_ID);
                    stage = Stage.CHANGED;
                }
                case CHANGED -> {
                    var menu = (WirelessChargerMenu) minecraft.player.containerMenu;
                    if (menu.getBlockEntity().getRange() != 47) return;
                    check("range_button_roundtrips_on_tcp", menu.getMaxRange() == 48);
                    capture.putAll(ClientSmoke.capture("wireless-charger-menu.png"));
                    check("quiet_hidden_menu_capture", Boolean.TRUE.equals(capture.get("passed")));
                    stage = Stage.DONE;
                    minecraft.player.closeContainer();
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("capture", capture);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_wireless_charge", result);
    }
}

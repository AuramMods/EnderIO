package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.StirlingGeneratorScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.stirling_generator.StirlingGeneratorMenu;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientStirlingSmoke {
    private enum Stage { TITLE, CONNECTED, MENU, NO_CAPACITOR, GENERATING, DISABLED, DONE }
    private static final BlockPos POS = new BlockPos(4, 80, 0);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private long fuelArrivedAt = -1;
    private int energyAtDisable;

    ClientStirlingSmoke() {
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
                        new ServerData("Ender IO local Stirling test", address, true), false);
                    stage = Stage.CONNECTED;
                }
                case CONNECTED -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (!minecraft.level.getBlockState(POS).is(EIOBlocks.STIRLING_GENERATOR.get())
                        || minecraft.player.distanceToSqr(4.5, 80, 2.5) > 1
                        || !minecraft.player.getInventory().getItem(9).is(Items.COAL)
                        || !minecraft.player.getInventory().getItem(10).is(EIOItems.BASIC_CAPACITOR.get())) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_tcp_connection", minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(4.5, 80.5, 1), Direction.SOUTH, POS, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof StirlingGeneratorScreen)
                        || !(minecraft.player.containerMenu instanceof StirlingGeneratorMenu menu)
                        || menu.getEnergyStorage() == null) return;
                    check("native_menu_with_capacitor_and_fuel_slots", menu.slots.size() == 38 && !menu.isCapacitorInstalled());
                    quickMove(menu, Items.COAL);
                    stage = Stage.NO_CAPACITOR;
                }
                case NO_CAPACITOR -> {
                    var menu = (StirlingGeneratorMenu) minecraft.player.containerMenu;
                    if (menu.getSlot(1).getItem().getCount() != 2) return;
                    if (fuelArrivedAt == -1) fuelArrivedAt = minecraft.level.getGameTime();
                    if (minecraft.level.getGameTime() - fuelArrivedAt < 10) return;
                    check("fuel_waits_without_capacitor", menu.getBurnProgress() == 0 && menu.getEnergyStorage().energyStored() == 0);
                    quickMove(menu, EIOItems.BASIC_CAPACITOR.get());
                    stage = Stage.GENERATING;
                }
                case GENERATING -> {
                    var menu = (StirlingGeneratorMenu) minecraft.player.containerMenu;
                    if (menu.getEnergyStorage().energyStored() < 6000 || menu.getBurnProgress() <= 0) return;
                    check("tcp_energy_burn_and_capacitor_sync", menu.isCapacitorInstalled() && menu.getEnergyStorage().maxEnergyStored() == 64000
                        && menu.getSlot(1).getItem().is(Items.COAL) && menu.getSlot(1).getItem().getCount() == 1
                        && minecraft.level.getBlockState(POS).getValue(ProgressMachineBlock.POWERED));
                    capture("stirling-generating");
                    var picker = minecraft.screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    energyAtDisable = menu.getEnergyStorage().energyStored();
                    check("redstone_widget_handles_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, 1));
                    stage = Stage.DISABLED;
                }
                case DISABLED -> {
                    var menu = (StirlingGeneratorMenu) minecraft.player.containerMenu;
                    if (menu.getRedstoneControl() != RedstoneControl.NEVER_ACTIVE
                        || minecraft.level.getBlockState(POS).getValue(ProgressMachineBlock.POWERED)
                        || menu.getEnergyStorage().energyStored() < energyAtDisable + 120) return;
                    check("lit_fuel_keeps_generating_when_new_fuel_disabled", menu.getBurnProgress() > 0 && menu.getSlot(1).getItem().getCount() == 1);
                    capture("stirling-redstone-disabled");
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

    private void quickMove(StirlingGeneratorMenu menu, Item item) {
        var slot = menu.slots.stream().filter(s -> s.container == minecraft.player.getInventory()
            && s.getItem().is(item)).findFirst().orElseThrow();
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check("silent_hidden_render_" + name, Boolean.TRUE.equals(result.get("passed")));
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
        ClientSmoke.complete("client_stirling", result);
    }
}

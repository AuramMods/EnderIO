package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.WiredChargerScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlock;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;

final class ClientSolarWiredSmoke {
    private enum Stage { TITLE, CONNECTED, PLACED, MENU, REMOVED, CHARGING, DONE }

    private static final BlockPos WIRED = new BlockPos(1190, 80, 8);
    private static final BlockPos ORIGINAL_PANEL = new BlockPos(1188, 80, 8);
    private static final BlockPos NEW_PANEL = ORIGINAL_PANEL.east();

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private long panelsConnected;
    private long menuOpened;

    ClientSolarWiredSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 175_000_000_000L) {
                String screen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
                throw new AssertionError("Timed out in " + stage + ", screen=" + screen);
            }
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local solar and wired charger test", address, true), false);
                    stage = Stage.CONNECTED;
                }
                case CONNECTED -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var vibrant = EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get();
                    if (!minecraft.player.getMainHandItem().is(vibrant.asItem())
                        || minecraft.player.distanceToSqr(1190.5, 80, 11.5) > 0.1
                        || !minecraft.level.getBlockState(ORIGINAL_PANEL).is(vibrant)) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_connection", minecraft.getSingleplayerServer() == null
                        && !connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress);
                    for (var tier : SolarPanelTier.values()) {
                        var stack = new ItemStack(EIOBlocks.SOLAR_PANELS.get(tier).get());
                        check("item_model_" + tier.name(), minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0)
                            != minecraft.getModelManager().getMissingModel());
                    }
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(NEW_PANEL), Direction.UP, NEW_PANEL.below(), false));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    var vibrant = EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get();
                    var original = minecraft.level.getBlockState(ORIGINAL_PANEL);
                    var placed = minecraft.level.getBlockState(NEW_PANEL);
                    if (!placed.is(vibrant) || !original.is(vibrant)
                        || placed.getValue(SolarPanelBlock.WEST) || original.getValue(SolarPanelBlock.EAST)) return;
                    if (panelsConnected == 0) panelsConnected = System.nanoTime();
                    if (System.nanoTime() - panelsConnected < 750_000_000L) return;
                    var models = minecraft.getModelManager();
                    check("old_and_new_panels_bake_connected_models",
                        models.getBlockModelShaper().getBlockModel(placed) != models.getMissingModel()
                        && models.getBlockModelShaper().getBlockModel(original) != models.getMissingModel());
                    capture("connected-panels");
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(1190.5, 80.5, 9), Direction.SOUTH, WIRED, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof WiredChargerScreen)
                        || !(minecraft.player.containerMenu instanceof WiredChargerMenu menu)) return;
                    if (menuOpened == 0) menuOpened = System.nanoTime();
                    var energy = menu.getEnergyStorage();
                    var input = menu.getSlot(1).getItem();
                    var output = menu.getSlot(2).getItem();
                    if (energy == null || energy.energyStored() != 12345 || energy.maxEnergyStored() != 128000
                        || !menu.isCapacitorInstalled() || !staff(input, 1, 40000) || !staff(output, 3, 100000)
                        || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL || menu.getChargeProgress() != 0f) {
                        if (System.nanoTime() - menuOpened < 3_000_000_000L) return;
                        throw new AssertionError("Migrated menu energy=" + energy + ", capacitor=" + menu.isCapacitorInstalled()
                            + ", input=" + input.save(new net.minecraft.nbt.CompoundTag())
                            + ", output=" + output.save(new net.minecraft.nbt.CompoundTag()) + ", redstone=" + menu.getRedstoneControl()
                            + ", progress=" + menu.getChargeProgress());
                    }
                    check("migrated_wired_menu_syncs_buffer_and_slots", menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()));
                    capture("wired-migrated");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.REMOVED;
                }
                case REMOVED -> {
                    var menu = (WiredChargerMenu) minecraft.player.containerMenu;
                    if (!menu.getSlot(2).getItem().isEmpty()) return;
                    var savedOutputs = minecraft.player.getInventory().items.stream().filter(stack -> staff(stack, 3, 100000)).count();
                    check("quick_move_collects_original_output_once", savedOutputs == 1L);
                    var picker = minecraft.screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    check("redstone_widget_accepts_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, 1));
                    stage = Stage.CHARGING;
                }
                case CHARGING -> {
                    var menu = (WiredChargerMenu) minecraft.player.containerMenu;
                    if (menu.getRedstoneControl() != RedstoneControl.ALWAYS_ACTIVE
                        || menu.getEnergyStorage().energyStored() != 0
                        || !staff(menu.getSlot(1).getItem(), 1, 52345)
                        || Math.abs(menu.getChargeProgress() - .52345f) > .0001f) return;
                    check("charging_progress_accounts_for_migrated_buffer", menu.getSlot(2).getItem().isEmpty());
                    capture("wired-partial-charge");
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

    private boolean staff(ItemStack stack, int label, int energy) {
        return stack.is(EIOItems.TRAVEL_STAFF.get()) && stack.getTag() != null
            && stack.getTag().getInt("PortWired") == label && ItemStackEnergy.getEnergyStored(stack) == energy;
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check("hidden_muted_" + name, Boolean.TRUE.equals(result.get("passed")));
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
        ClientSmoke.complete("client_solar_wired", result);
    }
}

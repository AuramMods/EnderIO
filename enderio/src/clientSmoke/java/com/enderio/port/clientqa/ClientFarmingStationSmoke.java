package com.enderio.port.clientqa;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.FarmingStationScreen;
import com.enderio.enderio.client.foundation.particle.RangeParticle;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

final class ClientFarmingStationSmoke {
    private enum Stage { TITLE, CONNECT, READY, MINING, PICKUP, OPEN, MENU, HARVEST, SETTINGS, REOPEN, RESYNC, RANGE, DONE }

    private static final BlockPos SOURCE = new BlockPos(2407, 80, 8);
    private static final BlockPos DESTINATION = new BlockPos(2407, 80, 13);
    private static final BlockPos INSIDE = DESTINATION.east(2);
    private static final BlockPos OUTSIDE = DESTINATION.north(3);
    private static final ActionRange HIDDEN = new ActionRange(2, false);
    private static final ActionRange VISIBLE = HIDDEN.visible();
    private static final int CHARGE = 6177;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int readyTick = -1;
    private int miningTick;
    private int cameraTick = -1;

    ClientFarmingStationSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 170_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Farming Station", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            var station = EIOBlocks.FARMING_STATION.get();
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.screen != null
                        || minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.READY;
                }
                case READY -> {
                    if (!marker("server-mine") || !positioned(11.5) || !minecraft.player.onGround()
                        || !minecraft.level.getBlockState(SOURCE).is(station)) {
                        readyTick = -1;
                        return;
                    }
                    minecraft.player.getInventory().selected = 0;
                    if (!minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)) return;
                    if (readyTick == -1) readyTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - readyTick < 6) return;
                    check("normal_survival_mining", minecraft.gameMode.startDestroyBlock(SOURCE, Direction.SOUTH));
                    miningTick = minecraft.player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(SOURCE)) {
                        if (miningTick != minecraft.player.tickCount) {
                            miningTick = minecraft.player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(SOURCE, Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    if (!marker("server-place") || !positioned(15.5)) return;
                    var inventory = minecraft.player.getInventory();
                    for (int slot = 0; slot < 9; slot++) {
                        var stack = inventory.getItem(slot);
                        if (!stack.is(station.asItem())) continue;
                        var contents = EnderBlockEntity.ITEM_INVENTORY.get(stack);
                        check("picked_up_charge_range_soul_and_inventory_reach_client", stack.getCount() == 1
                            && HIDDEN.equals(EIODataComponents.ACTION_RANGE.get(stack)) && validSoul(EIODataComponents.SOUL.get(stack))
                            && EIODataComponents.ENERGY.getOrDefault(stack, 0) == CHARGE
                            && contents != null && contents.getInt("Size") == 16);
                        inventory.selected = slot;
                        var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atBottomCenterOf(DESTINATION), Direction.UP, DESTINATION.below(), false));
                        check("normal_survival_placement", result.consumesAction());
                        signal("placed");
                        stage = Stage.OPEN;
                        break;
                    }
                }
                case OPEN -> {
                    if (!marker("server-placed") || !minecraft.player.getMainHandItem().isEmpty()
                        || !minecraft.level.getBlockState(DESTINATION).is(station) || !positioned(15.5)) return;
                    check("replanted_station_replaces_the_carried_item", minecraft.level.isEmptyBlock(SOURCE));
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(DESTINATION).add(0, 0, 0.5), Direction.SOUTH, DESTINATION, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof FarmingStationScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getEnergyStorage() == null || menu.getEnergyStorage().energyStored() != CHARGE
                        || !menu.getSlot(15).hasItem() || !menu.isCapacitorInstalled()) return;
                    var machine = menu.getBlockEntity();
                    if (!HIDDEN.equals(machine.getActionRange()) || !validSoul(machine.getBoundSoul())) return;
                    check("menu_syncs_redstone_io_and_range", menu.getRedstoneControl() == RedstoneControl.ACTIVE_WITH_SIGNAL
                        && machine.getIOConfig().getMode(Direction.EAST) == IOMode.DISABLED
                        && machine.getIOConfig().getMode(Direction.SOUTH) == IOMode.PULL && !menu.isRangeVisible());
                    check("menu_syncs_tools_seeds_fertilizer_and_outputs", menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get())
                        && tool(menu.getSlot(1).getItem(), Items.DIAMOND_AXE, 5) && tool(menu.getSlot(2).getItem(), Items.IRON_HOE, 3)
                        && tool(menu.getSlot(3).getItem(), Items.SHEARS, 2) && menu.getSlot(4).getItem().is(Items.WHEAT_SEEDS)
                        && menu.getSlot(5).getItem().is(Items.CARROT) && menu.getSlot(6).getItem().is(Items.POTATO)
                        && menu.getSlot(7).getItem().is(Items.BEETROOT_SEEDS) && menu.getSlot(8).getItem().getCount() == 6
                        && menu.getSlot(9).getItem().getCount() == 2 && menu.getSlot(10).getItem().is(Items.WHEAT)
                        && menu.getSlot(10).getItem().getCount() == 7 && menu.getSlot(15).getItem().is(Items.APPLE));
                    capture("replanted-farming-station");
                    signal("menu-initial");
                    stage = Stage.HARVEST;
                }
                case HARVEST -> {
                    if (!marker("server-harvested") || !(minecraft.screen instanceof FarmingStationScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!menu.getSlot(11).getItem().is(Items.PUMPKIN) || !minecraft.level.isEmptyBlock(INSIDE)) return;
                    check("client_sees_harvest_only_inside_the_carried_range", minecraft.level.getBlockState(OUTSIDE).is(Blocks.PUMPKIN)
                        && menu.getSlot(11).getItem().getCount() == 1 && menu.getEnergyStorage().energyStored() < CHARGE);
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 10, 0, ClickType.QUICK_MOVE, minecraft.player);
                    check("visibility_button_accepts_normal_gui_click",
                        screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 14, screen.getGuiTop() + 50, 0));
                    var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    check("redstone_picker_handles_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1)
                        && picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                    stage = Stage.SETTINGS;
                }
                case SETTINGS -> {
                    if (!(minecraft.screen instanceof FarmingStationScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!VISIBLE.equals(menu.getBlockEntity().getActionRange()) || menu.getSlot(10).hasItem()
                        || menu.getRedstoneControl() != RedstoneControl.NEVER_ACTIVE) return;
                    check("shift_click_extracts_exactly_seven_wheat", minecraft.player.getInventory().items.stream()
                        .filter(stack -> stack.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum() == 7);
                    check("visibility_update_reaches_client_block_entity", menu.isRangeVisible());
                    minecraft.player.closeContainer();
                    signal("settings");
                    stage = Stage.REOPEN;
                }
                case REOPEN -> {
                    if (!marker("server-controls-verified") || !positioned(15.5)) return;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(DESTINATION).add(0, 0, 0.5), Direction.SOUTH, DESTINATION, false));
                    stage = Stage.RESYNC;
                }
                case RESYNC -> {
                    if (!(minecraft.screen instanceof FarmingStationScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.getRedstoneControl() != RedstoneControl.NEVER_ACTIVE || !menu.getSlot(11).getItem().is(Items.PUMPKIN)) return;
                    check("fresh_menu_receives_updated_redstone_range_and_outputs", menu.isRangeVisible()
                        && menu.getSlot(10).getItem().isEmpty() && menu.getSlot(11).getItem().getCount() == 1);
                    minecraft.player.closeContainer();
                    signal("reopened-menu");
                    stage = Stage.RANGE;
                }
                case RANGE -> {
                    if (!marker("server-range-camera") || !positioned(18.5) || !minecraft.player.onGround()) {
                        cameraTick = -1;
                        return;
                    }
                    if (cameraTick == -1) cameraTick = minecraft.player.tickCount;
                    if (minecraft.player.tickCount - cameraTick < 8) return;
                    check("range_particle_at_carried_radius_reaches_native_renderer", visibleRangeParticle());
                    capture("farming-station-range");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static boolean tool(ItemStack stack, net.minecraft.world.item.Item item, int damage) {
        return stack.is(item) && stack.getDamageValue() == damage && stack.getTag() != null && stack.getTag().getInt("PortFixture") == 81;
    }

    private static boolean validSoul(Soul soul) {
        return soul != null && soul.hasEntity() && soul.entityType() == EntityType.BEE && soul.entityTag().getInt("PortFixture") == 81;
    }

    private boolean visibleRangeParticle() {
        Map<ParticleRenderType, Queue<Particle>> particles = ObfuscationReflectionHelper.getPrivateValue(
            ParticleEngine.class, minecraft.particleEngine, "f_107289_");
        var anchor = Vec3.atLowerCornerOf(DESTINATION.below());
        return particles != null && particles.values().stream().flatMap(Queue::stream).anyMatch(particle -> {
            if (!(particle instanceof RangeParticle)) return false;
            var bounds = particle.getBoundingBox();
            return bounds.getXsize() == 5 && bounds.getZsize() == 5 && bounds.getCenter().distanceToSqr(anchor) < 0.01;
        });
    }

    private boolean positioned(double z) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(DESTINATION.getX() + 0.5, 80, z) < 0.1;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_farming_station", result);
    }
}

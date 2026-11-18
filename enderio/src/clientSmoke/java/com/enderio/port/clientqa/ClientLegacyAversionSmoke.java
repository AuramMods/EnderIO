package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.filters.soul.EnderSoulFilterScreen;
import com.enderio.enderio.client.content.machines.gui.screen.ObeliskScreen;
import com.enderio.enderio.content.filters.soul.SoulFilterSlot;
import com.enderio.enderio.content.machines.obelisks.aversion.AversionObeliskMenu;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
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

final class ClientLegacyAversionSmoke {
    private enum Stage { TITLE, CONNECT, AVERSION, REPLANT, OPEN_FILTER, FILTER, EDIT, FINISH, DONE }

    private static final BlockPos PLACED = new BlockPos(1764, 80, 8);
    private static final BlockPos REPLANTED = new BlockPos(1768, 80, 8);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientLegacyAversionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected in " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Legacy Aversion migration", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (!minecraft.level.getBlockState(PLACED).is(EIOBlocks.AVERSION_OBELISK.get())) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(PLACED).add(0, 0, 0.5), Direction.SOUTH, PLACED, false));
                    stage = Stage.AVERSION;
                }
                case AVERSION -> {
                    if (!(minecraft.screen instanceof ObeliskScreen<?, ?> screen)
                        || !(screen.getMenu() instanceof AversionObeliskMenu menu)
                        || menu.getEnergyStorage() == null || !menu.isCapacitorInstalled()) return;
                    var filter = EIODataComponents.SOUL_FILTER.get(menu.getSlot(1).getItem());
                    if (filter == null) return;
                    check("placed_aversion_menu_shows_migrated_range_charge_and_filter", menu.getEnergyStorage().energyStored() == 5842
                        && menu.getBlockEntity().getRange() == 4 && menu.isRangeVisible()
                        && filter.shouldCompareTags() && filter.isDenyList() && filter.matches().get(4).entityType() == EntityType.COW);
                    capture("migrated-aversion");
                    minecraft.player.closeContainer();
                    signal("connected");
                    stage = Stage.REPLANT;
                }
                case REPLANT -> {
                    if (!marker("server-replant") || minecraft.screen != null
                        || !minecraft.player.getMainHandItem().is(EIOBlocks.AVERSION_OBELISK.asItem())
                        || minecraft.player.distanceToSqr(1768.5, 80, 11.5) > 0.1) return;
                    var range = EIODataComponents.ACTION_RANGE.get(minecraft.player.getMainHandItem());
                    check("carried_obelisk_has_explicit_visible_zero", range != null && range.range() == 0 && range.isVisible());
                    var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(REPLANTED), Direction.UP, REPLANTED.below(), false));
                    check("native_survival_placement_uses_carried_machine", result.consumesAction());
                    signal("replanted");
                    stage = Stage.OPEN_FILTER;
                }
                case OPEN_FILTER -> {
                    if (!marker("server-edit-filter") || !minecraft.player.getMainHandItem().is(EIOItems.BASIC_SOUL_FILTER.get())) return;
                    var connection = minecraft.getConnection();
                    connection.send(new ServerboundPlayerCommandPacket(minecraft.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                    minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                    connection.send(new ServerboundPlayerCommandPacket(minecraft.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                    stage = Stage.FILTER;
                }
                case FILTER -> {
                    if (!(minecraft.screen instanceof EnderSoulFilterScreen screen)) return;
                    var menu = screen.getMenu();
                    var first = (SoulFilterSlot) menu.getSlot(0);
                    var fifth = (SoulFilterSlot) menu.getSlot(4);
                    if (fifth.isEmpty() || !menu.isInverted() || !menu.shouldCompareTags()) return;
                    check("filter_editor_shows_forge6_first_and_fifth_slots", first.getResource().entityType() == EntityType.PIG
                        && first.getResource().entityTag().getString("CustomName").contains("Legacy Marker")
                        && ((SoulFilterSlot) menu.getSlot(1)).isEmpty() && fifth.getResource().entityType() == EntityType.COW);
                    capture("aversion-filter");
                    screen.mouseClicked(screen.getGuiLeft() + fifth.x + 8, screen.getGuiTop() + fifth.y + 8, 0);
                    check("tag_comparison_widget_responds", screen.mouseClicked(screen.getGuiLeft() + 166, screen.getGuiTop() + 53, 0));
                    stage = Stage.EDIT;
                }
                case EDIT -> {
                    if (!(minecraft.screen instanceof EnderSoulFilterScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!((SoulFilterSlot) menu.getSlot(4)).isEmpty() || menu.shouldCompareTags()) return;
                    check("editor_keeps_deny_list_and_first_slot", menu.isInverted()
                        && ((SoulFilterSlot) menu.getSlot(0)).getResource().entityType() == EntityType.PIG);
                    signal("filter-edited");
                    stage = Stage.FINISH;
                }
                case FINISH -> {
                    if (marker("server-verified")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_render", Boolean.TRUE.equals(frame.get("passed")));
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_legacy_aversion", result);
    }
}

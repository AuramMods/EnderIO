package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlockEntity;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.soul_binder.SoulBinderMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSoulBinderSmoke {
    private enum Stage { TITLE, CONNECT, MENU, CRAFT, TAKE, PLACE, VERIFY, DONE }

    private static final BlockPos BINDER = new BlockPos(1240, 80, 8);
    private static final BlockPos PANEL = new BlockPos(1242, 80, 10);
    private static final int PANEL_OUTPUT = 3;
    private static final int VIAL_OUTPUT = 4;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int productSlot;

    ClientSoulBinderSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 200_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Soul Binder test", address, true), false);
                    stage = Stage.CONNECT;
                }
                case CONNECT -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (minecraft.player.distanceToSqr(1240.5, 80, 10.5) > 0.1
                        || !minecraft.level.getBlockState(BINDER).is(EIOBlocks.SOUL_BINDER.get())) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(1240.5, 80.5, 9), Direction.SOUTH, BINDER, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen)
                        || !(minecraft.player.containerMenu instanceof SoulBinderMenu menu)
                        || menu.getEnergyStorage() == null || menu.getFluidTank() == null || menu.getCraftingProgress() == 0) return;
                    check("restored_job_syncs_energy_fluid_progress_and_slots", menu.slots.size() == 41 && menu.isCapacitorInstalled()
                        && menu.getEnergyStorage().energyStored() == 0 && fluidIs(menu, 6237)
                        && Math.abs(menu.getCraftingProgress() - 36781 / 288000f) < 0.00001f
                        && menu.getSlot(1).getItem().is(EIOItems.SOUL_VIAL.get()) && hasPhantom(menu.getSlot(1).getItem())
                        && isPanel(menu.getSlot(2).getItem()) && !menu.getSlot(PANEL_OUTPUT).hasItem());
                    check("menu_shows_recipe_requires_14_xp_levels", menu.getExperience() == 14);
                    capture("soul-binder-restarted");
                    signal("ready");
                    stage = Stage.CRAFT;
                }
                case CRAFT -> {
                    if (!Files.exists(output.resolve("server-complete"))) return;
                    var menu = (SoulBinderMenu) minecraft.player.containerMenu;
                    var stack = menu.getSlot(PANEL_OUTPUT).getItem();
                    if (stack.isEmpty() || !fluidIs(menu, 637)) return;
                    check("crafted_panel_and_empty_vial_sync_to_output_slots", isPanel(stack) && stack.getCount() == 1
                        && hasPhantom(stack) && menu.getSlot(VIAL_OUTPUT).getItem().is(EIOItems.SOUL_VIAL.get())
                        && EIODataComponents.SOUL.getOrDefault(menu.getSlot(VIAL_OUTPUT).getItem(), com.enderio.enderio.api.soul.Soul.EMPTY).isEmpty()
                        && menu.getEnergyStorage().energyStored() == 317);
                    capture("soul-binder-complete");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, PANEL_OUTPUT, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (((SoulBinderMenu) minecraft.player.containerMenu).getSlot(PANEL_OUTPUT).hasItem()) return;
                    var inventory = minecraft.player.getInventory();
                    productSlot = 0;
                    while (productSlot < 9 && !isPanel(inventory.getItem(productSlot))) productSlot++;
                    if (productSlot == 9) return;
                    check("shift_click_moves_the_bound_panel_to_the_player", hasPhantom(inventory.getItem(productSlot)));
                    minecraft.player.closeContainer();
                    signal("taken");
                    stage = Stage.PLACE;
                }
                case PLACE -> {
                    if (minecraft.screen != null || !Files.exists(output.resolve("server-place"))) return;
                    minecraft.player.getInventory().selected = productSlot;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(PANEL), Direction.UP, PANEL.below(), false));
                    signal("placed");
                    stage = Stage.VERIFY;
                }
                case VERIFY -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    if (!(minecraft.level.getBlockEntity(PANEL) instanceof SolarPanelBlockEntity panel)
                        || !minecraft.player.getMainHandItem().isEmpty()) return;
                    check("normal_placement_consumes_the_item_and_syncs_the_phantom", panel.getBoundSoul().entityType() == EntityType.PHANTOM);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    static boolean fluidIs(SoulBinderMenu menu, int amount) {
        var tank = menu.getFluidTank();
        return tank.capacity() == 10000 && tank.contents().getFluid() == EIOFluids.XP_JUICE.source().get()
            && tank.contents().getAmount() == amount;
    }

    private static boolean isPanel(ItemStack stack) {
        return stack.is(EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get().asItem());
    }

    private static boolean hasPhantom(ItemStack stack) {
        var soul = EIODataComponents.SOUL.get(stack);
        return soul != null && soul.entityType() == EntityType.PHANTOM;
    }

    private void signal(String name) throws Exception {
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_soul_binder", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.content.machines.soul_binder.SoulBinderMenu;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSoulBinderBackpressureSmoke {
    private enum Stage { TITLE, JOIN, OPEN, BLOCKED, SWAPPED, COMPLETE, VERIFIED, DONE }

    private static final BlockPos BINDER = new BlockPos(4922, 80, 8);
    private static final int HOTBAR_VIAL = 5;
    private static final int PRODUCT_SLOT = 3;
    private static final int EMPTY_VIAL_SLOT = 4;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;

    ClientSoulBinderBackpressureSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 240_000_000_000L) {
                throw new AssertionError("Soul Binder output test stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!ready("initial") || minecraft.screen != null
                        || player.distanceToSqr(Vec3.atBottomCenterOf(BINDER.south(2))) > 0.15) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(BINDER).relative(Direction.SOUTH, 0.5), Direction.SOUTH, BINDER, false);
                    check("opens_native_binder", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.BLOCKED;
                }
                case BLOCKED -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    SoulBinderMenu menu = screen.getMenu();
                    if (menu.getCraftingProgress() != 1 || menu.getEnergyStorage() == null
                        || !ClientSoulBinderSmoke.fluidIs(menu, 2277)
                        || !phantom(player.getInventory().getItem(HOTBAR_VIAL), "Nova", 16)) return;
                    check("paid_restored_job_keeps_inputs_behind_the_full_vial_slot",
                        menu.getEnergyStorage().energyStored() == 0 && menu.isCapacitorInstalled()
                            && phantom(menu.getSlot(SoulBinderMenu.INPUTS_INDEX).getItem(), "Flint", 18)
                            && panel(menu.getSlot(2).getItem()) && !menu.getSlot(PRODUCT_SLOT).hasItem()
                            && emptyVials(menu.getSlot(EMPTY_VIAL_SLOT).getItem(), 64));
                    capture("blocked");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, SoulBinderMenu.INPUTS_INDEX,
                        HOTBAR_VIAL, ClickType.SWAP, player);
                    stage = Stage.SWAPPED;
                }
                case SWAPPED -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!phantom(menu.getSlot(SoulBinderMenu.INPUTS_INDEX).getItem(), "Nova", 16)
                        || !phantom(player.getInventory().getItem(HOTBAR_VIAL), "Flint", 18)) return;
                    signal("swapped");
                    if (!ready("clear")) return;
                    check("same_recipe_swap_preserves_payment_and_keeps_output_blocked",
                        menu.getCraftingProgress() == 1 && menu.getEnergyStorage().energyStored() == 0
                            && ClientSoulBinderSmoke.fluidIs(menu, 2277) && !menu.getSlot(PRODUCT_SLOT).hasItem()
                            && emptyVials(menu.getSlot(EMPTY_VIAL_SLOT).getItem(), 64));
                    capture("swapped");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, EMPTY_VIAL_SLOT, 0, ClickType.QUICK_MOVE, player);
                    stage = Stage.COMPLETE;
                }
                case COMPLETE -> {
                    if (!(minecraft.screen instanceof SoulBinderScreen screen)) return;
                    var menu = screen.getMenu();
                    var result = menu.getSlot(PRODUCT_SLOT).getItem();
                    if (result.isEmpty() || !ClientSoulBinderSmoke.fluidIs(menu, 37)) return;
                    capture("completed");
                    check("paid_job_binds_the_phantom_now_in_the_input_slot",
                        panel(result) && result.getCount() == 1 && phantom(result, "Nova", 16)
                            && !SoulBoundUtils.getBoundSoul(result).entityTag().contains("NoAI"));
                    check("completion_costs_original_budget_and_returns_one_empty_vial",
                        menu.getEnergyStorage().energyStored() == 0 && emptyVials(menu.getSlot(EMPTY_VIAL_SLOT).getItem(), 1)
                            && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem()
                            && phantom(player.getInventory().getItem(HOTBAR_VIAL), "Flint", 18)
                            && player.getInventory().items.stream().anyMatch(stack -> emptyVials(stack, 64)));
                    signal("completed");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (ready("verified")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        var address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Soul Binder output test", address, true), false);
        stage = Stage.JOIN;
    }

    private static boolean panel(ItemStack stack) {
        return stack.is(EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.ENERGETIC).asItem());
    }

    private static boolean phantom(ItemStack stack, String name, float health) {
        var soul = SoulBoundUtils.getBoundSoul(stack);
        return soul.entityType() == EntityType.PHANTOM && soul.entityTag().getFloat("Health") == health
            && soul.entityTag().getString("CustomName").contains(name);
    }

    private static boolean emptyVials(ItemStack stack, int count) {
        return stack.is(EIOItems.SOUL_VIAL.get()) && stack.getCount() == count && SoulBoundUtils.getBoundSoul(stack).isEmpty();
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Path path = signals.resolve(name + ".json");
        if (Files.exists(path)) return;
        var temporary = signals.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, path);
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_soul_binder_backpressure", result);
    }
}

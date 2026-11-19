package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientLevitationStaffSmoke {
    private enum Stage { TITLE, JOIN, SAVED_ACTIVE, SAVED_SPENT, RESERVE, RESERVE_USE, RESERVE_SPENT,
        NATIVE, TANK, TANK_TAKEN, NATIVE_HAND, NATIVE_IDLE, NATIVE_USE, NATIVE_RISE, NATIVE_SPENT,
        REOPENED, VERIFIED, CAPTURE, DONE }

    private static final BlockPos TANK = new BlockPos(4043, 80, 8);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int idleStart;
    private double reservePeak;

    ClientLevitationStaffSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Levitation Staff client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.CAPTURE) {
                capture("staff");
                finish(null);
                return;
            }
            if (player.containerMenu instanceof FluidTankMenu menu) {
                takeFromTank(player, menu);
                return;
            }
            if (minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> join(player);
                case SAVED_ACTIVE -> {
                    var stack = player.getInventory().getItem(2);
                    if (!isStaff(stack) || !stack.hasFoil() || !player.hasEffect(MobEffects.LEVITATION)) return;
                    check("saved_active_staff_resumes_without_hand_selection", player.getInventory().selected != 2);
                    check("active_staff_retains_tagged_vapor_in_flight", fluidMatches(stack, "flight"));
                    stage = Stage.SAVED_SPENT;
                }
                case SAVED_SPENT -> {
                    var stack = player.getInventory().getItem(2);
                    if (!idleStaff(stack, 28, 0, "flight")) return;
                    check("legacy_active_staff_spends_expected_charge_and_all_fuel", player.getY() > 80.1);
                    signal("legacy-active");
                    stage = Stage.RESERVE;
                }
                case RESERVE -> {
                    var stack = player.getInventory().getItem(1);
                    if (!player.onGround() || !idleStaff(stack, 62, 40, "reserve")) return;
                    check("inactive_legacy_staff_syncs_its_tagged_reserve", fluidMatches(stack, "reserve"));
                    crouch(player, 1, Stage.RESERVE_USE);
                }
                case RESERVE_USE -> useWhenCrouched(player, Stage.RESERVE_SPENT);
                case RESERVE_SPENT -> {
                    reservePeak = Math.max(reservePeak, player.getY());
                    if (!idleStaff(player.getMainHandItem(), 2, 20, "reserve")) return;
                    check("crouch_use_lifts_and_leaves_subtick_energy_and_fuel", reservePeak > 80.1);
                    signal("legacy-reserve");
                    stage = Stage.NATIVE;
                }
                case NATIVE -> {
                    if (!player.onGround() || player.isCrouching() || !Files.exists(output.resolve("server-tank"))) return;
                    player.getInventory().selected = 0;
                    var hit = new BlockHitResult(new Vec3(4043, 80.5, 8.5), Direction.WEST, TANK, false);
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.TANK;
                }
                case NATIVE_HAND -> {
                    if (!player.onGround() || player.isCrouching() || !idleStaff(player.getInventory().getItem(0), 185, 60, "native")) return;
                    player.getInventory().selected = 0;
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    idleStart = player.tickCount;
                    stage = Stage.NATIVE_IDLE;
                }
                case NATIVE_IDLE -> {
                    if (player.tickCount - idleStart < 6) return;
                    check("standing_use_does_not_start_or_drain_a_fueled_staff", idleStaff(player.getMainHandItem(), 185, 60, "native")
                        && !player.hasEffect(MobEffects.LEVITATION));
                    crouch(player, 0, Stage.NATIVE_USE);
                }
                case NATIVE_USE -> useWhenCrouched(player, Stage.NATIVE_RISE);
                case NATIVE_RISE -> {
                    if (player.getY() < 82.5 || !player.hasEffect(MobEffects.LEVITATION)) return;
                    var stack = player.getMainHandItem();
                    check("active_staff_syncs_foil_energy_bar_and_tagged_fuel", stack.isBarVisible() && stack.hasFoil()
                        && fluidMatches(stack, "native"));
                    signal("native-rising");
                    capture("ascent");
                    stage = Stage.NATIVE_SPENT;
                }
                case NATIVE_SPENT -> {
                    if (!idleStaff(player.getMainHandItem(), 5, 0, "native") || !player.onGround()) return;
                    check("fuel_limited_staff_lands_after_effect_expires", !player.hasEffect(MobEffects.LEVITATION));
                    signal("native-empty");
                    stage = Stage.VERIFIED;
                }
                case REOPENED -> {
                    var inventory = player.getInventory();
                    if (!isStaff(inventory.getItem(0)) || !isStaff(inventory.getItem(1)) || !isStaff(inventory.getItem(2))) return;
                    check("all_energy_and_fluid_remainders_sync_after_restart", idleStaff(inventory.getItem(0), 5, 0, "native")
                        && idleStaff(inventory.getItem(1), 2, 20, "reserve") && idleStaff(inventory.getItem(2), 28, 0, "flight"));
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    minecraft.setScreen(new InventoryScreen(player));
                    stage = Stage.CAPTURE;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void takeFromTank(LocalPlayer player, FluidTankMenu menu) throws IOException {
        int input = 2;
        if (stage == Stage.TANK && idleStaff(menu.getSlot(input).getItem(), 185, 60, "native")) {
            check("partially_filled_staff_stays_in_native_refuelling_slot", !menu.getSlot(3).hasItem());
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, input, 0, ClickType.SWAP, player);
            stage = Stage.TANK_TAKEN;
        }
        if (stage == Stage.TANK_TAKEN && !menu.getSlot(input).hasItem() && idleStaff(player.getInventory().getItem(0), 185, 60, "native")) {
            player.closeContainer();
            signal("native-picked");
            stage = Stage.NATIVE_HAND;
        }
    }

    private void crouch(LocalPlayer player, int slot, Stage next) {
        player.getInventory().selected = slot;
        minecraft.options.keyShift.setDown(true);
        stage = next;
    }

    private void useWhenCrouched(LocalPlayer player, Stage next) {
        if (!player.isCrouching()) return;
        minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        minecraft.options.keyShift.setDown(false);
        stage = next;
    }

    private static boolean isStaff(ItemStack stack) {
        return stack.is(EIOItems.LEVITATION_STAFF.get());
    }

    private static boolean idleStaff(ItemStack stack, int energy, int amount, String batch) {
        if (!isStaff(stack) || stack.hasFoil() || ItemStackEnergy.getEnergyStored(stack) != energy) return false;
        var handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElseThrow();
        return handler.getFluidInTank(0).getAmount() == amount && (amount == 0 || fluidMatches(stack, batch));
    }

    private static boolean fluidMatches(ItemStack stack, String batch) {
        var fluid = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElseThrow().getFluidInTank(0);
        return fluid.getFluid() == EIOFluids.VAPOR_OF_LEVITY.source().get() && fluid.getTag() != null
            && fluid.getTag().getString("PortBatch").equals(batch);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Staff of Levity", address, true), false);
        stage = Stage.JOIN;
    }

    private void join(LocalPlayer player) throws IOException {
        if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
        check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
            && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
        signal("connected");
        stage = reopened ? Stage.REOPENED : Stage.SAVED_ACTIVE;
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture("levitation-" + name + ".png");
        check("silent_background_capture_" + name, Boolean.TRUE.equals(frame.get("passed")));
        captures.put(name, frame);
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_levitation_staff", result);
    }
}

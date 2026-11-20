package com.enderio.port.clientqa;

import com.enderio.enderio.client.EnderIOClient;
import com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientHangGliderSmoke {
    private enum Stage { TITLE, JOIN, SETUP, WING, WING_TAKEN, FRAME, FRAME_TAKEN, EQUIP, READY, FLIGHT, CONTROL_READY, CONTROL,
        CRUISE, DIVE, RECOVER, WATER, VERIFIED, DONE }

    private static final BlockPos TABLE = new BlockPos(3968, 80, 4);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final boolean maneuvers;
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private final LinkedHashMap<String, Double> descent = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private boolean recipeRequested;
    private boolean airborne;
    private boolean gliding;
    private boolean retriedJoin;
    private int sampleTicks;
    private int waterTicks;
    private double sampleY;
    private String pendingCapture;

    ClientHangGliderSmoke(boolean maneuvers) {
        this.maneuvers = maneuvers;
        MinecraftForge.EVENT_BUS.addListener(this::render);
        if (maneuvers) MinecraftForge.EVENT_BUS.addListener(this::tickManeuvers);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (maneuvers && stage == Stage.JOIN && minecraft.screen instanceof DisconnectedScreen && !retriedJoin) {
                retriedJoin = true;
                startConnection();
                return;
            }
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Hang Glider client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (pendingCapture != null) {
                var frame = ClientSmoke.capture(pendingCapture + ".png");
                check(pendingCapture + "_frame_is_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
                captures.put(pendingCapture, frame);
                pendingCapture = null;
            }
            if (minecraft.screen instanceof CraftingScreen && player.containerMenu instanceof CraftingMenu menu) {
                craft(player, menu);
                return;
            }
            if (minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = reopened || maneuvers ? Stage.READY : Stage.SETUP;
                }
                case SETUP -> {
                    if (!marker("craft") || player.distanceToSqr(3968.5, 80, 6.5) > 0.1
                        || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)
                        || count(player, EIOItems.DARK_STEEL_INGOT.get()) != 8) return;
                    var hit = new BlockHitResult(new Vec3(3968.5, 80.5, 5), Direction.SOUTH, TABLE, false);
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.WING;
                }
                case EQUIP -> {
                    for (int slot = 0; slot < 9; slot++) {
                        if (!player.getInventory().getItem(slot).is(EIOItems.GLIDER.get())) continue;
                        player.getInventory().selected = slot;
                        check("right_click_equips_crafted_glider", minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
                        stage = Stage.READY;
                        return;
                    }
                    throw new AssertionError("Crafted glider was not in the hotbar");
                }
                case READY -> {
                    if (!player.getItemBySlot(EquipmentSlot.CHEST).is(EIOItems.GLIDER.get())) return;
                    check("equipped_item_is_unstackable", player.getItemBySlot(EquipmentSlot.CHEST).getMaxStackSize() == 1);
                    check("held_item_was_consumed", count(player, EIOItems.GLIDER.get()) == 0);
                    signal("ready");
                    stage = maneuvers ? Stage.CRUISE : Stage.FLIGHT;
                }
                case FLIGHT -> flight(player);
                case CONTROL_READY -> {
                    if (!marker("control")) return;
                    minecraft.options.keyShift.setDown(true);
                    signal("sneak");
                    airborne = false;
                    stage = Stage.CONTROL;
                }
                case CONTROL -> control(player);
                case VERIFIED -> { if (marker("verified")) finish(null); }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void tickManeuvers(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || minecraft.player == null) return;
        var player = minecraft.player;
        try {
            switch (stage) {
                case CRUISE -> {
                    if (!PlayerMovementHandler.isGliding(player)) return;
                    if (sampleTicks == 0) {
                        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                        minecraft.options.hideGui = true;
                        minecraft.options.keyUp.setDown(true);
                    }
                    if (!sampleDescent(player, "cruise")) return;
                    check("ordinary_glide_has_the_stock_descent", descent.get("cruise") > 0.04 && descent.get("cruise") < 0.07);
                    minecraft.options.keySprint.setDown(true);
                    stage = Stage.DIVE;
                }
                case DIVE -> {
                    check("sprint_dive_stays_in_the_glider_state", PlayerMovementHandler.isGliding(player));
                    if (!player.isSprinting() || !sampleDescent(player, "sprint-dive")) return;
                    double ratio = descent.get("sprint-dive") / descent.get("cruise");
                    check("native_sprint_key_triples_glider_descent", ratio > 2.8 && ratio < 3.2);
                    minecraft.options.keySprint.setDown(false);
                    minecraft.options.keyUp.setDown(false);
                    stage = Stage.RECOVER;
                }
                case RECOVER -> {
                    check("ending_sprint_does_not_stow_the_glider_in_air", PlayerMovementHandler.isGliding(player));
                    if (player.isSprinting() || !sampleDescent(player, "recovered")) return;
                    check("ending_sprint_restores_normal_descent", Math.abs(descent.get("recovered") - descent.get("cruise")) < 0.006);
                    stage = Stage.WATER;
                }
                case WATER -> {
                    if (!player.isInWater()) {
                        waterTicks = 0;
                        return;
                    }
                    check("water_disables_gliding_with_the_original_item_equipped", !PlayerMovementHandler.isGliding(player)
                        && player.getItemBySlot(EquipmentSlot.CHEST).is(EIOItems.GLIDER.get()) && player.getHealth() == 20.0F);
                    if (++waterTicks < 6) return;
                    pendingCapture = "water";
                    signal("water");
                    stage = Stage.VERIFIED;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean sampleDescent(LocalPlayer player, String label) {
        if (sampleTicks++ == 0) sampleY = player.getY();
        if (sampleTicks < 6) return false;
        descent.put(label, (sampleY - player.getY()) / (sampleTicks - 1));
        sampleTicks = 0;
        pendingCapture = label;
        return true;
    }

    private void craft(LocalPlayer player, CraftingMenu menu) throws IOException {
        if (stage == Stage.WING_TAKEN && !menu.getSlot(0).hasItem() && count(player, EIOItems.GLIDER_WING.get()) == 2) {
            check("quick_move_crafts_two_wings", count(player, EIOItems.DARK_STEEL_INGOT.get()) == 2);
            recipeRequested = false;
            stage = Stage.FRAME;
        }
        if (stage == Stage.FRAME_TAKEN && count(player, EIOItems.GLIDER.get()) == 1) {
            check("crafting_consumes_both_wings_and_exact_materials", count(player, EIOItems.GLIDER_WING.get()) == 0
                && count(player, EIOItems.DARK_STEEL_INGOT.get()) == 0);
            player.closeContainer();
            stage = Stage.EQUIP;
            return;
        }
        if (stage != Stage.WING && stage != Stage.FRAME) return;
        boolean wing = stage == Stage.WING;
        var item = wing ? EIOItems.GLIDER_WING.get() : EIOItems.GLIDER.get();
        var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("enderio", wing ? "glider_wing" : "glider"))
            .orElseThrow();
        if (!recipeRequested) {
            if (!player.getRecipeBook().contains(recipe)) return;
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, wing);
            recipeRequested = true;
        }
        var result = menu.getSlot(0).getItem();
        // The recipe book fills slots one at a time, and a lone ingot can briefly show the nugget recipe.
        if (!result.is(item)) return;
        check(wing ? "native_wing_recipe" : "native_glider_recipe", result.getCount() == 1);
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
        stage = wing ? Stage.WING_TAKEN : Stage.FRAME_TAKEN;
    }

    private void flight(LocalPlayer player) throws IOException {
        if (player.getY() > 93 && !player.onGround()) airborne = true;
        if (!airborne) return;
        if (!gliding && PlayerMovementHandler.isGliding(player)) {
            gliding = true;
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.options.hideGui = true;
        } else if (gliding && captures.isEmpty() && player.getZ() > 9 && PlayerMovementHandler.isGliding(player)) {
            check("native_glider_model_baked", EnderIOClient.GLIDER_MODELS.containsKey(EIOItems.GLIDER.get()));
            var frame = ClientSmoke.capture("glider-in-flight.png");
            check("background_glider_render_is_silent", Boolean.TRUE.equals(frame.get("passed")));
            captures.put("flight", frame);
        }
        if (!player.onGround()) return;
        check("glider_deploys_and_moves_the_player", gliding && player.getZ() > 14.0);
        check("gliding_stops_when_landed", !PlayerMovementHandler.isGliding(player));
        signal("landed");
        stage = reopened ? Stage.VERIFIED : Stage.CONTROL_READY;
    }

    private void control(LocalPlayer player) throws IOException {
        if (player.getY() > 87 && !player.onGround()) airborne = true;
        if (!airborne) return;
        check("sneaking_prevents_deployment", !PlayerMovementHandler.isGliding(player));
        if (!player.onGround()) return;
        check("sneaking_lands_without_forward_glide", Math.abs(player.getZ() - 8.5) < 0.5);
        signal("control-landed");
        stage = Stage.VERIFIED;
    }

    private static int count(LocalPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        startConnection();
    }

    private void startConnection() {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Hang Glider", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
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
        if (maneuvers) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keySprint.setDown(false);
        }
        minecraft.options.hideGui = false;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (maneuvers) {
            result.put("descentBlocksPerTick", descent);
            result.put("retriedJoin", retriedJoin);
        }
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_hang_glider", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ClientIntegratedGliderSmoke {
    private enum Stage { TITLE, LOAD, FIXTURE, EQUIP, FLIGHT, RIDE, DISMOUNT, ARMOR, LANDED, SAVE, REOPEN, DONE }

    private enum Flight {
        FRESH("fresh"), BOAT("boat"), ARMOR_SWAP("armor-swap"), REOPENED("reopened");

        private final String id;

        Flight(String id) {
            this.id = id;
        }
    }

    private record Pilot(UUID uuid, Vec3 position, float health, boolean grounded, boolean gliding,
                         int vehicleId, ItemStack chest, ItemStack held) { }

    private static final double BOAT_Y = 90;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String worldName = System.getProperty("enderio.clientqa.worldName");
    private final long start = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> landings = new LinkedHashMap<>();
    private final Map<String, Object> ride = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private Flight flight = Flight.FRESH;
    private CompletableFuture<?> fixture;
    private CompletableFuture<Pilot> observation;
    private CompletableFuture<Integer> boatId;
    private int mountedAt = -1;
    private int dismountedAt = -1;
    private long titleReady;
    private boolean airborne;
    private boolean serverGliding;
    private float startingHealth;
    private Pilot saved;

    ClientIntegratedGliderSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - start > 220_000_000_000L) throw new AssertionError("Integrated glider stopped in " + stage + " / " + flight);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                createWorld();
                return;
            }
            if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null
                || minecraft.screen != null) return;
            switch (stage) {
                case LOAD -> createRunway();
                case FIXTURE -> {
                    if (!fixture.isDone() || minecraft.player.distanceToSqr(0.5, 80, 2.5) > 0.15
                        || !tagged(minecraft.player.getInventory().getItem(0), EIOItems.GLIDER.get())) return;
                    fixture.join();
                    useChestItem(0, "native_survival_use_equips_the_tagged_glider");
                    stage = Stage.EQUIP;
                }
                case EQUIP -> {
                    if (tagged(minecraft.player.getItemBySlot(EquipmentSlot.CHEST), EIOItems.GLIDER.get())) launch(Flight.FRESH);
                }
                case FLIGHT -> observeFlight();
                case RIDE -> observeRide();
                case DISMOUNT -> observeDismount();
                case ARMOR -> observeArmorSwap();
                case LANDED -> verifyLanding();
                case SAVE -> saveAndReopen();
                case REOPEN -> verifyReopened();
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void createWorld() {
        stage = Stage.LOAD;
        var settings = new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.NORMAL,
            true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(238L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private void createRunway() {
        check("packaged_single_player_uses_native_memory_connection", minecraft.getConnection().getConnection().isMemoryConnection()
            && !minecraft.player.isCreative() && !minecraft.player.isSpectator());
        var server = minecraft.getSingleplayerServer();
        fixture = server.submit(() -> {
            var level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
            for (int x = -4; x <= 4; ++x) {
                for (int z = -3; z <= 235; ++z) {
                    level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            var player = server.getPlayerList().getPlayers().get(0);
            var glider = new ItemStack(EIOItems.GLIDER.get());
            glider.getOrCreateTag().putInt("PortIntegratedGlider", 238);
            var armor = new ItemStack(Items.LEATHER_CHESTPLATE);
            armor.getOrCreateTag().putInt("PortIntegratedGlider", 238);
            player.getInventory().setItem(0, glider);
            player.getInventory().setItem(1, armor);
            player.connection.teleport(0.5, 80, 2.5, 0, 22);
        });
        stage = Stage.FIXTURE;
    }

    private void launch(Flight next) {
        flight = next;
        airborne = false;
        serverGliding = false;
        observation = null;
        startingHealth = minecraft.player.getHealth();
        var server = minecraft.getSingleplayerServer();
        if (next == Flight.BOAT) {
            boatId = server.submit(() -> {
                // Keep a vanilla boat at reachable height long enough to use it in midair.
                var boat = new Boat(server.overworld(), 2.25, BOAT_Y, 5.75);
                boat.setNoGravity(true);
                if (!server.overworld().addFreshEntity(boat)) throw new AssertionError("Boat fixture did not spawn");
                return boat.getId();
            });
        }
        server.execute(() -> server.getPlayerList().getPlayers().get(0).connection.teleport(0.5, 97, 4.5, 0, 22));
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
        stage = Stage.FLIGHT;
    }

    private void observeFlight() throws IOException {
        var player = minecraft.player;
        if (player.getY() > 93 && !player.onGround()) airborne = true;
        if (!airborne) return;
        if (PlayerMovementHandler.isGliding(player) && !serverGliding) {
            var state = pollPilot();
            if (state != null && state.gliding && tagged(state.chest, EIOItems.GLIDER.get())) {
                if (flight == Flight.BOAT) {
                    if (boardBoat()) {
                        serverGliding = true;
                        stage = Stage.RIDE;
                    }
                    return;
                }
                serverGliding = true;
                capture(flight.id + "-flight");
                if (flight == Flight.ARMOR_SWAP) {
                    useChestItem(1, "midair_native_use_equips_the_chestplate");
                    stage = Stage.ARMOR;
                    return;
                }
            }
        }
        if (player.onGround()) {
            check(flight.id + "_client_and_integrated_server_enter_glider_state", serverGliding);
            check(flight.id + "_client_lands_forward_without_damage", player.getZ() > 14 && player.getHealth() == startingHealth
                && !PlayerMovementHandler.isGliding(player));
            observation = null;
            stage = Stage.LANDED;
        }
    }

    private boolean boardBoat() throws IOException {
        if (!boatId.isDone() || !(minecraft.level.getEntity(boatId.join()) instanceof Boat boat)) return false;
        var player = minecraft.player;
        if (player.distanceToSqr(boat) > 9) return false;
        capture("boat-flight");
        check("native_survival_interaction_boards_the_boat_mid_glide",
            minecraft.gameMode.interact(player, boat, InteractionHand.MAIN_HAND).consumesAction());
        return true;
    }

    private void observeRide() throws IOException {
        var player = minecraft.player;
        if (!(player.getVehicle() instanceof Boat boat) || boat.getId() != boatId.join()) return;
        check("client_stops_gliding_while_airborne_in_the_boat", !PlayerMovementHandler.isGliding(player)
            && !player.onGround() && !player.isInWater() && !player.isShiftKeyDown()
            && tagged(player.getItemBySlot(EquipmentSlot.CHEST), EIOItems.GLIDER.get()));
        if (mountedAt < 0) mountedAt = player.tickCount;
        if (player.tickCount - mountedAt < 20) return;
        var state = pollPilot();
        if (state == null) return;
        check("server_keeps_the_original_glider_stowed_while_riding", state.vehicleId == boat.getId() && !state.gliding
            && !state.grounded && state.health == startingHealth && tagged(state.chest, EIOItems.GLIDER.get()));
        ride.put("mountedTicks", player.tickCount - mountedAt);
        capture("boat-mounted");
        minecraft.options.keyShift.setDown(true);
        stage = Stage.DISMOUNT;
    }

    private void observeDismount() throws IOException {
        var player = minecraft.player;
        if (player.isPassenger()) return;
        if (dismountedAt < 0) {
            minecraft.options.keyShift.setDown(false);
            // Walk clear if vanilla places the player on top of the boat when dismounting.
            minecraft.options.keyUp.setDown(true);
            check("native_dismount_leaves_the_glider_stowed_at_first", !PlayerMovementHandler.isGliding(player)
                && player.getY() > BOAT_Y - 1 && tagged(player.getItemBySlot(EquipmentSlot.CHEST), EIOItems.GLIDER.get()));
            dismountedAt = player.tickCount;
            ride.put("dismountY", player.getY());
        }
        if (player.getY() < BOAT_Y - 0.25) minecraft.options.keyUp.setDown(false);
        if (player.onGround() && player.getY() < BOAT_Y - 1) throw new AssertionError("Landed before re-deploying after dismount");
        if (!PlayerMovementHandler.isGliding(player)) return;
        var state = pollPilot();
        if (state == null || !state.gliding) return;
        check("client_and_server_redeploy_the_same_glider_after_dismount", state.vehicleId == -1
            && !state.grounded && !player.onGround() && state.health == startingHealth
            && player.tickCount - dismountedAt >= 10 && tagged(state.chest, EIOItems.GLIDER.get()));
        ride.put("redeployTicks", player.tickCount - dismountedAt);
        ride.put("redeployY", state.position.y);
        capture("boat-redeployed");
        stage = Stage.FLIGHT;
    }

    private void observeArmorSwap() throws IOException {
        var player = minecraft.player;
        if (!tagged(player.getItemBySlot(EquipmentSlot.CHEST), Items.LEATHER_CHESTPLATE)) return;
        if (!captures.containsKey("armor-swap-stowed")) {
            check("replacing_the_glider_stops_flight_before_landing", !player.onGround()
                && !PlayerMovementHandler.isGliding(player) && tagged(player.getMainHandItem(), EIOItems.GLIDER.get()));
            capture("armor-swap-stowed");
        }
        if (player.onGround()) stage = Stage.LANDED;
    }

    private void verifyLanding() throws IOException {
        var state = pollPilot();
        if (state == null || !state.grounded || state.gliding) return;
        if (flight == Flight.ARMOR_SWAP) {
            if (state.health == startingHealth || minecraft.player.getHealth() != state.health) return;
            check("ordinary_fall_damage_returns_with_chestplate_equipped", state.health > 0 && state.health < startingHealth
                && tagged(state.chest, Items.LEATHER_CHESTPLATE) && tagged(state.held, EIOItems.GLIDER.get()));
        } else {
            check(flight.id + "_native_server_landing_preserves_health_and_equipment", state.health == startingHealth
                && state.position.z > 14 && tagged(state.chest, EIOItems.GLIDER.get()));
        }
        landings.put(flight.id, Map.of("z", state.position.z, "health", state.health));
        if (flight == Flight.FRESH) launch(Flight.BOAT);
        else if (flight == Flight.BOAT) {
            var server = minecraft.getSingleplayerServer();
            int id = boatId.join();
            server.execute(() -> {
                if (server.overworld().getEntity(id) instanceof Boat boat) boat.discard();
            });
            launch(Flight.ARMOR_SWAP);
        } else if (flight == Flight.ARMOR_SWAP) {
            useChestItem(1, "native_use_re_equips_the_original_glider");
            stage = Stage.SAVE;
        } else {
            capture("reopened-landed");
            finish(null);
        }
    }

    private void saveAndReopen() throws IOException {
        var state = pollPilot();
        if (state == null || !tagged(state.chest, EIOItems.GLIDER.get())) return;
        check("the_server_has_the_same_glider_and_the_chestplate_returns_to_hand", tagged(state.held, Items.LEATHER_CHESTPLATE)
            && minecraft.player.getInventory().items.stream().noneMatch(s -> s.is(EIOItems.GLIDER.get())));
        saved = state;
        minecraft.level.disconnect();
        minecraft.clearLevel(new TitleScreen());
        check("world_writes_to_disk_and_integrated_server_stops", minecraft.getSingleplayerServer() == null
            && minecraft.level == null && Files.isRegularFile(minecraft.gameDirectory.toPath().resolve("saves").resolve(worldName).resolve("level.dat")));
        stage = Stage.REOPEN;
        minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), worldName);
    }

    private void verifyReopened() {
        var state = pollPilot();
        if (state == null || !state.grounded) return;
        check("the_saved_player_keeps_equipment_health_and_position", state.uuid.equals(saved.uuid)
            && state.position.distanceTo(saved.position) < 0.5 && state.health == saved.health
            && tagged(state.chest, EIOItems.GLIDER.get()) && tagged(state.held, Items.LEATHER_CHESTPLATE)
            && tagged(minecraft.player.getItemBySlot(EquipmentSlot.CHEST), EIOItems.GLIDER.get()));
        launch(Flight.REOPENED);
    }

    private Pilot pollPilot() {
        if (observation == null) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            observation = server.submit(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                return new Pilot(player.getUUID(), player.position(), player.getHealth(), player.onGround(),
                    PlayerMovementHandler.isGliding(player), player.isPassenger() ? player.getVehicle().getId() : -1,
                    player.getItemBySlot(EquipmentSlot.CHEST).copy(), player.getMainHandItem().copy());
            });
            return null;
        }
        if (!observation.isDone()) return null;
        var state = observation.join();
        observation = null;
        return state;
    }

    private void useChestItem(int hotbarSlot, String label) {
        var player = minecraft.player;
        player.getInventory().selected = hotbarSlot;
        check(label, minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
    }

    private static boolean tagged(ItemStack stack, Item item) {
        return stack.is(item) && stack.getCount() == 1 && stack.hasTag() && stack.getTag().getInt("PortIntegratedGlider") == 238;
    }

    private void capture(String id) throws IOException {
        var frame = ClientSmoke.capture(id + ".png");
        captures.put(id, frame);
        check(id + "_frame_is_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.hideGui = false;
        minecraft.options.keyShift.setDown(false);
        minecraft.options.keyUp.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("landings", landings);
        result.put("ride", ride);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_integrated_glider", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PoweredSpawnerScreen;
import com.enderio.enderio.content.machines.MachinesLang;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientMindKillerSmoke {
    private enum Stage { TITLE, JOIN, TABLE, RECIPE, RESULT, TAKE, MOUNT, MENU, BLOCKED, RESUME, DONE }

    private static final BlockPos SPAWNER = new BlockPos(4200, 80, 8);
    private static final BlockPos TABLE = new BlockPos(4199, 80, 9);
    private static final Vec3 STAND = new Vec3(4200.5, 80, 11.5);
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("enderio", "mind_killer");
    private static final String SPRITE = "enderio:block/soul_machine_top";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int renderedSince;

    ClientMindKillerSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 155_000_000_000L) {
                throw new AssertionError("Mind Killer stopped at " + stage);
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
                    check("native_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!ready("ingredients") || minecraft.screen != null || player.distanceToSqr(STAND) > 0.15
                        || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(RECIPE).orElseThrow();
                    if (!player.getRecipeBook().contains(recipe)) return;
                    check("the_original_zombie_electrode_unlocks_the_native_recipe", player.getInventory().countItem(EIOItems.ZOMBIE_ELECTRODE.get()) == 1);
                    check("native_crafting_table_opens", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(RECIPE).orElseThrow();
                    minecraft.gameMode.handlePlaceRecipe(screen.getMenu().containerId, recipe, false);
                    stage = Stage.RESULT;
                }
                case RESULT -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var menu = screen.getMenu();
                    var result = menu.getSlot(0).getItem();
                    var grid = menu.slots.subList(1, 10);
                    // The result packet can arrive before the last recipe-book grid update.
                    if (!mindKiller(result) || grid.stream().filter(Slot::hasItem).count() != 4) return;
                    check("native_recipe_assembles_exactly_one_mind_killer", result.getCount() == 1
                        && grid.stream().filter(slot -> slot.getItem().is(EIOItems.SOULARIUM_INGOT.get())).count() == 2);
                    var model = minecraft.getItemRenderer().getModel(result, minecraft.level, player, 0);
                    check("the_inventory_model_uses_the_shipped_particle_sprite", model.getParticleIcon().contents().name().toString().equals(SPRITE));
                    capture("mind-killer-recipe");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)
                        || screen.getMenu().slots.subList(0, 10).stream().anyMatch(Slot::hasItem)) return;
                    var inventory = player.getInventory();
                    for (int slot = 0; slot < 9; slot++) {
                        if (!mindKiller(inventory.getItem(slot))) continue;
                        player.closeContainer();
                        inventory.selected = slot;
                        var connection = minecraft.getConnection();
                        player.input.shiftKeyDown = true;
                        connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                        try {
                            check("native_sneak_place_mounts_the_crafted_block", use(SPAWNER, Direction.UP));
                        } finally {
                            player.input.shiftKeyDown = false;
                            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                        }
                        stage = Stage.MOUNT;
                        return;
                    }
                }
                case MOUNT -> {
                    var pos = SPAWNER.above();
                    var state = minecraft.level.getBlockState(pos);
                    if (!state.is(EIOBlocks.MIND_KILLER.get()) || minecraft.screen != null || !player.getMainHandItem().isEmpty()) return;
                    var model = minecraft.getBlockRenderer().getBlockModel(state);
                    check("the_placed_model_uses_the_stock_texture_and_six_pixel_height", model.getParticleIcon().contents().name().toString().equals(SPRITE)
                        && state.getShape(minecraft.level, pos).bounds().maxY == 6 / 16.0);
                    signal("mounted");
                    renderedSince = player.tickCount;
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!ready("inspect") || player.tickCount - renderedSince < 6 || minecraft.screen != null) return;
                    check("the_native_camera_is_aimed_at_the_placed_model", minecraft.hitResult instanceof BlockHitResult hit
                        && hit.getBlockPos().equals(SPAWNER.above()));
                    capture("placed-mind-killer");
                    check("native_powered_spawner_menu_opens", use(SPAWNER, Direction.SOUTH));
                    stage = Stage.BLOCKED;
                }
                case BLOCKED -> {
                    if (!(minecraft.screen instanceof PoweredSpawnerScreen screen) || !crowded(screen) || screen.getMenu().getSpawnProgress() != 1f) return;
                    capture("paid-spawner-at-limit");
                    signal("paid-and-blocked");
                    stage = Stage.RESUME;
                }
                case RESUME -> {
                    if (!ready("spawned") || !(minecraft.screen instanceof PoweredSpawnerScreen screen)) return;
                    if (screen.getMenu().getSpawnProgress() != 0f) return;
                    check("the_open_menu_clears_the_old_crowding_warning", !crowded(screen));
                    capture("spawner-after-live-limit-change");
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static boolean crowded(PoweredSpawnerScreen screen) {
        var text = MachinesLang.POWERED_SPAWNER_STATUS_OVERCROWDED_SPAWNERS.getString();
        return screen.getMenu().getMachineStates().stream().anyMatch(state -> state.component().getString().equals(text));
    }

    private static boolean mindKiller(ItemStack stack) {
        return stack.is(EIOBlocks.MIND_KILLER.get().asItem());
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Mind Killer", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_mind_killer", result);
    }
}

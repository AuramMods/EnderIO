package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.FluidTankScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Drives real client packets/widgets and the integrated server in an owned disposable world. */
final class ClientWorldSmoke {
    private enum Stage { TITLE, LOADING, FIXTURE, WORLD, MENU, FILL, REDSTONE, CHANGE, SYNC, FILLED_WORLD, SAVING, REOPEN, REOPEN_MENU, DONE }
    private final Minecraft minecraft = Minecraft.getInstance();
    private final BlockPos tankPos = new BlockPos(0, 80, 0);
    private final String worldName = System.getProperty("enderio.clientqa.worldName");
    private final boolean pressurized = Boolean.getBoolean("enderio.clientqa.pressurized");
    private final long started = System.nanoTime();
    private final Map<String, Object> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long stageStarted = started;
    private long titleReady;
    private CompletableFuture<?> pending;
    private boolean reopening;

    ClientWorldSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void next(Stage value) {
        stage = value;
        stageStarted = System.nanoTime();
        System.out.println("QA_EVT " + new Gson().toJson(Map.of("event", "world_stage", "status", "info", "stage", value)));
    }

    private void check(String name, boolean condition) {
        checks.put(name, condition);
        if (!condition) throw new AssertionError(name);
        System.out.println("QA_EVT " + new Gson().toJson(Map.of("event", name, "status", "pass")));
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check(name + "_silent_hidden_render", Boolean.TRUE.equals(result.get("passed")));
    }

    private FluidTankBlockEntity tank() {
        return (FluidTankBlockEntity) minecraft.getSingleplayerServer().overworld().getBlockEntity(tankPos);
    }

    private net.minecraft.world.level.block.Block fixtureTank() {
        return pressurized ? EIOBlocks.PRESSURIZED_FLUID_TANK.get() : EIOBlocks.FLUID_TANK.get();
    }

    private boolean inWorld() {
        return minecraft.level != null && minecraft.player != null && minecraft.screen == null
            && minecraft.getSingleplayerServer() != null;
    }

    private void openTank() {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, tankPos, false));
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE || stage == Stage.SAVING) return;
        try {
            if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("World scenario timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 4_000_000_000L) return;
                    capture("title");
                    next(Stage.LOADING);
                    var settings = new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    minecraft.createWorldOpenFlows().createFreshLevel(worldName, settings,
                        new WorldOptions(40L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case LOADING -> {
                    if (!inWorld()) return;
                    var server = minecraft.getSingleplayerServer();
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        level.setDayTime(6000);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 7; z++)
                            level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(tankPos, fixtureTank().defaultBlockState());
                        level.setBlockAndUpdate(tankPos.east(2), (pressurized ? EIOBlocks.FLUID_TANK.get()
                            : EIOBlocks.PRESSURIZED_FLUID_TANK.get()).defaultBlockState());
                        level.setBlockAndUpdate(tankPos.west(2), EIOBlocks.ENCHANTER.get().defaultBlockState());
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.getInventory().clearContent();
                        player.getInventory().setItem(9, new ItemStack(Items.WATER_BUCKET));
                        player.connection.teleport(0.5, 80, 4.5, 180, 15);
                    });
                    next(Stage.FIXTURE);
                }
                case FIXTURE -> {
                    if (!pending.isDone()) return;
                    pending.join();
                    check("integrated_world_and_fixture", true);
                    next(Stage.WORLD);
                }
                case WORLD -> {
                    if (!inWorld() || minecraft.player.distanceToSqr(0.5, 80, 4.5) > 1
                        || !minecraft.level.getBlockState(tankPos).is(fixtureTank())
                        || System.nanoTime() - stageStarted < 3_000_000_000L) return;
                    capture("world");
                    openTank();
                    next(Stage.MENU);
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof FluidTankScreen)
                        || !(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank() == null || menu.getFluidTank().capacity() != (pressurized ? 32000 : 16000)) return;
                    observations.put("tankCapacityMb", menu.getFluidTank().capacity());
                    check("native_block_use_opens_tank_menu", menu.getFluidTank().contents().isEmpty());
                    var slot = menu.slots.stream().filter(s -> s.container == minecraft.player.getInventory()
                        && s.getContainerSlot() == 9 && s.getItem().is(Items.WATER_BUCKET)).findFirst();
                    if (slot.isEmpty()) return;
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.get().index, 0,
                        ClickType.QUICK_MOVE, minecraft.player);
                    next(Stage.FILL);
                }
                case FILL -> {
                    if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank().contents().getAmount() != 1000 || !menu.getSlot(1).getItem().is(Items.BUCKET)) return;
                    check("inventory_packet_bucket_processing_and_fluid_sync", menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().getCount() == 1
                        && minecraft.player.getInventory().items.stream().noneMatch(item -> item.is(Items.WATER_BUCKET)));
                    observations.put("processedFluidMb", menu.getFluidTank().contents().getAmount());
                    observations.put("processedOutputBuckets", menu.getSlot(1).getItem().getCount());
                    capture("tank-filled");
                    var widget = minecraft.screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                        .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                    widget.mouseScrolled(widget.getX() + 8, widget.getY() + 8, -1);
                    pending = null;
                    next(Stage.REDSTONE);
                }
                case REDSTONE -> {
                    if (pending == null) {
                        pending = minecraft.getSingleplayerServer().submit(() -> tank().getRedstoneControl() == RedstoneControl.ACTIVE_WITH_SIGNAL);
                        return;
                    }
                    if (!pending.isDone()) return;
                    if (!Boolean.TRUE.equals(pending.join())) { pending = null; return; }
                    check("redstone_widget_packet_reaches_server", true);
                    pending = minecraft.getSingleplayerServer().submit(() -> {
                        if (tank().getFluidTank().getFluidAmount() != 1000) throw new AssertionError("Server fluid differs from client");
                        int accepted = tank().getFluidTank().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
                        if (accepted != 500) throw new AssertionError("Server tank rejected fixture update");
                    });
                    next(Stage.CHANGE);
                }
                case CHANGE -> {
                    if (!pending.isDone()) return;
                    pending.join();
                    next(Stage.SYNC);
                }
                case SYNC -> {
                    if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank().contents().getAmount() != 1500
                        || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                    check("server_update_reaches_open_client_menu", true);
                    observations.put("updatedFluidMb", menu.getFluidTank().contents().getAmount());
                    observations.put("updatedRedstoneMode", menu.getRedstoneControl().name());
                    capture("tank-updated");
                    minecraft.player.closeContainer();
                    next(Stage.FILLED_WORLD);
                }
                case FILLED_WORLD -> {
                    if (!inWorld() || System.nanoTime() - stageStarted < 1_000_000_000L
                        || !(minecraft.level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity clientTank)
                        || clientTank.getFluidTank().getFluidAmount() != 1500) return;
                    check("block_entity_fluid_reaches_world_renderer", true);
                    observations.put("renderedClientFluidMb", clientTank.getFluidTank().getFluidAmount());
                    capture("world-filled");
                    next(Stage.SAVING);
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("integrated_server_saved_and_stopped", minecraft.getSingleplayerServer() == null && minecraft.level == null);
                    next(Stage.REOPEN);
                    minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), worldName);
                }
                case REOPEN -> {
                    if (!inWorld()) return;
                    if (!reopening) {
                        reopening = true;
                        pending = minecraft.getSingleplayerServer().submit(() ->
                            tank().getFluidTank().getFluidAmount() == 1500
                                && tank().getRedstoneControl() == RedstoneControl.ACTIVE_WITH_SIGNAL
                                && FluidTankBlockEntity.FLUID_FILL_INPUT.getItemStack(tank()).isEmpty()
                                && FluidTankBlockEntity.FLUID_FILL_OUTPUT.getItemStack(tank()).is(Items.BUCKET)
                                && FluidTankBlockEntity.FLUID_FILL_OUTPUT.getItemStack(tank()).getCount() == 1
                                && minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0)
                                    .getInventory().items.stream().noneMatch(item -> item.is(Items.WATER_BUCKET)));
                    }
                    if (!pending.isDone()) return;
                    check("disk_reopen_preserves_tank_and_mode", Boolean.TRUE.equals(pending.join()));
                    openTank();
                    next(Stage.REOPEN_MENU);
                }
                case REOPEN_MENU -> {
                    if (!(minecraft.screen instanceof FluidTankScreen)
                        || !(minecraft.player.containerMenu instanceof FluidTankMenu menu)
                        || menu.getFluidTank() == null || menu.getFluidTank().contents().getAmount() != 1500
                        || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                    check("reopened_menu_resynchronizes_saved_state", menu.getSlot(1).getItem().is(Items.BUCKET)
                        && menu.getSlot(1).getItem().getCount() == 1 && menu.getSlot(0).getItem().isEmpty()
                        && minecraft.level.getBlockEntity(tankPos) instanceof FluidTankBlockEntity clientTank
                        && clientTank.getFluidTank().getFluidAmount() == 1500);
                    observations.put("reopenedFluidMb", menu.getFluidTank().contents().getAmount());
                    observations.put("reopenedOutputBuckets", menu.getSlot(1).getItem().getCount());
                    capture("tank-reopened");
                    next(Stage.DONE);
                    ClientSmoke.complete("client_singleplayer", new LinkedHashMap<>(Map.of(
                        "passed", true, "world", worldName, "variant", pressurized ? "pressurized" : "standard",
                        "checks", checks, "observations", observations, "captures", captures)));
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", false);
            result.put("stage", stage.name());
            result.put("error", failure.toString());
            result.put("checks", checks);
            result.put("observations", observations);
            result.put("captures", captures);
            try { result.put("failureCapture", ClientSmoke.capture("failure.png")); } catch (Exception ignored) { }
            next(Stage.DONE);
            ClientSmoke.complete("client_singleplayer", result);
        }
    }
}

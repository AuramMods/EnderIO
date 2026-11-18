package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PrimitiveAlloySmelterScreen;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientPrimitiveSmelterSmoke {
    private enum Stage { TITLE, WORLD, OPEN, MENU, WORKING, OUTPUT, COLLECT, DONE }
    private static final BlockPos POS = new BlockPos(0, 80, 0);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private CompletableFuture<?> pending;

    ClientPrimitiveSmelterSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 195_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    var block = EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get();
                    var models = minecraft.getModelManager();
                    for (var state : block.getStateDefinition().getPossibleStates()) {
                        check("baked_primitive_" + state.getValues(), models.getBlockModelShaper().getBlockModel(state) != models.getMissingModel());
                    }
                    check("baked_primitive_item", minecraft.getItemRenderer().getModel(new ItemStack(block), null, null, 0) != models.getMissingModel());
                    String name = System.getProperty("enderio.clientqa.worldName");
                    var options = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(name, options, new WorldOptions(57L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    var server = minecraft.getSingleplayerServer();
                    if (server == null || minecraft.player == null || minecraft.screen != null) return;
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        for (int z = 0; z < 4; z++) {
                            level.setBlockAndUpdate(new BlockPos(0, 79, z), Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(POS, EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get().defaultBlockState());
                        var player = server.getPlayerList().getPlayers().get(0);
                        player.getInventory().setItem(9, new ItemStack(Items.COAL, 2));
                        player.getInventory().setItem(10, new ItemStack(Items.COPPER_INGOT));
                        player.getInventory().setItem(11, new ItemStack(EIOItems.SILICON.get()));
                        player.connection.teleport(0.5, 80, 2.5, 180, 0);
                    });
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!pending.isDone() || !minecraft.level.getBlockState(POS).is(EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get())
                        || minecraft.player.distanceToSqr(Vec3.atCenterOf(POS)) > 10) return;
                    pending.join();
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, POS, false));
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof PrimitiveAlloySmelterScreen)
                        || !(minecraft.player.containerMenu instanceof PrimitiveAlloySmelterMenu menu)) return;
                    check("opened_native_five_slot_menu", menu.slots.size() == 41);
                    for (int i = 5; i < 8; i++) quickMove(menu, i);
                    stage = Stage.WORKING;
                }
                case WORKING -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (menu.getBurnProgress() < 0.1f || menu.getCraftingProgress() < 0.15f) return;
                    check("menu_syncs_flame_and_job_progress", menu.getSlot(0).getItem().is(Items.COAL)
                        && menu.getSlot(0).getItem().getCount() == 1);
                    capture("primitive-smelter-working");
                    stage = Stage.OUTPUT;
                }
                case OUTPUT -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (menu.getSlot(4).getItem().isEmpty()) return;
                    check("one_historical_alloy_output", menu.getSlot(4).getItem().is(EIOItems.CONDUCTIVE_ALLOY_INGOT.get())
                        && menu.getSlot(4).getItem().getCount() == 1 && menu.getSlot(1).getItem().isEmpty()
                        && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty());
                    capture("primitive-smelter-output");
                    quickMove(menu, 4);
                    stage = Stage.COLLECT;
                }
                case COLLECT -> {
                    var menu = (PrimitiveAlloySmelterMenu) minecraft.player.containerMenu;
                    if (!menu.getSlot(4).getItem().isEmpty()) return;
                    check("output_in_player_inventory", minecraft.player.getInventory().countItem(EIOItems.CONDUCTIVE_ALLOY_INGOT.get()) == 1);
                    stage = Stage.DONE;
                    minecraft.player.closeContainer();
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("integrated_world_stopped", minecraft.getSingleplayerServer() == null);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void quickMove(PrimitiveAlloySmelterMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private void capture(String name) throws Exception {
        var result = ClientSmoke.capture(name + ".png");
        captures.put(name, result);
        check("silent_hidden_capture_" + name, Boolean.TRUE.equals(result.get("passed")));
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
        ClientSmoke.complete("client_primitive_smelter", result);
    }
}

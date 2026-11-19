package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.PaintingMachineScreen;
import com.enderio.enderio.client.content.paint.PaintedSandRenderer;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.PaintedSandEntity;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientPaintedSandSmoke {
    private enum Stage { TITLE, JOIN, OPEN, TAKE, TRANSFER, FIRST, SECOND, AIRBORNE, DONE }

    private static final BlockPos MACHINE = new BlockPos(1804, 80, 4);
    private static final BlockPos FIRST = new BlockPos(1804, 88, 8);
    private static final BlockPos SECOND = new BlockPos(1810, 88, 8);
    private static final BlockPaintData DIAMOND = BlockPaintData.of(Blocks.DIAMOND_BLOCK);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int airborneTick = -1;
    private int heldSlot;

    ClientPaintedSandSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 140_000_000_000L) {
                throw new AssertionError("Painted sand scenario stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Painted sand QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!ready("server-menu") || !positioned(MACHINE, 2.5)) return;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(MACHINE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, MACHINE, false));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof PaintingMachineScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!paintedSand(menu.getSlot(3).getItem(), 2) || menu.getEnergyStorage() == null) return;
                    check("menu_syncs_two_paid_outputs_and_the_reusable_palette", menu.getEnergyStorage().energyStored() == 0
                        && menu.isCapacitorInstalled() && !menu.getSlot(1).hasItem()
                        && menu.getSlot(2).getItem().is(Items.DIAMOND_BLOCK) && menu.getSlot(2).getItem().getCount() == 1);
                    capture("painted-sand-machine");
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 3, 0, ClickType.QUICK_MOVE, minecraft.player);
                    stage = Stage.TRANSFER;
                }
                case TRANSFER -> {
                    if (!(minecraft.screen instanceof PaintingMachineScreen screen) || screen.getMenu().getSlot(3).hasItem()) return;
                    for (int slot = 0; slot < 9; slot++) {
                        if (!paintedSand(minecraft.player.getInventory().getItem(slot), 2)) continue;
                        heldSlot = slot;
                        check("quick_move_carries_both_typed_sand_items", true);
                        minecraft.player.closeContainer();
                        signal("taken");
                        stage = Stage.FIRST;
                        return;
                    }
                }
                case FIRST -> {
                    if (!ready("server-first") || !positioned(FIRST, 3.5)) return;
                    place(FIRST, "survival_places_the_first_sand_block", 2);
                    signal("placed-first");
                    stage = Stage.SECOND;
                }
                case SECOND -> {
                    if (!ready("server-second") || !positioned(SECOND, 3.5)) return;
                    place(SECOND, "survival_places_the_second_sand_block", 1);
                    signal("placed-second");
                    stage = Stage.AIRBORNE;
                }
                case AIRBORNE -> {
                    if (!ready("server-airborne") || !positioned(FIRST, 3.5)) return;
                    var bounds = new AABB(FIRST).expandTowards(0, -5, 0);
                    var entities = minecraft.level.getEntitiesOfClass(PaintedSandEntity.class, bounds);
                    if (entities.isEmpty()) return;
                    if (airborneTick == -1) airborneTick = minecraft.player.tickCount + 12;
                    if (minecraft.player.tickCount < airborneTick) return;
                    var sand = entities.get(0);
                    check("forge_spawn_packet_syncs_falling_block_state_and_diamond_paint", entities.size() == 1
                        && sand.getType() == EIOEntities.PAINTED_SAND.get() && sand.getPaint() == Blocks.DIAMOND_BLOCK
                        && sand.getBlockState().is(EIOBlocks.PAINTED_SAND.get()) && minecraft.level.isEmptyBlock(FIRST));
                    check("painted_sand_uses_its_registered_entity_renderer",
                        minecraft.getEntityRenderDispatcher().getRenderer(sand) instanceof PaintedSandRenderer);
                    check("both_placements_consume_the_original_inventory", minecraft.player.getMainHandItem().isEmpty());
                    var frame = capture("painted-sand-airborne");
                    int pixels = diamondPixels();
                    frame.put("diamondPixels", pixels);
                    check("airborne_frame_shows_the_diamond_surface", pixels > 300);
                    signal("rendered");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void place(BlockPos pos, String name, int count) {
        var inventory = minecraft.player.getInventory();
        inventory.selected = heldSlot;
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        check(name, paintedSand(inventory.getSelected(), count)
            && minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private static boolean paintedSand(ItemStack stack, int count) {
        return stack.is(EIOBlocks.PAINTED_SAND.get().asItem()) && stack.getCount() == count
            && DIAMOND.equals(EIODataComponents.BLOCK_PAINT.get(stack));
    }

    private int diamondPixels() {
        int count = 0;
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (int y = image.getHeight() * 18 / 100; y < image.getHeight() * 82 / 100; y++) {
                for (int x = image.getWidth() * 43 / 100; x < image.getWidth() * 57 / 100; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = pixel & 255;
                    int green = pixel >>> 8 & 255;
                    int blue = pixel >>> 16 & 255;
                    if (green - red > 45 && blue - red > 38 && Math.abs(green - blue) < 35) count++;
                }
            }
        }
        return count;
    }

    private boolean positioned(BlockPos pos, double offsetZ) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + offsetZ) < 0.09;
    }

    private Map<String, Object> capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
        return frame;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
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
        ClientSmoke.complete("client_painted_sand", result);
    }
}

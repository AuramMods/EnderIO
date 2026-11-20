package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.SlicerScreen;
import com.enderio.enderio.content.machines.slicer.SlicerMenu;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClientSlicerSmoke {
    private enum Stage {
        TITLE, JOIN, OPEN, SAVED, REMOVED, CHARGED, RETURNED, FINISHED,
        BLOCKED, HEAD_TAKEN, OUTPUT_CLEARED, UNBLOCKED, HEAD_RETURNED, RECOVERED, DONE
    }

    private static final BlockPos SLICER = new BlockPos(3163, 80, 8);
    private static final int AXE = 1;
    private static final int SHEARS = 2;
    private static final int HEAD = SlicerMenu.INPUTS_INDEX + 1;
    private static final int OUTPUT = SlicerMenu.LAST_INDEX;
    private static final float PAID = 3600f / 20000;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final boolean fullOutput;
    private final BlockPos slicer;
    private final long timeout;
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int useAt = -1;

    ClientSlicerSmoke(boolean fullOutput) {
        this.fullOutput = fullOutput;
        this.slicer = fullOutput ? new BlockPos(4954, 80, 8) : SLICER;
        this.timeout = fullOutput ? 240_000_000_000L : 140_000_000_000L;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > timeout) {
                throw new AssertionError("Slice'N'Splice stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Slice'N'Splice QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator() && minecraft.getSingleplayerServer() == null
                    && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
                return;
            }
            if (stage == Stage.OPEN) {
                if (!ready("slicer") || minecraft.screen != null
                    || player.distanceToSqr(Vec3.atBottomCenterOf(slicer.south(3))) > 0.15) return;
                if (useAt < 0) useAt = player.tickCount + 8;
                if (player.tickCount < useAt) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(slicer).relative(Direction.SOUTH, 0.5), Direction.SOUTH, slicer, false);
                check("native_slicer_interaction", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = fullOutput ? Stage.BLOCKED : Stage.SAVED;
                return;
            }
            if (minecraft.screen instanceof SlicerScreen screen) inspect(screen.getMenu());
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void inspect(SlicerMenu menu) throws IOException {
        var player = minecraft.player;
        switch (stage) {
            case SAVED -> {
                if (!paid(menu, 0)) return;
                check("restart_syncs_all_six_recipe_slots_and_both_tagged_tools", ingredientsMatch(menu)
                    && matches(menu.getSlot(AXE).getItem(), Items.IRON_AXE, 7)
                    && matches(menu.getSlot(SHEARS).getItem(), Items.SHEARS, 11) && player.getInventory().getItem(0).isEmpty());
                capture("saved-slicer-progress");
                swapShears(menu);
                stage = Stage.REMOVED;
            }
            case REMOVED -> {
                if (menu.getSlot(SHEARS).hasItem() || !matches(player.getInventory().getItem(0), Items.SHEARS, 11)) return;
                check("the_native_tool_slot_swap_retains_paid_progress", paid(menu, 0));
                signal("shears-removed");
                stage = Stage.CHARGED;
            }
            case CHARGED -> {
                if (!ready("checked") || !paid(menu, 16400)) return;
                check("an_open_menu_retains_progress_ingredients_and_energy_without_shears", ingredientsMatch(menu)
                    && !menu.getSlot(SHEARS).hasItem() && matches(menu.getSlot(AXE).getItem(), Items.IRON_AXE, 7));
                capture("slicer-waiting-for-shears");
                swapShears(menu);
                stage = Stage.RETURNED;
            }
            case RETURNED -> {
                if (!matches(menu.getSlot(SHEARS).getItem(), Items.SHEARS, 11)) return;
                check("returning_the_same_shears_clears_the_hotbar_slot", player.getInventory().getItem(0).isEmpty());
                signal("shears-returned");
                stage = Stage.FINISHED;
            }
            case FINISHED -> {
                if (!ready("finished") || menu.getCraftingProgress() != 0 || menu.getEnergyStorage().getEnergyStored() != 0) return;
                if (!matches(menu.getSlot(AXE).getItem(), Items.IRON_AXE, 8)
                    || !matches(menu.getSlot(SHEARS).getItem(), Items.SHEARS, 12)) return;
                check("the_finished_menu_syncs_wear_empty_inputs_and_the_original_capacitor",
                    menu.slots.subList(SlicerMenu.INPUTS_INDEX, SlicerMenu.LAST_INDEX + 1).stream().noneMatch(slot -> slot.hasItem())
                        && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()));
                capture("slicer-completed");
                player.closeContainer();
                finish(null);
            }
            case BLOCKED -> {
                if (menu.getCraftingProgress() != 1 || menu.getEnergyStorage() == null
                    || !controller(menu.getSlot(OUTPUT).getItem(), 64) || !taggedHead(menu.getSlot(HEAD).getItem())) return;
                check("restored_full_output_has_one_paid_job_and_unworn_tools",
                    ingredientsMatch(menu) && unworn(menu) && menu.getEnergyStorage().getEnergyStored() == 0
                        && player.getInventory().getItem(0).isEmpty());
                capture("blocked");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, HEAD, 0, ClickType.SWAP, player);
                stage = Stage.HEAD_TAKEN;
            }
            case HEAD_TAKEN -> {
                if (menu.getSlot(HEAD).hasItem() || !taggedHead(player.getInventory().getItem(0))) return;
                if (!ready("clear")) {
                    signalOnce("head-taken");
                    return;
                }
                check("head_swap_keeps_the_original_full_output_and_tools",
                    controller(menu.getSlot(OUTPUT).getItem(), 64) && unworn(menu));
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, OUTPUT, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.OUTPUT_CLEARED;
            }
            case OUTPUT_CLEARED -> {
                if (player.getInventory().items.stream().noneMatch(stack -> controller(stack, 64))) return;
                signal("output-cleared");
                stage = Stage.UNBLOCKED;
            }
            case UNBLOCKED -> {
                if (!ready("unblocked")) return;
                capture("without-head");
                check("free_output_cannot_deliver_with_the_head_missing",
                    !menu.getSlot(OUTPUT).hasItem() && !menu.getSlot(HEAD).hasItem()
                        && unworn(menu) && menu.getCraftingProgress() == 0 && menu.getEnergyStorage().getEnergyStored() == 0);
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, HEAD, 0, ClickType.SWAP, player);
                stage = Stage.HEAD_RETURNED;
            }
            case HEAD_RETURNED -> {
                if (!taggedHead(menu.getSlot(HEAD).getItem()) || !player.getInventory().getItem(0).isEmpty()) return;
                check("returning_the_head_restores_all_inputs_without_delivering_for_free",
                    ingredientsMatch(menu) && !menu.getSlot(OUTPUT).hasItem() && unworn(menu)
                        && menu.getEnergyStorage().getEnergyStored() == 0);
                signal("head-returned");
                stage = Stage.RECOVERED;
            }
            case RECOVERED -> {
                if (!ready("finished") || !controller(menu.getSlot(OUTPUT).getItem(), 1)
                    || !matches(menu.getSlot(AXE).getItem(), Items.IRON_AXE, 8)
                    || !matches(menu.getSlot(SHEARS).getItem(), Items.SHEARS, 12)) return;
                check("valid_recovery_crafts_one_item_and_spends_one_use_per_tool",
                    menu.getEnergyStorage().getEnergyStored() == 0 && menu.getCraftingProgress() == 0
                        && menu.slots.subList(SlicerMenu.INPUTS_INDEX, OUTPUT).stream().noneMatch(slot -> slot.hasItem())
                        && player.getInventory().items.stream().anyMatch(stack -> controller(stack, 64)));
                capture("recovered");
                player.closeContainer();
                finish(null);
            }
            default -> { }
        }
    }

    private void swapShears(SlicerMenu menu) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, SHEARS, 0, ClickType.SWAP, minecraft.player);
    }

    private static boolean paid(SlicerMenu menu, int energy) {
        return Math.abs(menu.getCraftingProgress() - PAID) < 0.0001 && menu.getEnergyStorage().getEnergyStored() == energy;
    }

    private static boolean matches(ItemStack stack, Item item, int damage) {
        return stack.is(item) && stack.getCount() == 1 && stack.hasTag() && stack.getTag().getInt("PortSlicer") == 159
            && stack.getDamageValue() == damage;
    }

    private static boolean unworn(SlicerMenu menu) {
        return matches(menu.getSlot(AXE).getItem(), Items.IRON_AXE, 7)
            && matches(menu.getSlot(SHEARS).getItem(), Items.SHEARS, 11);
    }

    private static boolean taggedHead(ItemStack stack) {
        return stack.is(Items.ZOMBIE_HEAD) && stack.getCount() == 1
            && stack.hasTag() && stack.getTag().getInt("PortSlicerIngredient") == 231;
    }

    private static boolean controller(ItemStack stack, int count) {
        return stack.is(EIOItems.Z_LOGIC_CONTROLLER.get()) && stack.getCount() == count;
    }

    private static boolean ingredientsMatch(SlicerMenu menu) {
        var soularium = EIOItems.SOULARIUM_INGOT.get();
        var silicon = EIOItems.SILICON.get();
        var ingredients = List.of(soularium, Items.ZOMBIE_HEAD, soularium, silicon, Items.REDSTONE, silicon);
        for (int i = 0; i < ingredients.size(); i++) {
            var stack = menu.getSlot(SlicerMenu.INPUTS_INDEX + i).getItem();
            if (!stack.is(ingredients.get(i)) || stack.getCount() != 1) return false;
        }
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, dir.resolve(name + ".json"));
    }

    private void signalOnce(String name) throws IOException {
        if (!Files.exists(output.resolve("signals").resolve(name + ".json"))) signal(name);
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_slicer", result);
    }
}

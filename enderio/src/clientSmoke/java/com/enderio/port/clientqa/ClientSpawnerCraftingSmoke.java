package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.machines.powered_spawner.PoweredSpawnerBlockEntity;
import com.enderio.enderio.foundation.soul.ShapedEntityStorageRecipe;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
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

final class ClientSpawnerCraftingSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, FILL, UNBOUND, UNBOUND_REMOVED, BOUND_INPUT, RESULT, TAKE, PLACE,
        ERASE, ERASE_RESULT, ERASE_TAKE, VERIFY, DONE
    }

    private static final BlockPos TABLE = new BlockPos(4140, 80, 8);
    private static final BlockPos PLACED = TABLE.east();
    private static final Vec3 STAND = new Vec3(4140.5, 80, 11.5);
    private static final ResourceLocation BIND = new ResourceLocation("enderio", "powered_spawner");
    private static final ResourceLocation ERASE = new ResourceLocation("enderio", "erase_powered_spawner");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int gridSlot;
    private int unboundAt = -1;
    private boolean secondCraft;

    ClientSpawnerCraftingSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 170_000_000_000L) {
                throw new AssertionError("Spawner crafting stopped at " + stage + ", grid slot " + gridSlot);
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
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!ready(secondCraft ? "pig" : "workshop") || minecraft.screen != null
                        || player.distanceToSqr(STAND) > 0.15 || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(BIND).orElseThrow();
                    if (!player.getRecipeBook().contains(recipe)) return;
                    check("stock_entity_storage_serializer_and_natural_recipe_unlock", recipe instanceof ShapedEntityStorageRecipe);
                    use(TABLE, Direction.SOUTH);
                    gridSlot = 0;
                    stage = Stage.FILL;
                }
                case PLACE -> {
                    if (minecraft.screen != null) return;
                    if (!(minecraft.level.getBlockEntity(PLACED) instanceof PoweredSpawnerBlockEntity spawner)
                        || spawner.getBoundSoul().entityType() != EntityType.COW) return;
                    check("native_placement_syncs_the_original_cow_soul", hotbarSpawner() == -1);
                    signal("placed");
                    secondCraft = true;
                    stage = Stage.TABLE;
                }
                case VERIFY -> {
                    if (ready("finish")) finish(null);
                }
                default -> {
                    if (minecraft.screen instanceof CraftingScreen screen) craft(screen.getMenu());
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(CraftingMenu menu) throws Exception {
        var player = minecraft.player;
        var inventory = player.getInventory();
        var source = menu.getSlot(2).getItem();
        var result = menu.getSlot(0).getItem();
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case FILL -> {
                if (gridSlot > 0 && !menu.getSlot(gridSlot).hasItem()) return;
                if (inventory.getItem(gridSlot).isEmpty()) return;
                click(menu, gridSlot + 1, gridSlot, ClickType.SWAP);
                if (++gridSlot == 9) stage = secondCraft ? Stage.RESULT : Stage.UNBOUND;
            }
            case UNBOUND -> {
                if (grid.stream().anyMatch(slot -> !slot.hasItem())) return;
                if (unboundAt < 0) unboundAt = player.tickCount + 8;
                if (player.tickCount < unboundAt) return;
                check("full_recipe_rejects_a_broken_spawner_without_a_soul", source.is(EIOItems.BROKEN_SPAWNER.get())
                    && soul(source) == Soul.EMPTY && result.isEmpty());
                click(menu, 2, 1, ClickType.SWAP);
                stage = Stage.UNBOUND_REMOVED;
            }
            case UNBOUND_REMOVED -> {
                if (!source.isEmpty() || !inventory.getItem(1).is(EIOItems.BROKEN_SPAWNER.get())) return;
                signal("rejected-unbound");
                stage = Stage.BOUND_INPUT;
            }
            case BOUND_INPUT -> {
                if (!ready("cow") || !source.isEmpty() || soul(inventory.getItem(1)).entityType() != EntityType.COW) return;
                click(menu, 2, 1, ClickType.SWAP);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (result.isEmpty()) return;
                var expected = secondCraft ? EntityType.PIG : EntityType.COW;
                check("native_result_inherits_" + (secondCraft ? "pig" : "cow"), source.is(EIOItems.BROKEN_SPAWNER.get())
                    && soul(source).entityType() == expected && spawner(result) && result.getCount() == 1
                    && soul(result).equals(soul(source)) && grid.stream().allMatch(slot -> slot.getItem().getCount() == 1));
                capture(secondCraft ? "pig-spawner" : "cow-spawner");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                int slot = hotbarSpawner();
                if (slot < 0 || grid.stream().anyMatch(Slot::hasItem)) return;
                check("normal_result_take_consumes_grid_for_" + (secondCraft ? "pig" : "cow"), result.isEmpty()
                    && soul(inventory.getItem(slot)).entityType() == (secondCraft ? EntityType.PIG : EntityType.COW));
                signal(secondCraft ? "took-pig" : "took-cow");
                if (secondCraft) {
                    stage = Stage.ERASE;
                } else {
                    player.closeContainer();
                    inventory.selected = slot;
                    use(PLACED.below(), Direction.UP);
                    stage = Stage.PLACE;
                }
            }
            case ERASE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(ERASE).orElseThrow();
                if (!ready("erase") || !player.getRecipeBook().contains(recipe)) return;
                check("stock_erase_recipe_naturally_unlocks", hotbarSpawner() >= 0 && grid.stream().noneMatch(Slot::hasItem));
                click(menu, 1, hotbarSpawner(), ClickType.SWAP);
                stage = Stage.ERASE_RESULT;
            }
            case ERASE_RESULT -> {
                var input = menu.getSlot(1).getItem();
                if (result.isEmpty() || !spawner(input)) return;
                check("stock_erase_result_clears_pig_soul", soul(input).entityType() == EntityType.PIG
                    && spawner(result) && soul(result) == Soul.EMPTY && result.getCount() == 1);
                capture("erase-spawner");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.ERASE_TAKE;
            }
            case ERASE_TAKE -> {
                int slot = hotbarSpawner();
                if (slot < 0 || grid.stream().anyMatch(Slot::hasItem)) return;
                check("take_leaves_one_unbound_spawner", result.isEmpty() && soul(inventory.getItem(slot)) == Soul.EMPTY);
                player.closeContainer();
                signal("erased");
                stage = Stage.VERIFY;
            }
            default -> {}
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Stock Spawner Crafting", address, true), false);
        stage = Stage.JOIN;
    }

    private void use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private void click(CraftingMenu menu, int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private int hotbarSpawner() {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (spawner(inventory.getItem(slot))) return slot;
        }
        return -1;
    }

    private static Soul soul(ItemStack stack) {
        return Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY);
    }

    private static boolean spawner(ItemStack stack) {
        return stack.is(EIOBlocks.POWERED_SPAWNER_ITEM.get());
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
        ClientSmoke.complete("client_spawner_crafting", result);
    }
}

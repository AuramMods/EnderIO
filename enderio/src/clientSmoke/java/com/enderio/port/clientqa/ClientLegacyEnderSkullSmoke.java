package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.misc_blocks.EnderSkullRenderer;
import com.enderio.enderio.foundation.block.entity.EnderSkullBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WallSkullBlock;
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
import java.util.function.Predicate;

final class ClientLegacyEnderSkullSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, COLLECTED, MINE, MINING, LOOT, PLACE, PLACED, VERIFY, DONE }

    private record Skull(String name, BlockPos source, Vec3 miningStand, Direction miningFace,
                         BlockPos destination, Vec3 placingStand, Direction placingFace) {}

    private static final BlockPos CHEST = new BlockPos(3362, 80, 12);
    private static final Vec3 CHEST_STAND = new Vec3(3362.5, 80, 14.5);
    private static final String MARKER = "PortLegacySkull";
    private static final List<Skull> SKULLS = List.of(
        new Skull("floor", new BlockPos(3362, 80, 8), new Vec3(3362.5, 80, 11.5), Direction.SOUTH,
            new BlockPos(3373, 80, 8), new Vec3(3373.5, 80, 11.5), Direction.UP),
        new Skull("wall", new BlockPos(3368, 80, 8), new Vec3(3370.5, 80, 8.5), Direction.EAST,
            new BlockPos(3378, 80, 8), new Vec3(3378.5, 80, 5.5), Direction.NORTH));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;
    private int miningTick;
    private int skullIndex;

    ClientLegacyEnderSkullSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 175_000_000_000L) {
                throw new AssertionError("Legacy skull stopped at " + stage + ", index " + skullIndex);
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
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!settled("chest", CHEST_STAND)) return;
                    check("vanilla_chest_opens_with_original_saved_stacks", use(CHEST, Direction.SOUTH));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (!marked(menu.getSlot(0).getItem(), 1) || !marked(menu.getSlot(1).getItem(), 2)
                        || !menu.getSlot(8).getItem().is(Items.IRON_PICKAXE)) return;
                    for (int index : new int[]{0, 1, 8}) {
                        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, index, 0, ClickType.QUICK_MOVE, player);
                    }
                    stage = Stage.COLLECTED;
                }
                case COLLECTED -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("the_two_original_forge6_loot_items_keep_their_distinct_tags", hasMarked(1) && hasMarked(2) && looseHeads() == 0);
                    player.closeContainer();
                    signal("collected");
                    stage = Stage.MINE;
                }
                case MINE, MINING, LOOT -> mine(SKULLS.get(skullIndex));
                case PLACE, PLACED -> replant(SKULLS.get(skullIndex));
                case VERIFY -> {
                    if (!ready("done")) return;
                    check("exactly_two_recovered_heads_remain_and_both_original_items_were_placed",
                        looseHeads() == 2 && !hasMarked(1) && !hasMarked(2));
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void mine(Skull skull) throws IOException {
        var player = minecraft.player;
        switch (stage) {
            case MINE -> {
                if (!settled("mine-" + skull.name(), skull.miningStand()) || !equip(stack -> stack.is(Items.IRON_PICKAXE))) return;
                check(skull.name() + "_original_variant_is_loaded", matches(skull.source(), skull.miningFace()));
                capture("original-" + skull.name(), skull.source());
                check(skull.name() + "_native_survival_mining", minecraft.gameMode.startDestroyBlock(skull.source(), skull.miningFace()));
                miningTick = player.tickCount;
                stage = Stage.MINING;
            }
            case MINING -> {
                if (!minecraft.level.getBlockState(skull.source()).isAir()) {
                    if (player.tickCount != miningTick) {
                        minecraft.gameMode.continueDestroyBlock(skull.source(), skull.miningFace());
                        miningTick = player.tickCount;
                    }
                    return;
                }
                signal("mined-" + skull.name());
                stage = Stage.LOOT;
            }
            case LOOT -> {
                if (looseHeads() != skullIndex + 1) return;
                check(skull.name() + "_mined_head_is_distinct_from_the_two_saved_loot_items", hasMarked(1) && hasMarked(2));
                signal("picked-up-" + skull.name());
                if (++skullIndex == SKULLS.size()) {
                    skullIndex = 0;
                    stage = Stage.PLACE;
                } else stage = Stage.MINE;
            }
            default -> {}
        }
    }

    private void replant(Skull skull) throws IOException {
        if (stage == Stage.PLACE) {
            if (!settled("place-" + skull.name(), skull.placingStand()) || !equip(stack -> marked(stack, skullIndex + 1))) return;
            var support = skull.destination().relative(skull.placingFace().getOpposite());
            check(skull.name() + "_uses_native_placement_of_the_original_6_2_drop", use(support, skull.placingFace()));
            stage = Stage.PLACED;
            return;
        }
        if (!matches(skull.destination(), skull.placingFace()) || hasMarked(skullIndex + 1)) return;
        check(skull.name() + "_placement_preserves_both_newly_mined_loose_heads", looseHeads() == 2);
        capture("replanted-" + skull.name(), skull.destination());
        signal("placed-" + skull.name());
        stage = ++skullIndex == SKULLS.size() ? Stage.VERIFY : Stage.PLACE;
    }

    private boolean matches(BlockPos pos, Direction face) {
        var state = minecraft.level.getBlockState(pos);
        // The standing model can turn locally while the player looks at it. The server checks the stored rotation.
        if (face.getAxis().isVertical() || pos.equals(SKULLS.get(0).source())) return state.is(EIOBlocks.ENDERMAN_HEAD.get());
        return state.is(EIOBlocks.WALL_ENDERMAN_HEAD.get()) && state.getValue(WallSkullBlock.FACING) == face;
    }

    private boolean equip(Predicate<ItemStack> wanted) {
        var player = minecraft.player;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!wanted.test(inventory.getItem(slot))) continue;
            inventory.selected = slot;
            return true;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (!wanted.test(inventory.getItem(slot))) continue;
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
            return false;
        }
        throw new AssertionError("Missing a saved fixture item at " + stage);
    }

    private boolean settled(String marker, Vec3 position) {
        var player = minecraft.player;
        if (!ready(marker) || minecraft.screen != null || player.distanceToSqr(position) > 0.15) {
            actionAt = -1;
            return false;
        }
        if (actionAt < 0) actionAt = player.tickCount + 8;
        if (player.tickCount < actionAt) return false;
        actionAt = -1;
        return true;
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private int looseHeads() {
        return minecraft.player.getInventory().items.stream().filter(stack -> stack.is(EIOBlocks.ENDERMAN_HEAD.asItem())
            && (!stack.hasTag() || !stack.getTag().contains(MARKER))).mapToInt(ItemStack::getCount).sum();
    }

    private boolean hasMarked(int marker) {
        return minecraft.player.getInventory().items.stream().anyMatch(stack -> marked(stack, marker));
    }

    private static boolean marked(ItemStack stack, int marker) {
        return stack.is(EIOBlocks.ENDERMAN_HEAD.asItem()) && stack.getCount() == 1 && stack.hasTag()
            && stack.getTag().getInt(MARKER) == marker;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Forge 6 Heads", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void capture(String name, BlockPos pos) throws IOException {
        check(name + "_has_the_mod_skull_renderer", minecraft.level.getBlockEntity(pos) instanceof EnderSkullBlockEntity skull
            && minecraft.getBlockEntityRenderDispatcher().getRenderer(skull) instanceof EnderSkullRenderer);
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
        ClientSmoke.complete("client_legacy_ender_skull", result);
    }
}

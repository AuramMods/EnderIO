package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSoulVialSmoke {
    private enum Stage { TITLE, JOIN, CAPTURE_VILLAGER, CHECK_CAPTURE, RELEASE_VILLAGER, CHECK_RELEASE, VERIFIED, DONE }

    private static final AABB CAPTURE_PEN = new AABB(1206, 80, 9, 1211, 83, 12);
    private static final BlockPos COW_FLOOR = new BlockPos(1206, 79, 13);
    private static final BlockPos VILLAGER_FLOOR = new BlockPos(1210, 79, 13);
    private static final String COW_NAME = "Capture cow";
    private static final String VILLAGER_NAME = "Capture librarian";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean capture = "exercise".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientSoulVialSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 170_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local Soul Vial test", address, true), false);
                    stage = Stage.JOIN;
                }
                case JOIN -> {
                    if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null
                        || minecraft.player.distanceToSqr(1208.5, 80, 12.5) > 0.1) return;
                    if (capture) {
                        var cow = mob(EntityType.COW, COW_NAME, CAPTURE_PEN);
                        var hand = minecraft.player.getMainHandItem();
                        if (cow == null || !isVial(hand) || hand.getCount() != 2) return;
                        checkConnection();
                        captureMob(cow);
                        stage = Stage.CAPTURE_VILLAGER;
                    } else {
                        int cowSlot = filledSlot(EntityType.COW);
                        if (cowSlot < 0 || filledSlot(EntityType.VILLAGER) < 0) return;
                        checkConnection();
                        check("reconnect_syncs_both_occupied_vials", soulStateMatches());
                        releaseAt(cowSlot, COW_FLOOR);
                        stage = Stage.RELEASE_VILLAGER;
                    }
                }
                case CAPTURE_VILLAGER -> {
                    if (filledSlot(EntityType.COW) < 0) return;
                    var hand = minecraft.player.getMainHandItem();
                    check("capturing_from_stack_leaves_one_empty_vial_in_hand", isVial(hand) && hand.getCount() == 1
                        && Soul.ITEM_DATA.getOrDefault(hand, Soul.EMPTY).isEmpty());
                    var villager = mob(EntityType.VILLAGER, VILLAGER_NAME, CAPTURE_PEN);
                    if (villager == null) return;
                    captureMob(villager);
                    stage = Stage.CHECK_CAPTURE;
                }
                case CHECK_CAPTURE -> {
                    if (minecraft.screen != null) throw new AssertionError("Villager capture opened the trading screen");
                    if (filledSlot(EntityType.VILLAGER) < 0 || mob(EntityType.VILLAGER, VILLAGER_NAME, CAPTURE_PEN) != null
                        || mob(EntityType.COW, COW_NAME, CAPTURE_PEN) != null) return;
                    check("last_vial_replaces_the_hand_and_skips_trading", minecraft.player.getInventory().selected == filledSlot(EntityType.VILLAGER));
                    check("stored_vials_sync_both_entities_and_their_health", soulStateMatches());
                    var held = minecraft.player.getMainHandItem();
                    var empty = new ItemStack(EIOItems.SOUL_VIAL.get());
                    var renderer = minecraft.getItemRenderer();
                    check("filled_vial_uses_its_model_override_and_glint", held.hasFoil() && !empty.hasFoil()
                        && renderer.getModel(held, minecraft.level, minecraft.player, 0)
                            != renderer.getModel(empty, minecraft.level, minecraft.player, 0));
                    signal();
                    stage = Stage.VERIFIED;
                }
                case RELEASE_VILLAGER -> {
                    // The spawn packet can arrive before the inventory updates from the same use.
                    if (mob(EntityType.COW, COW_NAME, new AABB(COW_FLOOR.above()).inflate(0.2)) == null
                        || filledSlot(EntityType.COW) >= 0 || emptyVialCount() == 0) return;
                    int villagerSlot = filledSlot(EntityType.VILLAGER);
                    check("player_release_replaces_only_the_cow_vial", villagerSlot >= 0 && emptyVialCount() == 1);
                    releaseAt(villagerSlot, VILLAGER_FLOOR);
                    stage = Stage.CHECK_RELEASE;
                }
                case CHECK_RELEASE -> {
                    if (mob(EntityType.VILLAGER, VILLAGER_NAME, new AABB(VILLAGER_FLOOR.above()).inflate(0.2)) == null
                        || emptyVialCount() != 2) return;
                    check("both_releases_return_empty_vials_and_sync_named_mobs", filledSlot(EntityType.COW) < 0
                        && filledSlot(EntityType.VILLAGER) < 0);
                    minecraft.player.setYRot(0);
                    minecraft.player.setXRot(12);
                    signal();
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    var frame = ClientSmoke.capture(capture ? "soul-vials-filled.png" : "soul-vials-released.png");
                    check("silent_hidden_render", Boolean.TRUE.equals(frame.get("passed")));
                    finish(null, frame);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure, Map.of());
        }
    }

    private void checkConnection() {
        var connection = minecraft.getConnection().getConnection();
        check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
            && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
            && connection.getRemoteAddress() instanceof InetSocketAddress);
    }

    private void captureMob(LivingEntity entity) {
        var hit = new EntityHitResult(entity, entity.position().add(0, 0.5, 0));
        var hand = InteractionHand.MAIN_HAND;
        if (!minecraft.gameMode.interactAt(minecraft.player, entity, hit, hand).consumesAction()) {
            minecraft.gameMode.interact(minecraft.player, entity, hand);
        }
    }

    private void releaseAt(int slot, BlockPos floor) {
        minecraft.player.getInventory().selected = slot;
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atBottomCenterOf(floor.above()), Direction.UP, floor, false));
    }

    private @Nullable LivingEntity mob(EntityType<?> type, String name, AABB bounds) {
        return minecraft.level.getEntitiesOfClass(LivingEntity.class, bounds,
            entity -> entity.getType() == type && entity.getName().getString().equals(name)).stream().findFirst().orElse(null);
    }

    private static boolean isVial(ItemStack stack) {
        return stack.is(EIOItems.SOUL_VIAL.get());
    }

    private int filledSlot(EntityType<?> type) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = minecraft.player.getInventory().getItem(slot);
            if (isVial(stack) && Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).entityType() == type) return slot;
        }
        return -1;
    }

    private int emptyVialCount() {
        return minecraft.player.getInventory().items.stream()
            .filter(stack -> isVial(stack) && Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).isEmpty())
            .mapToInt(ItemStack::getCount).sum();
    }

    private boolean soulStateMatches() {
        var inventory = minecraft.player.getInventory();
        var cow = inventory.getItem(filledSlot(EntityType.COW));
        var villager = inventory.getItem(filledSlot(EntityType.VILLAGER));
        return cow.getCount() == 1 && villager.getCount() == 1 && emptyVialCount() == 0
            && Soul.ITEM_DATA.get(cow).entityTag().getFloat("Health") == 7
            && Soul.ITEM_DATA.get(villager).entityTag().getFloat("Health") == 11
            && SoulVialItem.ENTITY_MAX_HEALTH.getOrDefault(cow, 0f) == 10
            && SoulVialItem.ENTITY_MAX_HEALTH.getOrDefault(villager, 0f) == 20;
    }

    private void signal() throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("interacted.json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure, Map<String, Object> frame) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("capture", frame);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_soul_vials", result);
    }
}

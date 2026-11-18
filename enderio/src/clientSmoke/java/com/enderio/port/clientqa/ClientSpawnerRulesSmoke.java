package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
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
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientSpawnerRulesSmoke {
    private enum Stage { TITLE, JOIN, READY, USED, TARGETS, CAPTURED, VERIFIED, DONE }

    private static final BlockPos SPAWNER = new BlockPos(1412, 80, 8);
    private static final AABB PEN = new AABB(1409, 80, 9, 1416, 83, 13);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean enabled = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;

    ClientSpawnerRulesSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 165_000_000_000L) {
                var player = minecraft.player;
                throw new AssertionError("Timed out in " + stage + (player == null ? "" : " at " + player.position()
                    + " holding " + player.getMainHandItem().save(new net.minecraft.nbt.CompoundTag())
                    + ", spawner display " + displayType()));
            }
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    if (titleReady == 0) titleReady = System.nanoTime();
                    if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                    var address = System.getProperty("enderio.clientqa.server");
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                        new ServerData("Ender IO local spawner rules test", address, true), false);
                    stage = Stage.JOIN;
                }
                case JOIN -> {
                    if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = Stage.READY;
                }
                case READY -> {
                    minecraft.player.getInventory().selected = 0;
                    var stack = minecraft.player.getMainHandItem();
                    if (minecraft.player.distanceToSqr(1412.5, 80, 11.5) > 0.1 || !stack.is(EIOItems.SOUL_VIAL.get())
                        || Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).entityType() != EntityType.COW
                        || displayType() != EntityType.PIG) return;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(1412.5, 80.5, 9), Direction.SOUTH, SPAWNER, false));
                    signal("used-vial");
                    stage = Stage.USED;
                }
                case USED -> {
                    if (emptyVials() != 1) return;
                    if (enabled) {
                        stage = Stage.VERIFIED;
                    } else {
                        check("default_use_preserves_the_pig_display", displayType() == EntityType.PIG);
                        stage = Stage.TARGETS;
                    }
                    signal("changed");
                }
                case TARGETS -> {
                    if (!Files.exists(output.resolve("server-targets")) || emptyVials() != 2) return;
                    var sheep = find(EntityType.SHEEP);
                    var pig = find(EntityType.PIG);
                    if (sheep == null || pig == null) return;
                    captureMob(sheep);
                    captureMob(pig);
                    stage = Stage.CAPTURED;
                }
                case CAPTURED -> {
                    if (find(EntityType.PIG) != null || emptyVials() != 1) return;
                    check("native_capture_rejects_sheep_and_syncs_pig_vial", find(EntityType.SHEEP) != null
                        && minecraft.player.getInventory().items.stream().filter(stack -> stack.is(EIOItems.SOUL_VIAL.get()))
                        .anyMatch(stack -> Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).entityType() == EntityType.PIG));
                    signal("captured");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!Files.exists(output.resolve("server-verified"))) return;
                    check("native_display_agrees_with_server_setting", displayType() == (enabled ? EntityType.COW : EntityType.PIG));
                    var frame = ClientSmoke.capture(enabled ? "cow-spawner.png" : "default-pig-spawner.png");
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

    private EntityType<?> displayType() {
        if (minecraft.level.getBlockEntity(SPAWNER) instanceof SpawnerBlockEntity spawner) {
            var display = spawner.getSpawner().getOrCreateDisplayEntity(minecraft.level, minecraft.level.getRandom(), SPAWNER);
            if (display != null) return display.getType();
        }
        return null;
    }

    private int emptyVials() {
        return minecraft.player.getInventory().items.stream()
            .filter(stack -> stack.is(EIOItems.SOUL_VIAL.get()) && Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).isEmpty())
            .mapToInt(ItemStack::getCount).sum();
    }

    private LivingEntity find(EntityType<?> type) {
        return minecraft.level.getEntitiesOfClass(LivingEntity.class, PEN, entity -> entity.getType() == type)
            .stream().findFirst().orElse(null);
    }

    private void captureMob(LivingEntity entity) {
        var hand = InteractionHand.MAIN_HAND;
        var hit = new EntityHitResult(entity, entity.position().add(0, 0.5, 0));
        if (!minecraft.gameMode.interactAt(minecraft.player, entity, hit, hand).consumesAction()) {
            minecraft.gameMode.interact(minecraft.player, entity, hand);
        }
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_spawner_rules", result);
    }
}

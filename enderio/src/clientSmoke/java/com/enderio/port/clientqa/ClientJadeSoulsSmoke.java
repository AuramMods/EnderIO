package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.compat.jade.EIOJadePlugin;
import com.enderio.enderio.init.EIOItems;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import snownee.jade.impl.config.PluginConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientJadeSoulsSmoke {
    private enum Action { LOOK, VIAL, PLACE, CONFIGURE }

    private record Step(String name, int x, String block, String soul, boolean enabled, Action action) {
        BlockPos pos() { return new BlockPos(x, 80, 8); }
    }

    private static final Gson GSON = new Gson();

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long firstTitleFrame;
    private boolean connecting;
    private boolean joined;
    private boolean finished;
    private int index;
    private long stepStarted;
    private int actedAt = -1;
    private Step step;

    ClientJadeSoulsSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 210_000_000_000L) {
                throw new AssertionError("Jade soul session timed out at step " + index);
            }
            if (!connecting) {
                connect();
                return;
            }
            if (step != null && actedAt >= 0 && minecraft.screen != null) {
                throw new AssertionError("Unexpected screen during " + step.name() + ": " + minecraft.screen.getClass().getSimpleName());
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            if (!joined) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("joins_the_real_dedicated_server", !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                joined = true;
                return;
            }
            if (step == null) {
                if (Files.exists(output.resolve("server-finish"))) {
                    finish(null);
                    return;
                }
                var request = output.resolve("step-" + index + ".json");
                if (!Files.exists(request)) return;
                step = GSON.fromJson(Files.readString(request), Step.class);
                stepStarted = System.nanoTime();
            }
            if (System.nanoTime() - stepStarted > 17_000_000_000L) {
                throw new AssertionError("Jade overlay did not settle for " + step + ", current hit " + minecraft.hitResult
                    + ", observed overlay " + ClientJadeSmoke.overlayAt(step.pos()));
            }
            if (actedAt < 0 && !act()) return;
            if (minecraft.player.tickCount - actedAt < 12 || hitAt(step.pos()) == null) return;
            inspect();
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (firstTitleFrame == 0) firstTitleFrame = System.nanoTime();
        if (System.nanoTime() - firstTitleFrame < 2_000_000_000L) return;
        var address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Jade Souls", address, true), false);
        connecting = true;
    }

    private boolean act() throws Exception {
        var player = minecraft.player;
        var hit = hitAt(step.action() == Action.PLACE ? step.pos().below() : step.pos());
        if (hit == null) return false;
        switch (step.action()) {
            case VIAL, PLACE -> {
                boolean vial = step.action() == Action.VIAL;
                if (player.getAbilities().instabuild != vial) return false;
                var stack = player.getInventory().getItem(0);
                if (vial ? !stack.is(EIOItems.SOUL_VIAL.get()) : !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(step.block())) {
                    return false;
                }
                var bindable = stack.getCapability(SoulBindable.CAPABILITY).orElse(null);
                if (bindable == null) return false;
                var soul = bindable.getBoundSoul();
                if (!(soul.hasEntity() ? soul.entityTypeId().toString() : "").equals(step.soul())) return false;
                if (!vial) check(step.name() + "_looks_at_the_real_floor", hit.getDirection() == Direction.UP);
                player.getInventory().selected = 0;
                check(step.name() + "_native_use_is_accepted",
                    minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                signal("acted-" + step.name());
            }
            case CONFIGURE -> {
                var config = PluginConfig.INSTANCE;
                check(step.name() + "_reads_the_provider_setting",
                    config.set(EIOJadePlugin.SOUL_BOUND_COMPONENT, step.enabled())
                    && config.get(EIOJadePlugin.SOUL_BOUND_COMPONENT) == step.enabled());
            }
            default -> {}
        }
        actedAt = player.tickCount;
        return true;
    }

    private BlockHitResult hitAt(BlockPos position) {
        return minecraft.hitResult instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(position) ? hit : null;
    }

    private void inspect() throws Exception {
        var view = ClientJadeSmoke.overlayAt(step.pos());
        boolean showsSoul = step.enabled() && !step.soul().isEmpty();
        if (view == null || !view.block().equals(step.block()) || view.soulLine() != showsSoul) return;
        if (step.enabled() && !view.soul().equals(step.soul())) return;
        var entity = minecraft.level.getBlockEntity(step.pos());
        if (entity == null || !view.serverType().equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString())) return;
        var title = minecraft.level.getBlockState(step.pos()).getBlock().getName().getString();
        check(step.name() + "_jade_renders_the_actual_machine", view.connected() && view.blockEntity() && view.text().contains(title));
        if (showsSoul) {
            var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(step.soul()));
            check(step.name() + "_exact_soul_name", view.text().contains("Bound Soul: " + type.getDescription().getString()));
        } else {
            check(step.name() + "_the_soul_line_is_absent", !view.text().contains("Bound Soul:"));
        }
        var frame = ClientSmoke.capture(step.name() + ".png");
        frame.put("jadeOverlay", view);
        if (entity instanceof SoulBindable bindable) {
            var localSoul = bindable.getBoundSoul();
            frame.put("clientSoul", localSoul.hasEntity() ? localSoul.entityTypeId().toString() : "");
        }
        captures.put(step.name(), frame);
        check(step.name() + "_capture_is_silent_and_hidden", Boolean.TRUE.equals(frame.get("passed")));
        signal(step.name());
        index++;
        actedAt = -1;
        step = null;
    }

    private void signal(String name) throws Exception {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        finished = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_jade_souls", result);
    }
}

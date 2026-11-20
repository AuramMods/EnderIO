package com.enderio.port.clientqa;

import com.enderio.enderio.api.glider.GliderMovementInfo;
import com.enderio.enderio.api.integration.ClientIntegration;
import com.enderio.enderio.api.integration.Integration;
import com.enderio.enderio.api.integration.IntegrationManager;
import com.enderio.enderio.client.EnderIOClient;
import com.enderio.enderio.content.tools.hang_glider.PlayerMovementHandler;
import com.enderio.enderio.foundation.integrations.EnderIOSelfIntegration;
import com.enderio.enderio.init.EIOItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class ClientRemoteHangGliderSmoke {
    private enum Stage { TITLE, JOIN, GROUND, FLIGHT, LANDED, DONE }

    private record Pose(int entityId, boolean grounded, boolean gliding, float vertical) { }

    private static final String WEARER = "EnderIOQA";
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> poses = new LinkedHashMap<>();
    private final Matrix4f inverseRoot = new Matrix4f();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private boolean retriedJoin;
    private Pose renderedPose;
    private int rootEntityId = -1;

    ClientRemoteHangGliderSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::beforePlayer);
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::aim);
        // Read the native layer's pose without drawing anything or changing its glider selection.
        IntegrationManager.addIntegration(new Integration() {
            private final ClientIntegration probe = new ClientIntegration() {
                @Override
                public void renderHangGlider(PoseStack stack, MultiBufferSource buffers, int light, int overlay,
                        AbstractClientPlayer player, float partialTick) {
                    if (rootEntityId != player.getId()) return;
                    var pose = new Matrix4f(inverseRoot).mul(stack.last().pose());
                    float vertical = pose.m11() / (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11() + pose.m12() * pose.m12());
                    renderedPose = new Pose(player.getId(), player.onGround(), PlayerMovementHandler.isGliding(player), vertical);
                }
            };

            @Override
            public Optional<GliderMovementInfo> getGliderMovementInfo(Player player) {
                return isWearer(player) ? EnderIOSelfIntegration.INSTANCE.getGliderMovementInfo(player) : Optional.empty();
            }

            @Override
            public ClientIntegration getClientIntegration() {
                return probe;
            }
        });
    }

    private static boolean isWearer(Player player) {
        return player instanceof RemotePlayer && player.getGameProfile().getName().equals(WEARER);
    }

    private void beforePlayer(RenderPlayerEvent.Pre event) {
        if (isWearer(event.getEntity())) {
            rootEntityId = event.getEntity().getId();
            inverseRoot.set(event.getPoseStack().last().pose()).invert();
        }
    }

    private void aim(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage.ordinal() < Stage.GROUND.ordinal() || stage == Stage.DONE
            || minecraft.player == null || minecraft.level == null) return;
        minecraft.level.players().stream().filter(ClientRemoteHangGliderSmoke::isWearer).findFirst().ifPresent(wearer -> {
            var player = minecraft.player;
            var look = wearer.position().add(0, 1.25, 0).subtract(player.getEyePosition());
            player.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90));
            player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        });
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            renderedPose = null;
            rootEntityId = -1;
            return;
        }
        if (stage == Stage.DONE) return;
        try {
            if (stage == Stage.JOIN && minecraft.screen instanceof DisconnectedScreen && !retriedJoin) {
                retriedJoin = true;
                connect();
                return;
            }
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 235_000_000_000L) {
                throw new AssertionError("Remote Hang Glider observer stopped during " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady > 2_000_000_000L) connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("observer_uses_dedicated_tcp_in_survival", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("native_canopy_is_baked", EnderIOClient.GLIDER_MODELS.containsKey(EIOItems.GLIDER.get()));
                    minecraft.options.hideGui = true;
                    signal("connected");
                    stage = Stage.GROUND;
                }
                case GROUND -> {
                    if (!marker("ground") || renderedPose == null || !renderedPose.grounded) return;
                    check("equipped_remote_player_renders_upright_before_launch", upright(renderedPose));
                    capture("grounded", renderedPose);
                    signal("grounded");
                    stage = Stage.FLIGHT;
                }
                case FLIGHT -> {
                    if (marker("landed")) {
                        stage = Stage.LANDED;
                        return;
                    }
                    if (!marker("gliding") || renderedPose == null || renderedPose.grounded) return;
                    if (renderedPose.gliding && Math.abs(renderedPose.vertical) < 0.4f) {
                        if (!captures.containsKey("deployed")) capture("deployed", renderedPose);
                    } else if (!captures.containsKey("deployed") && !captures.containsKey("airborne-upright")) {
                        capture("airborne-upright", renderedPose);
                    }
                }
                case LANDED -> {
                    if (renderedPose == null || !renderedPose.grounded) return;
                    if (!(minecraft.level.getEntity(renderedPose.entityId) instanceof RemotePlayer wearer)
                        || wearer.distanceTo(player) > 8) return;
                    checks.put("remote_canopy_rotates_during_server_confirmed_flight", captures.containsKey("deployed"));
                    check("same_remote_glider_returns_to_its_upright_pose", upright(renderedPose)
                        && wearer.getItemBySlot(EquipmentSlot.CHEST).is(EIOItems.GLIDER.get()));
                    capture("landed", renderedPose);
                    signal("verified");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private static boolean upright(Pose pose) {
        return !pose.gliding && Math.abs(pose.vertical) > 0.95f;
    }

    private void connect() {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Hang Glider observer", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void capture(String name, Pose pose) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        poses.put(name, Map.of("entity", pose.entityId, "grounded", pose.grounded, "gliding", pose.gliding, "vertical", pose.vertical));
        check(name + "_uses_a_hidden_muted_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.hideGui = false;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean.TRUE::equals));
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("poses", poses);
        result.put("retriedJoin", retriedJoin);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_remote_hang_glider", result);
    }
}

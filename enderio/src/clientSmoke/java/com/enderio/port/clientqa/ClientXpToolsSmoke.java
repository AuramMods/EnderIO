package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOItems;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Operates the XP tools through a real packaged survival client connection. */
final class ClientXpToolsSmoke {
    private static final BlockPos TANK = new BlockPos(0, 80, 0);
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    private final long start = System.nanoTime();
    private long titleReady;
    private boolean connecting;
    private boolean joined;
    private boolean done;
    private int sequence;
    private int captureFrames;
    private Step drinking;

    ClientXpToolsSmoke() {
        result.put("checks", checks);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - start > 160_000_000_000L) {
                throw new AssertionError("XP tools client stopped at operation " + sequence);
            }
            if (!connecting) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (captureFrames > 0 && ++captureFrames == 5) {
                var capture = ClientSmoke.capture("xp-tools-inventory.png");
                check("hidden_and_muted", Boolean.TRUE.equals(capture.get("passed")));
                result.put("capture", capture);
                finish(null);
                return;
            }
            if (minecraft.screen != null) return;
            if (!joined) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                var connection = minecraft.getConnection().getConnection();
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                    && connection.getRemoteAddress() instanceof InetSocketAddress);
                joined = true;
                signal("connected");
            }
            if (drinking != null) {
                if (player.isUsingItem()) return;
                minecraft.options.keyUse.setDown(false);
                signal(drinking.name());
                drinking = null;
                return;
            }
            var request = output.resolve("server-operation-" + sequence + ".json");
            if (!Files.exists(request)) return;
            var step = new Gson().fromJson(Files.readString(request), Step.class);
            var stack = player.getMainHandItem();
            if (player.distanceToSqr(0.5, 80, 3.5) > 0.1
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(step.held())
                || step.fluid() >= 0 && fluid(stack) != step.fluid()
                || step.offhand() >= 0 && fluid(player.getOffhandItem()) != step.offhand()
                || step.xp() >= 0 && player.totalExperience != step.xp()) return;
            sequence++;
            switch (step.action()) {
                case "transfer", "deposit" -> {
                    boolean crouching = step.action().equals("deposit");
                    if (crouching) shift(true);
                    var hit = new BlockHitResult(new Vec3(0.5, 80.5, 1), Direction.SOUTH, TANK, false);
                    check(step.name(), minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    if (crouching) shift(false);
                    signal(step.name());
                }
                case "drink" -> {
                    minecraft.options.keyUse.setDown(true);
                    check(step.name(), minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()
                        && player.isUsingItem());
                    drinking = step;
                }
                case "drink-remainder" -> {
                    check(step.name(), minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL
                        && !player.isUsingItem());
                    signal(step.name());
                }
                case "inspect" -> {
                    check(step.name(), player.getOffhandItem().is(EIOItems.VOID_VIAL.get()));
                    var lines = stack.getTooltipLines(player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
                    result.put("tooltip", lines);
                    if (step.fluid() == 15) {
                        check("remainder_tooltip", lines.contains("15/3200 mb of XP Juice") && lines.contains("Stored: 0 levels + 0 experience"));
                    }
                    minecraft.setScreen(new InventoryScreen(player));
                    captureFrames = 1;
                }
                default -> throw new AssertionError("Unknown XP operation " + step.action());
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void shift(boolean press) {
        var action = press ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
            : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY;
        minecraft.getConnection().send(new ServerboundPlayerCommandPacket(minecraft.player, action));
    }

    private static int fluid(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
            .map(handler -> handler.getFluidInTank(0).getAmount()).orElse(-1);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        connecting = true;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO experience tools", address, true), false);
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void signal(String name) throws java.io.IOException {
        Path signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void finish(Throwable failure) {
        done = true;
        minecraft.options.keyUse.setDown(false);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_xp_tools", result);
    }

    private record Step(String name, String action, String held, int fluid, int offhand, int xp) {}
}

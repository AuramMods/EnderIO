package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.conduits.facade.FacadeType;
import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleRenderState;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientAE2FacadeSmoke {
    private enum Stage { TITLE, JOIN, BARE, VISIBLE, GHOST, MINING, BROKEN, RECOVER, REPLANT, RELOAD, REOPEN, DONE }

    private static final BlockPos TARGET = new BlockPos(1087, 80, 4);
    private static final ResourceLocation FACADE = ResourceLocation.parse("ae2:facade");
    private static final ResourceLocation CONDUIT = ResourceLocation.parse("enderio:me");
    private static final int TOOL = 4;
    private static final int WRENCH = 8;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopen = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int stableTick = -1;
    private int barePaint;
    private int fullPaint;
    private int lastMiningTick;
    private int recoveredSlot;
    private ItemStack original;
    private CompletableFuture<Void> reload;

    ClientAE2FacadeSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("AE2 facade scenario stopped at " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("AE2 facade QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !minecraft.player.isCreative() && minecraft.getSingleplayerServer() == null
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = reopen ? Stage.REOPEN : Stage.BARE;
                }
                case BARE -> {
                    var stack = minecraft.player.getInventory().getItem(0);
                    if (!ready("server-place") || !positioned() || !isFacade(stack)) return;
                    minecraft.player.getInventory().selected = 0;
                    if (!meshReady(true)) return;
                    check("native_ae2_inventory_facade_has_the_diamond_provider", isDiamondFacade(stack));
                    check("native_ae2_inventory_facade_has_a_baked_model", minecraft.getItemRenderer().getModel(stack,
                        minecraft.level, minecraft.player, 0) != minecraft.getModelManager().getMissingModel());
                    original = stack.copy();
                    barePaint = capture("ae2-facade-bare");
                    place(0, "native_placement_uses_the_ae2_item");
                    stage = Stage.VISIBLE;
                }
                case VISIBLE -> {
                    if (!attached() || !meshReady(true)) return;
                    check("installed_item_and_me_node_sync", ItemStack.matches(original, bundle().getFacadeProvider()));
                    fullPaint = capture("ae2-facade-visible");
                    check("diamond_face_appears_in_world", fullPaint > barePaint + 450);
                    signal("placed");
                    minecraft.player.getInventory().selected = WRENCH;
                    stage = Stage.GHOST;
                }
                case GHOST -> {
                    if (!meshReady(false)) return;
                    check("wrench_mode_exposes_the_me_mesh", conduitQuads());
                    check("wrench_mode_fades_the_diamond_face", capture("ae2-facade-wrench") * 2 < fullPaint);
                    minecraft.player.getInventory().selected = TOOL;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!ready("server-mine") || !positioned() || !meshReady(true)) return;
                    if (!minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)) return;
                    check("survival_mining_starts_on_the_visible_facade", minecraft.gameMode.startDestroyBlock(TARGET, Direction.SOUTH));
                    lastMiningTick = minecraft.player.tickCount;
                    stage = Stage.BROKEN;
                }
                case BROKEN -> {
                    if (bundle() != null && bundle().hasFacade()) {
                        if (lastMiningTick != minecraft.player.tickCount) {
                            lastMiningTick = minecraft.player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(TARGET, Direction.SOUTH);
                        }
                        return;
                    }
                    check("mining_leaves_the_me_conduit_in_place", hasConduit());
                    signal("mined");
                    stage = Stage.RECOVER;
                }
                case RECOVER -> {
                    for (int slot = 0; slot < 9; slot++) {
                        if (!isFacade(minecraft.player.getInventory().getItem(slot))) continue;
                        check("survival_pickup_recovers_the_exact_ae2_facade", ItemStack.matches(original, minecraft.player.getInventory().getItem(slot)));
                        recoveredSlot = slot;
                        signal("picked-up");
                        stage = Stage.REPLANT;
                        return;
                    }
                }
                case REPLANT -> {
                    if (!ready("server-replant") || !positioned() || !meshReady(true)) return;
                    place(recoveredSlot, "recovered_native_facade_reinstalls");
                    reload = minecraft.reloadResourcePacks();
                    stage = Stage.RELOAD;
                }
                case RELOAD -> {
                    if (!reload.isDone() || minecraft.getOverlay() != null || !attached() || !meshReady(true)) return;
                    reload.join();
                    check("replant_consumes_the_original_ae2_drop", ItemStack.matches(original, bundle().getFacadeProvider())
                        && minecraft.player.getInventory().getItem(recoveredSlot).isEmpty());
                    check("resource_reload_renders_the_same_replanted_facade", capture("ae2-facade-reloaded") > barePaint + 450);
                    signal("rendered");
                    finish(null);
                }
                case REOPEN -> {
                    if (!ready("server-reopen") || !positioned() || !attached() || !meshReady(true)) return;
                    check("restart_restores_the_native_item_and_diamond_mesh", isDiamondFacade(bundle().getFacadeProvider())
                        && capture("ae2-facade-reopened") > 450);
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

    private void place(int slot, String name) {
        minecraft.player.getInventory().selected = slot;
        var hit = new BlockHitResult(Vec3.atCenterOf(TARGET).relative(Direction.SOUTH, 0.125), Direction.SOUTH, TARGET, false);
        check(name, minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private boolean positioned() {
        return minecraft.player.distanceToSqr(1087.5, 80, 6.5) < 0.09;
    }

    private ConduitBundleBlockEntity bundle() {
        return minecraft.level.getBlockEntity(TARGET) instanceof ConduitBundleBlockEntity bundle ? bundle : null;
    }

    private boolean hasConduit() {
        return bundle() != null && bundle().getConduits().stream()
            .anyMatch(node -> node.unwrapKey().orElseThrow().location().equals(CONDUIT));
    }

    private boolean attached() {
        if (!hasConduit() || !bundle().hasFacade()) return false;
        var data = bundle().getModelData().get(ConduitBundleRenderState.PROPERTY);
        return data != null && data.hasFacade() && data.facade().is(Blocks.DIAMOND_BLOCK)
            && data.doesFacadeHideConduits() == FacadeType.BASIC.doesHideConduits();
    }

    private boolean conduitQuads() {
        var state = minecraft.level.getBlockState(TARGET);
        var data = bundle().getModelData();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var random = RandomSource.create(42);
        return !model.getQuads(state, null, random, data, RenderType.cutout()).isEmpty()
            || Arrays.stream(Direction.values()).anyMatch(side -> !model.getQuads(state, side, random, data, RenderType.cutout()).isEmpty());
    }

    private boolean meshReady(boolean visible) {
        if (ClientFacadeVisibility.areFacadesVisible() != visible) return false;
        if (stableTick == -1) stableTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - stableTick < 25) return false;
        stableTick = -1;
        return true;
    }

    private static boolean isFacade(ItemStack stack) {
        return FACADE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean isDiamondFacade(ItemStack stack) {
        var provider = stack.getCapability(EnderIOCapabilities.CONDUIT_FACADE_PROVIDER).orElse(null);
        return isFacade(stack) && stack.getCount() == 1 && provider != null && provider.isValid()
            && provider.type() == FacadeType.BASIC && provider.block() == Blocks.DIAMOND_BLOCK;
    }

    private int capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        int diamondPixels = 0;
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (int y = image.getHeight() * 44 / 100; y < image.getHeight() * 64 / 100; y++) {
                for (int x = image.getWidth() * 42 / 100; x < image.getWidth() * 58 / 100; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = pixel & 255, green = pixel >>> 8 & 255, blue = pixel >>> 16 & 255;
                    if (green - red > 40 && blue - red > 34 && Math.abs(green - blue) < 45) diamondPixels++;
                }
            }
        }
        frame.put("diamondPixels", diamondPixels);
        captures.put(name, frame);
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
        return diamondPixels;
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
        ClientSmoke.complete("client_ae2_facade", result);
    }
}

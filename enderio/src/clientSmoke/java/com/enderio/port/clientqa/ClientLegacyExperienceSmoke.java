package com.enderio.port.clientqa;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ClientLegacyExperienceSmoke {
    private enum Stage { TITLE, JOIN, MINE, BREAK, MELEE, BOW, DRAW, VERIFIED, CAPTURE, DONE }

    private static final BlockPos[] SCULK = { new BlockPos(3871, 80, 8), new BlockPos(3875, 80, 8) };
    private static final String[] MINING_TRIALS = { "boosted", "silk" };
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean reopened = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int miningTrial;
    private int miningTick;

    ClientLegacyExperienceSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 145_000_000_000L) {
                throw new AssertionError("Legacy experience client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.CAPTURE) {
                capture();
                return;
            }
            if (minecraft.screen != null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    signal("connected");
                    stage = reopened ? Stage.VERIFIED : Stage.MINE;
                }
                case MINE -> {
                    var pos = SCULK[miningTrial];
                    var name = MINING_TRIALS[miningTrial];
                    if (!marker("mine-" + name) || player.distanceToSqr(pos.getX() + 0.5, 80, 10.5) > 0.1
                        || !minecraft.level.getBlockState(pos).is(Blocks.SCULK) || !player.getMainHandItem().is(Items.DIAMOND_HOE)) return;
                    check("native_survival_break_" + name, minecraft.gameMode.startDestroyBlock(pos, Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.BREAK;
                }
                case BREAK -> {
                    var pos = SCULK[miningTrial];
                    if (!minecraft.level.isEmptyBlock(pos)) {
                        if (miningTick != player.tickCount) {
                            miningTick = player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(pos, Direction.SOUTH);
                        }
                        return;
                    }
                    signal("mined-" + MINING_TRIALS[miningTrial++]);
                    stage = miningTrial < SCULK.length ? Stage.MINE : Stage.MELEE;
                }
                case MELEE -> {
                    if (!marker("melee") || !player.getMainHandItem().is(Items.IRON_SWORD)) return;
                    var target = minecraft.level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(3),
                        zombie -> player.distanceToSqr(zombie) < 6.25 && zombie.getName().getString().equals("XP melee target"))
                        .stream().findFirst().orElse(null);
                    if (target == null) return;
                    minecraft.gameMode.attack(player, target);
                    player.swing(InteractionHand.MAIN_HAND);
                    signal("melee-hit");
                    stage = Stage.BOW;
                }
                case BOW -> {
                    if (!marker("draw") || player.distanceToSqr(3890.5, 80, 21.5) > 0.1 || !player.getMainHandItem().is(Items.BOW)) return;
                    minecraft.options.keyUse.setDown(true);
                    check("native_bow_draw", minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
                    stage = Stage.DRAW;
                }
                case DRAW -> {
                    if (!player.isUsingItem() || player.getTicksUsingItem() < 25) return;
                    minecraft.options.keyUse.setDown(false);
                    minecraft.gameMode.releaseUsingItem(player);
                    signal("released");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!marker("verified") || player.totalExperience != (reopened ? 21 : 10)) return;
                    var armor = player.getItemBySlot(EquipmentSlot.CHEST);
                    var shimmer = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.fromNamespaceAndPath("enderio", "shimmer"));
                    check("migrated_shimmer_renders_with_native_foil", shimmer != null && armor.getEnchantmentLevel(shimmer) == 1
                        && armor.hasFoil() && !Items.IRON_CHESTPLATE.getDefaultInstance().hasFoil());
                    check("native_item_tooltip_resolves_shimmer", armor.getTooltipLines(player, TooltipFlag.NORMAL)
                        .stream().anyMatch(line -> line.getString().equals("Shimmer")));
                    check("exact_experience_total_syncs", player.totalExperience == (reopened ? 21 : 10));
                    minecraft.setScreen(new InventoryScreen(player));
                    stage = Stage.CAPTURE;
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO legacy experience", address, true), false);
        stage = Stage.JOIN;
    }

    private void capture() throws IOException {
        check("native_inventory_screen", minecraft.screen instanceof InventoryScreen);
        var capture = ClientSmoke.capture("shimmer-inventory.png");
        captures.put("shimmer-inventory", capture);
        check("silent_hidden_inventory", Boolean.TRUE.equals(capture.get("passed")));
        finish(null);
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        Path signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyUse.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_legacy_experience", result);
    }
}

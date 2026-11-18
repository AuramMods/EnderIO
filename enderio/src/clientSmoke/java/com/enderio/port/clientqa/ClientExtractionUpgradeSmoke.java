package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.client.content.conduits.gui.ConduitScreen;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.content.conduits.type.item.ItemConduit;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class ClientExtractionUpgradeSmoke {
    private enum Stage { TITLE, WORLD, FIXTURE, MENU, INSERT, INSERT_CONFIRMED, REMOVE, REMOVE_CONFIRMED, DONE }

    private static final BlockPos POS = new BlockPos(0, 80, 0);
    private static final int SLOT = ItemConduit.EXTRACTION_UPGRADE_SLOT;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> capture = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private CompletableFuture<?> pending;
    private int inventorySlot;

    ClientExtractionUpgradeSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 210_000_000_000L) throw new AssertionError("Timed out in " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    var models = minecraft.getModelManager();
                    for (var upgrade : EIOItems.LEGACY_EXTRACTION_UPGRADES) {
                        var stack = new ItemStack(upgrade.get());
                        check("baked_model_" + upgrade.getId().getPath(),
                            minecraft.getItemRenderer().getModel(stack, null, null, 0) != models.getMissingModel());
                    }
                    String name = System.getProperty("enderio.clientqa.worldName");
                    var options = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(name, options,
                        new WorldOptions(55L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    var server = minecraft.getSingleplayerServer();
                    if (server == null || minecraft.player == null || minecraft.screen != null) return;
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        var player = serverPlayer();
                        for (int z = 0; z <= 3; z++) {
                            level.setBlockAndUpdate(new BlockPos(0, 79, z), Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(POS.south(), Blocks.BARREL.defaultBlockState());
                        level.setBlockAndUpdate(POS, EIOBlocks.CONDUIT_BUNDLE.get().defaultBlockState());
                        var bundle = (ConduitBundleBlockEntity) level.getBlockEntity(POS);
                        var conduit = level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
                            .getHolderOrThrow(EIOConduits.ITEM);
                        bundle.addConduit(conduit, Direction.SOUTH, player);
                        var upgrades = new ItemStack(EIOItems.LEGACY_EXTRACTION_UPGRADES.get(3).get(), 3);
                        upgrades.getOrCreateTag().putInt("PortUpgradeScreen", 4);
                        player.getInventory().setItem(9, upgrades);
                        player.connection.teleport(0.5, 80, 2.5, 180, 0);
                    });
                    stage = Stage.FIXTURE;
                }
                case FIXTURE -> {
                    if (!pending.isDone() || !(minecraft.level.getBlockEntity(POS) instanceof ConduitBundleBlockEntity)) return;
                    pending.join();
                    var server = minecraft.getSingleplayerServer();
                    pending = server.submit(() -> {
                        var level = server.overworld();
                        var conduit = level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
                            .getHolderOrThrow(EIOConduits.ITEM);
                        ConduitMenu.openConduitMenu(serverPlayer(), (ConduitBundleBlockEntity) level.getBlockEntity(POS),
                            Direction.SOUTH, conduit);
                    });
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!pending.isDone()) return;
                    pending.join();
                    if (!(minecraft.screen instanceof ConduitScreen) ||
                        !(minecraft.player.containerMenu instanceof ConduitMenu menu)) return;
                    var upgradeStack = menu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory()
                        && slot.getContainerSlot() == 9 && isUpgrade(slot.getItem(), 3)).findFirst();
                    if (upgradeStack.isEmpty()) return;
                    inventorySlot = upgradeStack.get().index;
                    check("forge_open_packet_creates_upgrade_slot", menu.getSide() == Direction.SOUTH
                        && menu.getSlot(SLOT).getMaxStackSize() == 1 && menu.getSlot(SLOT).mayPlace(upgradeStack.get().getItem()));
                    click(menu, inventorySlot);
                    click(menu, SLOT);
                    stage = Stage.INSERT;
                }
                case INSERT -> {
                    var menu = (ConduitMenu) minecraft.player.containerMenu;
                    if (!isUpgrade(menu.getSlot(SLOT).getItem(), 1) || !isUpgrade(menu.getCarried(), 2)) return;
                    pending = minecraft.getSingleplayerServer().submit(() ->
                        isUpgrade(serverPlayer().containerMenu.getSlot(SLOT).getItem(), 1)
                            && isUpgrade(serverPlayer().containerMenu.getCarried(), 2));
                    stage = Stage.INSERT_CONFIRMED;
                }
                case INSERT_CONFIRMED -> {
                    if (!pending.isDone()) return;
                    check("click_packets_install_one_tagged_upgrade", Boolean.TRUE.equals(pending.join()));
                    capture.putAll(ClientSmoke.capture("extraction-upgrade-menu.png"));
                    check("silent_hidden_menu_render", Boolean.TRUE.equals(capture.get("passed")));
                    var menu = (ConduitMenu) minecraft.player.containerMenu;
                    click(menu, inventorySlot);
                    click(menu, SLOT);
                    stage = Stage.REMOVE;
                }
                case REMOVE -> {
                    var menu = (ConduitMenu) minecraft.player.containerMenu;
                    if (menu.getSlot(SLOT).hasItem() || !isUpgrade(menu.getCarried(), 1)) return;
                    pending = minecraft.getSingleplayerServer().submit(() -> !serverPlayer().containerMenu.getSlot(SLOT).hasItem()
                        && isUpgrade(serverPlayer().containerMenu.getCarried(), 1)
                        && isUpgrade(serverPlayer().getInventory().getItem(9), 2));
                    stage = Stage.REMOVE_CONFIRMED;
                }
                case REMOVE_CONFIRMED -> {
                    if (!pending.isDone()) return;
                    check("click_packets_return_installed_item", Boolean.TRUE.equals(pending.join()));
                    stage = Stage.DONE;
                    minecraft.player.closeContainer();
                    minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    check("integrated_world_stopped", minecraft.getSingleplayerServer() == null);
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private ServerPlayer serverPlayer() {
        return minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0);
    }

    private static boolean isUpgrade(ItemStack stack, int count) {
        return stack.is(EIOItems.LEGACY_EXTRACTION_UPGRADES.get(3).get()) && stack.getCount() == count
            && stack.getTag() != null && stack.getTag().getInt("PortUpgradeScreen") == 4;
    }

    private void click(ConduitMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, minecraft.player);
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("capture", capture);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_extraction_upgrades", result);
    }
}

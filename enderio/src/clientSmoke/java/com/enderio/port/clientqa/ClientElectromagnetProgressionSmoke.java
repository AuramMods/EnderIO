package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.keymaps.KeymapHandler;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerMenu;
import com.enderio.enderio.init.EIOItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;

final class ClientElectromagnetProgressionSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, RESULT, TAKE, CHARGER, INSERT, INPUT, WITHDRAW, RECLAIMED, ORB, ACTIVE, DEPLETED, VERIFY, DONE
    }

    private static final Craft RECIPE = Craft.shaped("electromagnet", 1, "EVEE EC C",
        Map.of('E', "energetic_alloy_ingot", 'V', "vibrant_crystal", 'C', "conductive_alloy_ingot"));
    private static final BlockPos TABLE = new BlockPos(4240, 80, 8);
    private static final BlockPos CHARGER = new BlockPos(4244, 80, 8);
    private static final Vec3 ORB_STAND = new Vec3(4250.5, 80, 8.5);
    private static final int CHARGE = 192;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int positioned = -1;
    private int magnetSlot;

    ClientElectromagnetProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 155_000_000_000L) {
                throw new AssertionError("Electromagnet progression stopped at " + stage);
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
                        && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!settled("table", stand(TABLE))) return;
                    check("ordinary_crafting_table_opens", open(TABLE));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, TAKE -> craft();
                case CHARGER -> {
                    if (!settled("charger", stand(CHARGER))) return;
                    player.getInventory().selected = (magnetSlot + 1) % 9;
                    check("empty_hand_opens_the_stock_wired_charger", player.getMainHandItem().isEmpty() && open(CHARGER));
                    stage = Stage.INSERT;
                }
                case INSERT, INPUT, WITHDRAW, RECLAIMED -> charge();
                case ORB -> {
                    if (!settled("activate", ORB_STAND)) return;
                    check("the_crafted_magnet_starts_the_orb_test_with_exactly_192_fe", idle(magnet(), CHARGE) && player.experienceLevel == 0);
                    KeyMapping.click(InputConstants.getKey(KeymapHandler.TOGGLE_MAGNET_KEY.get().saveString()));
                    stage = Stage.ACTIVE;
                }
                case ACTIVE -> {
                    if (!ready("collected") || !magnet().hasFoil() || player.experienceLevel != 1) return;
                    check("the_live_client_receives_the_orb_xp_while_the_magnet_is_active", ItemStackEnergy.getEnergyStored(magnet()) > 0);
                    capture("experience-orb-collected");
                    stage = Stage.DEPLETED;
                }
                case DEPLETED -> {
                    if (!idle(magnet(), 0)) return;
                    check("the_original_magnet_automatically_shuts_off_at_zero_fe", player.experienceLevel == 1);
                    signal("depleted");
                    stage = Stage.VERIFY;
                }
                case VERIFY -> {
                    if (ready("finish")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var result = menu.getSlot(0).getItem();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("the_stock_vibrant_crystal_unlocks_the_limited_recipe", player.getInventory().countItem(EIOItems.VIBRANT_CRYSTAL.get()) == 1);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (!isMagnet(result) || grid.stream().filter(Slot::hasItem).count() != RECIPE.ingredientCount()) return;
                check("the_stock_grid_creates_one_uncharged_and_inactive_magnet", RECIPE.matchesGrid(grid) && idle(result, 0));
                capture("electromagnet-recipe");
                click(menu, 0, 0, ClickType.QUICK_MOVE);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (result.isEmpty() && grid.stream().noneMatch(Slot::hasItem)) {
                    for (int slot = 0; slot < 9; slot++) {
                        if (!idle(player.getInventory().getItem(slot), 0)) continue;
                        magnetSlot = slot;
                        player.closeContainer();
                        signal("crafted");
                        stage = Stage.CHARGER;
                        return;
                    }
                }
            }
            default -> {}
        }
    }

    private void charge() throws Exception {
        if (!(minecraft.player.containerMenu instanceof WiredChargerMenu menu)) return;
        switch (stage) {
            case INSERT -> {
                if (!menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()) || !idle(magnet(), 0)) return;
                click(menu, 1, magnetSlot, ClickType.SWAP);
                stage = Stage.INPUT;
            }
            case INPUT -> {
                var input = menu.getSlot(1).getItem();
                if (!idle(input, CHARGE) || !magnet().isEmpty()) return;
                check("wired_charger_transfers_the_exact_partial_charge_into_its_input", !menu.getSlot(2).hasItem());
                capture("partially-charged-magnet");
                signal("charged-in-slot");
                stage = Stage.WITHDRAW;
            }
            case WITHDRAW -> {
                if (!ready("withdraw") || !idle(menu.getSlot(1).getItem(), CHARGE) || !magnet().isEmpty()) return;
                click(menu, 1, magnetSlot, ClickType.SWAP);
                stage = Stage.RECLAIMED;
            }
            case RECLAIMED -> {
                if (menu.getSlot(1).hasItem() || !idle(magnet(), CHARGE)) return;
                minecraft.player.closeContainer();
                minecraft.player.getInventory().selected = magnetSlot;
                signal("charged");
                stage = Stage.ORB;
            }
            default -> {}
        }
    }

    private boolean settled(String name, Vec3 stand) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(stand) > 0.12) {
            positioned = -1;
            return false;
        }
        if (positioned < 0) positioned = player.tickCount;
        if (player.tickCount - positioned < 6) return false;
        positioned = -1;
        return true;
    }

    private static Vec3 stand(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, 80, pos.getZ() + 3.5);
    }

    private boolean open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, minecraft.player);
    }

    private ItemStack magnet() {
        return minecraft.player.getInventory().getItem(magnetSlot);
    }

    private static boolean isMagnet(ItemStack stack) {
        return stack.is(EIOItems.ELECTROMAGNET.get()) && stack.getCount() == 1;
    }

    private static boolean idle(ItemStack stack, int charge) {
        return isMagnet(stack) && !stack.hasFoil() && ItemStackEnergy.getEnergyStored(stack) == charge;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Stock Electromagnet", address, true), false);
        stage = Stage.JOIN;
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
        ClientSmoke.complete("client_electromagnet_progression", result);
    }
}

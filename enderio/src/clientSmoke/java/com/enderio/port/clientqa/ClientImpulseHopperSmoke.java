package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.ImpulseHopperScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientImpulseHopperSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, PREVIEW, CRAFTED, OPEN, MENU, CLEARED, SAMPLED, BLOCKED, READY,
        AIM_SIDE, SIDE_CHANGED, RUNNING, RESET_TABLE, RESET_INPUT, RESET_PREVIEW, RESET_CRAFTED, PLACE, VERIFIED, DONE
    }

    private static final BlockPos MACHINE = new BlockPos(2820, 80, 8);
    private static final BlockPos TABLE = new BlockPos(2826, 80, 12);
    private static final BlockPos LEGACY = new BlockPos(2824, 80, 5);
    private static final int CHARGE = 980;
    private static final int HOTBAR_FIRST = 46;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int recipeTick;
    private int arrivalTick = -1;

    ClientImpulseHopperSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 225_000_000_000L) {
                throw new AssertionError("Impulse Hopper scenario stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> open("server-craft", TABLE, Stage.RECIPE);
                case RECIPE, PREVIEW, CRAFTED, RESET_INPUT, RESET_PREVIEW, RESET_CRAFTED -> craft();
                case OPEN -> open("server-menu", MACHINE, Stage.MENU);
                case MENU, CLEARED, SAMPLED, BLOCKED, READY, AIM_SIDE, SIDE_CHANGED, RUNNING -> operate();
                case RESET_TABLE -> open("server-reset", TABLE, Stage.RESET_INPUT);
                case PLACE -> place();
                case VERIFIED -> {
                    if (ready("server-verified")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var hopper = EIOBlocks.IMPULSE_HOPPER.get().asItem();
        switch (stage) {
            case RECIPE, RESET_INPUT -> {
                boolean reset = stage == Stage.RESET_INPUT;
                var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(
                    reset ? "enderio:erase_impulse_hopper" : "enderio:impulse_hopper")).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                if (reset) {
                    var stack = menu.getSlot(37).getItem();
                    if (!stack.is(hopper) || !stack.hasTag()) return;
                    check("reset_input_carries_live_charge_and_side_mode", EIODataComponents.ENERGY.getOrDefault(stack, 0) == CHARGE - 30
                        && IOConfig.ITEM_DATA.get(stack).getMode(Direction.SOUTH) == IOMode.PUSH);
                    click(menu, 37, ClickType.PICKUP);
                    click(menu, 1, ClickType.PICKUP);
                } else {
                    check("void_chassis_unlocks_the_shipped_recipe", true);
                    minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                }
                recipeTick = minecraft.player.tickCount;
                stage = reset ? Stage.RESET_PREVIEW : Stage.PREVIEW;
            }
            case PREVIEW, RESET_PREVIEW -> {
                ItemStack result = menu.getSlot(0).getItem();
                if (!result.is(hopper)) {
                    if (minecraft.player.tickCount - recipeTick > 60) {
                        throw new AssertionError("The crafting table did not produce an Impulse Hopper in " + stage);
                    }
                    return;
                }
                boolean reset = stage == Stage.RESET_PREVIEW;
                check(reset ? "factory_reset_preview_is_clean" : "native_survival_recipe_previews_one_hopper", result.getCount() == 1 && !result.hasTag());
                capture(reset ? "impulse-reset" : "impulse-crafting");
                click(menu, 0, ClickType.QUICK_MOVE);
                stage = reset ? Stage.RESET_CRAFTED : Stage.CRAFTED;
            }
            case CRAFTED, RESET_CRAFTED -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem)
                    || minecraft.player.getInventory().items.stream().noneMatch(stack -> stack.is(hopper)
                        && stack.getCount() == 1 && !stack.hasTag())) return;
                boolean reset = stage == Stage.RESET_CRAFTED;
                check(reset ? "native_pickup_returns_one_reset_item" : "native_pickup_returns_one_crafted_hopper", menu.getCarried().isEmpty());
                minecraft.player.closeContainer();
                signal(reset ? "reset-crafted" : "crafted");
                stage = reset ? Stage.PLACE : Stage.OPEN;
            }
            default -> { }
        }
    }

    private void operate() throws IOException {
        if (!(minecraft.screen instanceof ImpulseHopperScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case MENU -> {
                if (menu.getEnergyStorage() == null || menu.getEnergyStorage().energyStored() != CHARGE
                    || menu.getRedstoneControl() != RedstoneControl.NEVER_ACTIVE || !iron(menu.getSlot(1).getItem(), 1, 137)
                    || !iron(menu.getSlot(2).getItem(), 1, 138) || !iron(menu.getSlot(HOTBAR_FIRST).getItem(), 2, 137)) return;
                check("menu_syncs_capacitor_amount_and_blocked_output", menu.isCapacitorInstalled()
                    && menu.getSlot(18).getItem().is(EIOItems.BASIC_CAPACITOR.get()) && menu.getSlot(4).getItem().is(Items.REDSTONE));
                click(menu, 1, ClickType.PICKUP);
                stage = Stage.CLEARED;
            }
            case CLEARED -> {
                if (menu.getSlot(1).hasItem()) return;
                check("empty_cursor_clears_a_template_without_extracting_an_item", menu.getCarried().isEmpty());
                click(menu, HOTBAR_FIRST, ClickType.PICKUP);
                click(menu, 1, ClickType.PICKUP);
                stage = Stage.SAMPLED;
            }
            case SAMPLED -> {
                if (!iron(menu.getSlot(1).getItem(), 2, 137)) return;
                check("template_copies_all_two_items_without_consuming_the_cursor", iron(menu.getCarried(), 2, 137));
                click(menu, HOTBAR_FIRST, ClickType.PICKUP);
                stage = Stage.BLOCKED;
            }
            case BLOCKED -> {
                if (!menu.getCarried().isEmpty() || !iron(menu.getSlot(HOTBAR_FIRST).getItem(), 2, 137)) return;
                check("blocked_lane_draws_both_red_indicators", indicator(screen, 26, false) && indicator(screen, 53, false));
                check("unconfigured_lane_draws_no_input_indicator", !indicator(screen, 26, false, 2) && !indicator(screen, 26, true, 2));
                capture("impulse-blocked");
                click(menu, HOTBAR_FIRST, ClickType.QUICK_MOVE);
                click(menu, HOTBAR_FIRST + 1, ClickType.QUICK_MOVE);
                click(menu, 2, ClickType.QUICK_MOVE);
                stage = Stage.READY;
            }
            case READY -> {
                if (!iron(menu.getSlot(0).getItem(), 3, 137) || menu.getSlot(2).hasItem() || !menu.getSlot(3).getItem().is(Items.REDSTONE)) return;
                check("shift_click_delivers_matching_inputs_and_recovers_the_different_tag", minecraft.player.getInventory().items.stream()
                    .anyMatch(stack -> iron(stack, 1, 138)));
                check("ready_lane_draws_both_green_indicators", indicator(screen, 26, true) && indicator(screen, 53, true));
                capture("impulse-ready");
                check("native_side_overlay_opens", screen.mouseClicked(screen.getGuiLeft() + 161, screen.getGuiTop() + 30, 0));
                aimAtSide(screen);
                stage = Stage.AIM_SIDE;
            }
            case AIM_SIDE -> {
                capture("impulse-io");
                check("right_click_in_the_rendered_overlay_selects_a_real_face",
                    screen.mouseClicked(screen.getGuiLeft() + 88, screen.getGuiTop() + 121, 1));
                stage = Stage.SIDE_CHANGED;
            }
            case SIDE_CHANGED -> {
                if (menu.getBlockEntity().getIOMode(Direction.SOUTH) != IOMode.PUSH) return;
                check("the_selected_south_face_syncs_as_push", true);
                check("native_side_overlay_closes", screen.mouseClicked(screen.getGuiLeft() + 161, screen.getGuiTop() + 30, 0));
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check("native_redstone_picker_enables_batching", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.RUNNING;
            }
            case RUNNING -> {
                if (menu.getEnergyStorage().energyStored() != CHARGE - 30 || !iron(menu.getSlot(2).getItem(), 2, 137)
                    || menu.getSlot(5).getItem().getCount() != 2) return;
                check("the_open_menu_receives_the_complete_batch_and_exact_charge", iron(menu.getSlot(0).getItem(), 1, 137)
                    && menu.getSlot(3).getItem().isEmpty() && menu.getSlot(5).getItem().is(Items.REDSTONE)
                    && iron(menu.getSlot(1).getItem(), 2, 137) && menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE);
                capture("impulse-completed");
                minecraft.player.closeContainer();
                signal("batch");
                stage = Stage.RESET_TABLE;
            }
            default -> { }
        }
    }

    private static boolean iron(ItemStack stack, int count, int marker) {
        return stack.is(Items.IRON_INGOT) && stack.getCount() == count && stack.getTag() != null
            && stack.getTag().getInt("PortFixture") == marker;
    }

    private boolean indicator(ImpulseHopperScreen screen, int y, boolean ready) {
        return indicator(screen, y, ready, 0);
    }

    private boolean indicator(ImpulseHopperScreen screen, int y, boolean ready, int lane) {
        double scale = minecraft.getWindow().getGuiScale();
        int x0 = (int) ((screen.getGuiLeft() + 43 + 18 * lane) * scale);
        int y0 = (int) ((screen.getGuiTop() + y) * scale);
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (int row = 0; row < (int) (9 * scale); row++) {
                for (int col = 0; col < (int) (18 * scale); col++) {
                    int rgba = image.getPixelRGBA(x0 + col, y0 + row);
                    int red = rgba & 255, green = rgba >>> 8 & 255, blue = rgba >>> 16 & 255;
                    if (blue < 12 && (ready ? green > 120 && red < 12 : red > 120 && green < 12)) return true;
                }
            }
        }
        return false;
    }

    private void aimAtSide(ImpulseHopperScreen screen) {
        // Only the guarded client's in-game cursor coordinates change. The host cursor stays untouched.
        var window = minecraft.getWindow();
        double pixelX = (screen.getGuiLeft() + 88.5) * window.getScreenWidth() / window.getGuiScaledWidth();
        double pixelY = (screen.getGuiTop() + 121.5) * window.getScreenHeight() / window.getGuiScaledHeight();
        ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler, pixelX, "f_91507_");
        ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler, pixelY, "f_91508_");
    }

    private void place() throws IOException {
        var stack = minecraft.player.getInventory().getItem(0);
        if (!ready("server-place") || !positioned(LEGACY) || !stack.is(EIOBlocks.IMPULSE_HOPPER.get().asItem()) || !stack.hasTag()) {
            arrivalTick = -1;
            return;
        }
        if (arrivalTick < 0) arrivalTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - arrivalTick < 6) return;
        check("original_drop_reaches_client_with_converted_energy_and_controls", EIODataComponents.ENERGY.getOrDefault(stack, 0) == CHARGE
            && IOConfig.ITEM_DATA.get(stack).getMode(Direction.EAST) == IOMode.DISABLED
            && RedstoneControl.ITEM_DATA.get(stack) == RedstoneControl.ACTIVE_WITH_SIGNAL);
        minecraft.player.getInventory().selected = 0;
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(LEGACY), Direction.UP, LEGACY.below(), false);
        check("native_survival_places_the_archived_hopper", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal("placed");
        stage = Stage.VERIFIED;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Impulse Hopper", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(String name, BlockPos site, Stage next) {
        if (!ready(name) || !positioned(site)) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(site).relative(Direction.SOUTH, 0.5), Direction.SOUTH, site, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        stage = next;
    }

    private boolean positioned(BlockPos site) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(site.getX() + 0.5, 80, site.getZ() + 2.5) < 0.1;
    }

    private void click(AbstractContainerMenu menu, int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, type, minecraft.player);
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_impulse_hopper", result);
    }
}

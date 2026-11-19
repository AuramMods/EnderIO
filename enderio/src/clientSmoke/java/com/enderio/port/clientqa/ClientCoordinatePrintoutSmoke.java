package com.enderio.port.clientqa;

import com.enderio.enderio.api.attachment.CoordinateSelection;
import com.enderio.enderio.client.content.tools.CoordinateMenuScreen;
import com.enderio.enderio.content.tools.coordinate_selector.LocationPrintoutItem;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientCoordinatePrintoutSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, CRAFTED, COLLECTED, NO_PAPER, PAPER, SELECTED, CREATED, NETHER, EDIT, RENAMED,
        CLEAR, CLEARED, REOPEN, UNCHANGED, FINISH, DONE
    }

    private static final BlockPos TABLE = new BlockPos(3161, 80, 9);
    private static final BlockPos TARGET = new BlockPos(3161, 81, 0);
    private static final CoordinateSelection SELECTION = new CoordinateSelection(Level.OVERWORLD, TARGET);
    private static final String FIRST_NAME = "North Survey";
    private static final String RENAMED = "Nether Survey";

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int deadline;

    ClientCoordinatePrintoutSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 130_000_000_000L) {
                var screen = minecraft.screen;
                String detail = screen instanceof CraftingScreen crafting
                    ? crafting.getMenu().slots.subList(0, 10).stream().map(Slot::getItem).toList().toString()
                    : String.valueOf(screen);
                var player = minecraft.player;
                var held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
                throw new AssertionError("Coordinate printout stopped at " + stage + " with " + detail + ", held " + held.getTag());
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
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = restarted ? Stage.NETHER : Stage.TABLE;
                }
                case TABLE -> {
                    if (!marker("craft") || player.distanceToSqr(3161.5, 80, 11.5) > 0.1
                        || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    var hit = new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false);
                    check("native_crafting_table_interaction", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.RECIPE;
                }
                case RECIPE -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(EIOItems.COORDINATE_SELECTOR.getId()).orElseThrow();
                    if (!player.getRecipeBook().contains(recipe)) return;
                    check("stock_recipe_unlocked_from_the_ingredients", count(player, EIOItems.CONDUCTIVE_ALLOY_INGOT.get()) == 4
                        && count(player, Items.COMPASS) == 1 && count(player, Items.ENDER_PEARL) == 1);
                    minecraft.gameMode.handlePlaceRecipe(screen.getMenu().containerId, recipe, false);
                    stage = Stage.CRAFTED;
                }
                case CRAFTED -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var menu = screen.getMenu();
                    var grid = menu.slots.subList(1, 10);
                    if (!menu.getSlot(0).getItem().is(EIOItems.COORDINATE_SELECTOR.get())
                        || grid.stream().filter(Slot::hasItem).count() != 6) return;
                    check("native_crafting_grid_and_result", menu.getSlot(0).getItem().getCount() == 1
                        && grid.stream().filter(slot -> slot.getItem().is(EIOItems.CONDUCTIVE_ALLOY_INGOT.get())).count() == 4
                        && grid.get(1).getItem().is(Items.ENDER_PEARL) && grid.get(4).getItem().is(Items.COMPASS));
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                    stage = Stage.COLLECTED;
                }
                case COLLECTED -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen) || count(player, EIOItems.COORDINATE_SELECTOR.get()) != 1
                        || screen.getMenu().slots.subList(0, 10).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    stage = Stage.NO_PAPER;
                }
                case NO_PAPER -> {
                    if (!select(player, EIOItems.COORDINATE_SELECTOR.get())) return;
                    if (deadline == 0) {
                        check("selector_requires_paper", !minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
                        deadline = player.tickCount + 10;
                        return;
                    }
                    if (player.tickCount < deadline) return;
                    check("no_printout_or_menu_without_paper", minecraft.screen == null && count(player, EIOItems.LOCATION_PRINTOUT.get()) == 0);
                    signal("no-paper");
                    stage = Stage.PAPER;
                }
                case PAPER -> {
                    if (player.getOffhandItem().getCount() != 2 || !player.getOffhandItem().is(Items.PAPER)) return;
                    check("selector_uses_the_distant_target", player.distanceToSqr(Vec3.atCenterOf(TARGET)) > 100
                        && minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
                    stage = Stage.SELECTED;
                }
                case SELECTED -> {
                    if (!(minecraft.screen instanceof CoordinateMenuScreen screen)) return;
                    check("selector_menu_receives_exact_position", screen.getMenu().getSelection().equals(SELECTION)
                        && screen.getMenu().stillValid(player) && editBox(screen).getValue().isEmpty());
                    replaceName(screen, FIRST_NAME);
                    capture("selected-coordinate");
                    close(screen);
                    stage = Stage.CREATED;
                }
                case CREATED -> {
                    if (!select(player, EIOItems.LOCATION_PRINTOUT.get())) return;
                    verifyPrintout(player, FIRST_NAME);
                    signal("created");
                    stage = Stage.NETHER;
                }
                case NETHER -> {
                    if (!marker("edit") || minecraft.level.dimension() != Level.NETHER || minecraft.screen != null
                        || !select(player, EIOItems.LOCATION_PRINTOUT.get())) return;
                    if (!prepareCrouch(player)) return;
                    verifyPrintout(player, restarted ? RENAMED : FIRST_NAME);
                    usePrintout();
                    stage = restarted ? Stage.CLEAR : Stage.EDIT;
                }
                case EDIT, CLEAR -> {
                    if (!(minecraft.screen instanceof CoordinateMenuScreen screen)) return;
                    String oldName = restarted ? RENAMED : FIRST_NAME;
                    check("printout_menu_keeps_overworld_location_in_the_nether", screen.getMenu().getSelection().equals(SELECTION)
                        && editBox(screen).getValue().equals(oldName));
                    check("printout_menu_stays_valid_for_the_held_printer_output", screen.getMenu().stillValid(player));
                    replaceName(screen, restarted ? "" : RENAMED);
                    capture(restarted ? "cleared-name" : "nether-rename");
                    close(screen);
                    if (restarted) signal("clear-clicked");
                    stage = restarted ? Stage.CLEARED : Stage.RENAMED;
                }
                case RENAMED -> {
                    if (!player.getMainHandItem().getHoverName().getString().equals(RENAMED)) return;
                    verifyPrintout(player, RENAMED);
                    signal("renamed");
                    stage = Stage.FINISH;
                }
                case CLEARED -> {
                    if (player.getMainHandItem().hasCustomHoverName() || !marker("reopen")) return;
                    if (!prepareCrouch(player)) return;
                    verifyPrintout(player, "");
                    usePrintout();
                    stage = Stage.REOPEN;
                }
                case REOPEN -> {
                    if (!(minecraft.screen instanceof CoordinateMenuScreen screen)) return;
                    check("reopening_unnamed_printout_has_empty_edit_box", editBox(screen).getValue().isEmpty());
                    close(screen);
                    deadline = player.tickCount + 10;
                    stage = Stage.UNCHANGED;
                }
                case UNCHANGED -> {
                    if (player.tickCount < deadline) return;
                    verifyPrintout(player, "");
                    signal("cleared");
                    stage = Stage.FINISH;
                }
                case FINISH -> {
                    if (marker("done")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Location Printout", address, true), false);
        stage = Stage.JOIN;
    }

    private static int count(LocalPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean select(LocalPlayer player, Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (!player.getInventory().getItem(slot).is(item)) continue;
            player.getInventory().selected = slot;
            return true;
        }
        return false;
    }

    private boolean prepareCrouch(LocalPlayer player) {
        minecraft.options.keyShift.setDown(true);
        return player.isShiftKeyDown() && player.isCrouching();
    }

    private void usePrintout() {
        check("crouch_use_opens_printout_" + stage,
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND).consumesAction());
        minecraft.options.keyShift.setDown(false);
    }

    private void verifyPrintout(LocalPlayer player, String expectedName) {
        var stack = player.getMainHandItem();
        check("item_identity_and_coordinates_" + stage, stack.is(EIOItems.LOCATION_PRINTOUT.get())
            && count(player, EIOItems.LOCATION_PRINTOUT.get()) == 1
            && LocationPrintoutItem.getSelection(stack).filter(SELECTION::equals).isPresent());
        check("expected_printout_name_" + stage, expectedName.isEmpty() ? !stack.hasCustomHoverName()
            : stack.hasCustomHoverName() && stack.getHoverName().getString().equals(expectedName));
        var lines = stack.getTooltipLines(player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        check("coordinate_and_dimension_tooltip_" + stage, lines.contains("x3161y81z0") && lines.contains("overworld"));
    }

    private void replaceName(CoordinateMenuScreen screen, String name) {
        var box = editBox(screen);
        int length = box.getValue().length();
        box.setCursorPosition(length);
        for (int deleted = 0; deleted < length; deleted++) {
            if (!screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0)) throw new AssertionError("Could not erase the printout name");
        }
        for (char letter : name.toCharArray()) {
            if (!screen.charTyped(letter, 0)) throw new AssertionError("Could not enter the printout name");
        }
        check("native_name_input_" + stage, box.getValue().equals(name));
    }

    private void close(CoordinateMenuScreen screen) {
        check("native_ok_button_" + stage, screen.mouseClicked(screen.getGuiLeft() + 155, screen.getGuiTop() + 94, 0));
    }

    private static EditBox editBox(CoordinateMenuScreen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_hidden_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_coordinate_printout", result);
    }
}

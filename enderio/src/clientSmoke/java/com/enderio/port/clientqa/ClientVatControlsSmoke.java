package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ClientVatControlsSmoke {
    private enum Stage { TITLE, JOIN, PREVIEW, OPEN_QUEUE, QUEUED, PAUSING, PAUSED, RESUMED,
        SWAP_OPEN, SWAP_PAID, SWAP_INPUT, SWAP_FINISHED, DONE }

    private record CatalystSwap(String name, BlockPos vat, int slot, Item original, Item replacement, int hotbar,
                                double multiplier, int expectedPending) { }

    private static final BlockPos PREVIEW = new BlockPos(4476, 80, 8);
    private static final BlockPos QUEUE = new BlockPos(4470, 80, 8);
    private static final ResourceLocation TAGGED_RECIPE = ResourceLocation.fromNamespaceAndPath("port_qa", "tagged_batch");
    private static final ResourceLocation CLOUD_SEED = ResourceLocation.fromNamespaceAndPath("enderio", "fermenting/fluid_cloud_seed_still");
    private static final CatalystSwap SNOWBALL = new CatalystSwap("snowball", new BlockPos(4996, 80, 8), 1,
        Items.BLUE_ICE, Items.SNOWBALL, 0, 0.11, 0);
    private static final CatalystSwap BLUE_ICE = new CatalystSwap("blue-ice", new BlockPos(5004, 80, 8), 0,
        Items.SNOWBALL, Items.BLUE_ICE, 1, 81, 80891);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean baseline = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final boolean backpressure;
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, List<String>> tooltips = new LinkedHashMap<>();
    private final List<String> gatheredTooltip = new ArrayList<>();
    private long titleReady;
    private boolean collecting;
    private boolean tooltipRequested;
    private boolean tooltipRendered;
    private Stage stage = Stage.TITLE;
    private CatalystSwap swap = SNOWBALL;
    private int useAt = -1;
    private int joinRetries;

    ClientVatControlsSmoke(boolean backpressure) {
        this.backpressure = backpressure;
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::gatherTooltip);
        MinecraftForge.EVENT_BUS.addListener(this::renderWithOutputHover);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (backpressure && stage == Stage.JOIN && minecraft.screen instanceof DisconnectedScreen && joinRetries == 0) {
                joinRetries++;
                startConnection();
                return;
            }
            long timeout = backpressure ? 240_000_000_000L : 145_000_000_000L;
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > timeout) {
                throw new AssertionError("VAT controls client stopped in " + stage);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN && minecraft.screen == null) {
                if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("real_dedicated_survival_connection", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                if (backpressure) {
                    signal("connected");
                    stage = Stage.SWAP_OPEN;
                } else {
                    open(PREVIEW);
                    stage = Stage.PREVIEW;
                }
            } else if (stage == Stage.SWAP_OPEN && minecraft.screen == null) {
                if (!Files.exists(output.resolve("server-" + swap.name + "-open"))
                    || player.distanceToSqr(Vec3.atBottomCenterOf(swap.vat.south(3))) > 0.15) return;
                if (useAt < 0) useAt = player.tickCount + 8;
                if (player.tickCount < useAt) return;
                useAt = -1;
                open(swap.vat);
                stage = Stage.SWAP_PAID;
            } else if (stage == Stage.OPEN_QUEUE && minecraft.screen == null) {
                open(QUEUE);
                stage = Stage.QUEUED;
            } else if (minecraft.screen instanceof VatScreen screen) {
                if (backpressure) inspectSwap(screen.getMenu());
                else inspect(screen);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void inspectSwap(VatMenu menu) throws IOException {
        var player = minecraft.player;
        var recipe = menu.getRecipe();
        var catalyst = menu.getSlot(swap.slot).getItem();
        var prismarine = menu.getSlot(1 - swap.slot).getItem();
        var input = menu.getInputTank().contents();
        var product = menu.getOutputTank().contents();
        switch (stage) {
            case SWAP_PAID -> {
                if (menu.getCraftingProgress() != 1 || product.getAmount() != 8000
                    || !taggedItem(catalyst, swap.original, "PortVatOriginal")) return;
                check(swap.name + "_restores_completed_cloud_seed_job_with_unspent_reagents",
                    recipe != null && CLOUD_SEED.equals(recipe.getId()) && prismarine.is(Items.PRISMARINE_SHARD)
                        && input.getFluid() == Fluids.WATER && input.getAmount() == 1234
                        && menu.getPendingOutput().isEmpty());
                capture(swap.name + "-paid");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, swap.slot, swap.hotbar, ClickType.SWAP, player);
                stage = Stage.SWAP_INPUT;
            }
            case SWAP_INPUT -> {
                if (!taggedItem(catalyst, swap.replacement, "PortVatReplacement")
                    || !taggedItem(player.getInventory().getItem(swap.hotbar), swap.original, "PortVatOriginal")) return;
                check(swap.name + "_preview_uses_the_replacement_in_its_actual_slot", recipe != null
                    && menu.getCraftingProgress() == 1 && recipe.getSlotModifier(minecraft.level, menu.getSlot(0).getItem(),
                        menu.getSlot(1).getItem(), swap.slot) == swap.multiplier);
                capture(swap.name + "-swapped");
                signal(swap.name + "-swapped");
                stage = Stage.SWAP_FINISHED;
            }
            case SWAP_FINISHED -> {
                if (!Files.exists(output.resolve("server-" + swap.name + "-finished"))) return;
                boolean correctPending = swap.expectedPending == 0 ? menu.getPendingOutput().isEmpty() : pending(menu, swap.expectedPending);
                checks.put(swap.name + "_menu_syncs_the_committed_yield_and_exact_input_balance", correctPending
                    && catalyst.isEmpty() && prismarine.isEmpty() && input.getAmount() == 234 && input.getFluid() == Fluids.WATER
                    && menu.getCraftingProgress() == (swap.expectedPending == 0 ? 0 : 1) && recipe == null
                    && product.getAmount() == 8000 && product.getFluid() == EIOFluids.CLOUD_SEED.source().get());
                capture(swap.name + "-finished");
                player.closeContainer();
                signal(swap.name + "-done");
                if (swap == BLUE_ICE) finish(null);
                else {
                    swap = BLUE_ICE;
                    stage = Stage.SWAP_OPEN;
                }
            }
            default -> { }
        }
    }

    private static boolean taggedItem(ItemStack stack, Item item, String tag) {
        return stack.is(item) && stack.getCount() == 1 && stack.hasTag() && stack.getTag().getInt(tag) == 234;
    }

    private void inspect(VatScreen screen) throws IOException {
        var menu = screen.getMenu();
        switch (stage) {
            case PREVIEW -> {
                var recipe = menu.getRecipe();
                if (recipe == null || !TAGGED_RECIPE.equals(recipe.getId()) || menu.getCraftingProgress() < 1) return;
                var left = menu.getSlot(0).getItem();
                var right = menu.getSlot(1).getItem();
                if (!left.is(Items.GOLD_INGOT) || !right.is(Items.COPPER_INGOT)) return;
                check("native_vat_menu_shows_reversed_reagents_and_blocked_recipe", menu.getInputTank().contents().getAmount() == 1000);
                capture("reversed-reagents");
                if (baseline) {
                    finish(null);
                } else {
                    check("reversed_slots_use_their_actual_reagent_multipliers",
                        recipe.getSlotModifier(minecraft.level, left, right, 0) == 0.75
                            && recipe.getSlotModifier(minecraft.level, left, right, 1) == 1.5);
                    minecraft.player.closeContainer();
                    stage = Stage.OPEN_QUEUE;
                }
            }
            case QUEUED -> {
                if (menu.getOutputTank().contents().getAmount() != 8000 || !pending(menu, 57000)) return;
                check("queued_blue_ice_product_syncs_at_full_progress", menu.getCraftingProgress() == 1 && menu.getRecipe() == null);
                if (!captureOutputTooltip("queued", "57000", true)) return;
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check("native_redstone_picker_selects_signal_mode", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.PAUSING;
            }
            case PAUSING -> {
                if (menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL || !hasState(menu, MachineState.REDSTONE)
                    || minecraft.level.getBlockState(QUEUE).getValue(ProgressMachineBlock.POWERED)) return;
                signal("paused");
                stage = Stage.PAUSED;
            }
            case PAUSED -> {
                if (!Files.exists(output.resolve("server-drained")) || !menu.getOutputTank().contents().isEmpty()) return;
                check("paused_empty_tank_keeps_queue_and_reports_redstone_only", pending(menu, 57000)
                    && menu.getCraftingProgress() == 1 && hasState(menu, MachineState.REDSTONE)
                    && !hasState(menu, MachineState.FULL_OUTPUT));
                if (!captureOutputTooltip("paused-with-room", "57000", false)) return;
                signal("drained");
                stage = Stage.RESUMED;
            }
            case RESUMED -> {
                if (!Files.exists(output.resolve("server-resumed")) || !pending(menu, 25000) || hasState(menu, MachineState.REDSTONE)
                    || !minecraft.level.getBlockState(QUEUE).getValue(ProgressMachineBlock.POWERED)) return;
                check("signal_resumes_and_updates_queue_on_open_menu", menu.getCraftingProgress() == 1
                    && hasState(menu, MachineState.FULL_OUTPUT) && menu.getOutputTank().contents().getAmount() == 8000);
                if (!captureOutputTooltip("resumed", "25000", true)) return;
                finish(null);
            }
            default -> { }
        }
    }

    private void renderWithOutputHover(ScreenEvent.Render.Pre event) {
        if (!tooltipRequested || tooltipRendered || !(event.getScreen() instanceof VatScreen screen)) return;
        gatheredTooltip.clear();
        collecting = true;
        try {
            event.setCanceled(true);
            screen.render(event.getGuiGraphics(), screen.getGuiLeft() + 140, screen.getGuiTop() + 28, event.getPartialTick());
        } finally {
            collecting = false;
        }
        tooltipRendered = true;
    }

    private boolean captureOutputTooltip(String name, String amount, boolean hasStoredFluid) throws IOException {
        tooltipRequested = true;
        if (!tooltipRendered) return false;
        tooltipRequested = false;
        tooltipRendered = false;
        tooltips.put(name, List.copyOf(gatheredTooltip));
        check(name + "_native_output_tooltip_reports_the_paid_remainder", gatheredTooltip.contains("Waiting to output " + amount + " mB of Cloud Seed"));
        check(name + "_tooltip_keeps_physical_tank_contents_separate", gatheredTooltip.contains("8000mB") == hasStoredFluid);
        capture(name);
        return true;
    }

    private void gatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (!collecting) return;
        event.getTooltipElements().stream().map(entry -> entry.left()).flatMap(Optional::stream)
            .map(FormattedText::getString).forEach(gatheredTooltip::add);
    }

    private static boolean pending(VatMenu menu, int amount) {
        var fluid = menu.getPendingOutput();
        return fluid.getAmount() == amount && fluid.getFluid() == EIOFluids.CLOUD_SEED.source().get();
    }

    private static boolean hasState(VatMenu menu, MachineState match) {
        return menu.getMachineStates().stream().anyMatch(state -> state.type() == match.type()
            && state.component().getString().equals(match.component().getString()));
    }

    private void open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        startConnection();
    }

    private void startConnection() {
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO VAT controls", address, true), false);
        stage = Stage.JOIN;
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_background_capture", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean.TRUE::equals));
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("tooltips", tooltips);
        if (backpressure) result.put("initialJoinRetries", joinRetries);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_vat_controls", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.DrainScreen;
import com.enderio.enderio.client.foundation.particle.RangeParticle;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

final class ClientDrainSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, PREVIEW, CRAFTED, OPEN_DRAIN, DRAIN, INCREASED, DECREASED, ENABLED,
        COLLECTED, RANGE, RESET_TABLE, RESET_INPUT, RESET_PREVIEW, RESET_CRAFTED, RESET_PLACE, LEGACY_PLACE, VERIFIED, DONE
    }

    private static final BlockPos DRAIN = new BlockPos(2631, 80, 8);
    private static final BlockPos TABLE = new BlockPos(2637, 80, 12);
    private static final BlockPos RESET = new BlockPos(2637, 80, 8);
    private static final BlockPos LEGACY = new BlockPos(2637, 80, 4);
    private static final int CHARGE = 4827;
    private static final int START_FLUID = 1375;
    private static final int END_FLUID = START_FLUID + 1000;
    private static final ResourceLocation RECIPE = ResourceLocation.parse("enderio:drain");
    private static final ResourceLocation ERASE = ResourceLocation.parse("enderio:erase_drain");

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int recipeSubmittedAt;
    private int[] zeroLabel;
    private boolean sawActive;

    ClientDrainSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 220_000_000_000L) {
                throw new AssertionError("Drain scenario stopped in " + stage);
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
                    var models = minecraft.getModelManager();
                    check("all_eight_drain_block_states_and_item_have_models", EIOBlocks.DRAIN.get().getStateDefinition()
                        .getPossibleStates().stream().allMatch(state -> minecraft.getBlockRenderer().getBlockModel(state) != models.getMissingModel())
                        && minecraft.getItemRenderer().getModel(new ItemStack(EIOBlocks.DRAIN.get()), minecraft.level, null, 0)
                        != models.getMissingModel());
                    signal("connected");
                    stage = Stage.TABLE;
                }
                case TABLE -> openWhenReady("server-craft", TABLE, Stage.RECIPE);
                case RECIPE, PREVIEW, CRAFTED, RESET_INPUT, RESET_PREVIEW, RESET_CRAFTED -> craft();
                case OPEN_DRAIN -> openWhenReady("server-drain", DRAIN, Stage.DRAIN);
                case DRAIN, INCREASED, DECREASED, ENABLED, COLLECTED -> operateDrain();
                case RANGE -> {
                    if (!ready("server-range") || !positioned(DRAIN, 6.5) || !hasRangeParticle()) return;
                    check("range_one_particle_uses_the_world_anchor", true);
                    capture("drain-range");
                    signal("overlay");
                    stage = Stage.RESET_TABLE;
                }
                case RESET_TABLE -> openWhenReady("server-reset", TABLE, Stage.RESET_INPUT);
                case RESET_PLACE -> {
                    if (!ready("server-place-reset") || !positioned(RESET, 2.5)) return;
                    if (!placeInventoryDrain(RESET, false)) return;
                    signal("reset-placed");
                    stage = Stage.LEGACY_PLACE;
                }
                case LEGACY_PLACE -> {
                    if (!ready("server-place-legacy") || !positioned(LEGACY, 2.5)) return;
                    if (!placeInventoryDrain(LEGACY, true)) return;
                    signal("legacy-placed");
                    stage = Stage.VERIFIED;
                }
                case VERIFIED -> {
                    if (!ready("server-verified")) return;
                    if (Boolean.FALSE.equals(checks.get("range_value_renders_inside_its_label_cell"))) {
                        throw new AssertionError("The zero and one range labels render identically inside the Drain GUI");
                    }
                    finish(null);
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
            new ServerData("Ender IO Drain", address, true), false);
        stage = Stage.JOIN;
    }

    private void openWhenReady(String marker, BlockPos pos, Stage next) {
        if (!ready(marker) || !positioned(pos, 2.5)) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        stage = next;
    }

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(RECIPE).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                check("void_chassis_unlocks_the_shipped_recipe", true);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                recipeSubmittedAt = minecraft.player.tickCount;
                stage = Stage.PREVIEW;
            }
            case RESET_INPUT -> {
                var recipe = minecraft.level.getRecipeManager().byKey(ERASE).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe) || !configuredDrain(menu.getSlot(37).getItem())) return;
                check("real_drain_loot_carries_charge_fluid_and_controls", true);
                clickSlot(screen, menu.getSlot(37));
                clickSlot(screen, menu.getSlot(1));
                recipeSubmittedAt = minecraft.player.tickCount;
                stage = Stage.RESET_PREVIEW;
            }
            case PREVIEW, RESET_PREVIEW -> {
                var result = menu.getSlot(0).getItem();
                if (!result.is(EIOBlocks.DRAIN.get().asItem())) {
                    if (minecraft.player.tickCount - recipeSubmittedAt > 60) {
                        throw new AssertionError("Missing Drain output in " + stage + ": " + menu.getItems().subList(0, 10));
                    }
                    return;
                }
                boolean erase = stage == Stage.RESET_PREVIEW;
                check(erase ? "native_factory_reset_preview_is_clean" : "native_drain_recipe_preview", result.getCount() == 1 && !result.hasTag());
                capture(erase ? "drain-factory-reset" : "drain-crafting");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
                stage = erase ? Stage.RESET_CRAFTED : Stage.CRAFTED;
            }
            case CRAFTED, RESET_CRAFTED -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem)) return;
                if (minecraft.player.getInventory().items.stream().noneMatch(stack -> stack.is(EIOBlocks.DRAIN.get().asItem())
                    && stack.getCount() == 1 && !stack.hasTag())) return;
                boolean erase = stage == Stage.RESET_CRAFTED;
                check(erase ? "survival_collects_the_clean_reset_drain" : "survival_collects_one_crafted_drain", menu.getCarried().isEmpty());
                minecraft.player.closeContainer();
                signal(erase ? "reset-crafted" : "crafted");
                stage = erase ? Stage.RESET_PLACE : Stage.OPEN_DRAIN;
            }
            default -> { }
        }
    }

    private void operateDrain() throws IOException {
        if (!(minecraft.screen instanceof DrainScreen screen)) return;
        var menu = screen.getMenu();
        var machine = menu.getBlockEntity();
        var fluid = menu.getFluidTank();
        switch (stage) {
            case DRAIN -> {
                if (fluid == null || fluid.contents().getAmount() != START_FLUID || menu.getEnergyStorage() == null
                    || menu.getEnergyStorage().energyStored() != CHARGE || !menu.isCapacitorInstalled()) return;
                check("menu_syncs_exact_lava_charge_capacitor_and_controls", fluid.capacity() == 3000
                    && fluid.contents().getFluid() == Fluids.LAVA && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get())
                    && machine.getActionRange().equals(new ActionRange(0, false)) && menu.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE);
                zeroLabel = labelPixels(screen);
                capture("drain-menu-zero");
                click(screen, 147, 37);
                stage = Stage.INCREASED;
            }
            case INCREASED -> {
                if (machine.getRange() != 1) return;
                checks.put("range_value_renders_inside_its_label_cell", !Arrays.equals(zeroLabel, labelPixels(screen)));
                capture("drain-menu-one");
                click(screen, 147, 45);
                stage = Stage.DECREASED;
            }
            case DECREASED -> {
                if (machine.getRange() != 0) return;
                check("range_controls_send_both_button_packets", true);
                click(screen, 161, 41);
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check("redstone_picker_accepts_gui_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.ENABLED;
            }
            case ENABLED -> {
                if (!menu.isRangeVisible() || menu.getRedstoneControl() != RedstoneControl.ALWAYS_ACTIVE || fluid == null) return;
                sawActive |= machine.getBlockState().getValue(BlockStateProperties.POWERED);
                if (fluid.contents().getAmount() != END_FLUID || menu.getEnergyStorage().energyStored() != CHARGE - 1500) return;
                check("active_state_and_lava_update_arrive_while_menu_is_open", sawActive && fluid.contents().getFluid() == Fluids.LAVA);
                click(screen, 147, 37);
                stage = Stage.COLLECTED;
            }
            case COLLECTED -> {
                if (!machine.getActionRange().equals(new ActionRange(1, true))) return;
                check("range_visibility_survives_the_radius_change", menu.isRangeVisible());
                minecraft.player.closeContainer();
                signal("menu-complete");
                stage = Stage.RANGE;
            }
            default -> { }
        }
    }

    private int[] labelPixels(DrainScreen screen) {
        double scale = minecraft.getWindow().getGuiScale();
        int x = (int) ((screen.getGuiLeft() + 130) * scale);
        int y = (int) ((screen.getGuiTop() + 38) * scale);
        int width = (int) (12 * scale), height = (int) (8 * scale);
        int[] pixels = new int[width * height];
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    pixels[row * width + column] = image.getPixelRGBA(x + column, y + row);
                }
            }
        }
        return pixels;
    }

    private boolean hasRangeParticle() {
        Map<ParticleRenderType, Queue<Particle>> particles = ObfuscationReflectionHelper.getPrivateValue(
            ParticleEngine.class, minecraft.particleEngine, "f_107289_");
        var anchor = Vec3.atLowerCornerOf(DRAIN.below(2));
        return particles != null && particles.values().stream().flatMap(Queue::stream).anyMatch(particle -> {
            if (!(particle instanceof RangeParticle)) return false;
            var bounds = particle.getBoundingBox();
            return bounds.getXsize() == 3 && bounds.getZsize() == 3 && bounds.getCenter().distanceToSqr(anchor) < 0.01;
        });
    }

    private boolean placeInventoryDrain(BlockPos pos, boolean legacy) {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.is(EIOBlocks.DRAIN.get().asItem())) continue;
            var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(stack);
            if (legacy && (fluid == null || fluid.getAmount() != 1000)) continue;
            check(legacy ? "client_receives_the_migrated_drop" : "reset_drain_has_no_carried_state",
                legacy ? fluid.getFluid() == Fluids.LAVA && new ActionRange(1, true).equals(EIODataComponents.ACTION_RANGE.get(stack))
                    && RedstoneControl.ITEM_DATA.get(stack) == RedstoneControl.ACTIVE_WITH_SIGNAL : !stack.hasTag());
            inventory.selected = slot;
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
            check(legacy ? "survival_places_the_migrated_drop" : "survival_places_the_reset_drain",
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
            return true;
        }
        return false;
    }

    private static boolean configuredDrain(ItemStack stack) {
        if (!stack.is(EIOBlocks.DRAIN.get().asItem())) return false;
        var fluid = EIODataComponents.ITEM_FLUID_CONTENT.get(stack);
        return fluid != null && fluid.getFluid() == Fluids.LAVA && fluid.getAmount() == END_FLUID
            && EIODataComponents.ENERGY.getOrDefault(stack, 0) == CHARGE - 1500
            && new ActionRange(1, true).equals(EIODataComponents.ACTION_RANGE.get(stack));
    }

    private void clickSlot(CraftingScreen screen, Slot slot) {
        minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.PICKUP, minecraft.player);
    }

    private void click(DrainScreen screen, int x, int y) {
        check("range_widget_accepts_click_at_" + x + "_" + y,
            screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0));
    }

    private boolean positioned(BlockPos pos, double offset) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(pos.getX() + 0.5, 80, pos.getZ() + offset) < 0.1;
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
        check("silent_hidden_" + name, Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_drain", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.VacuumChestScreen;
import com.enderio.enderio.client.content.machines.gui.screen.XPVacuumScreen;
import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.foundation.particle.RangeParticle;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.vacuum.VacuumMenu;
import com.enderio.enderio.content.machines.vacuum.xp.XPVacuumMenu;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
import java.util.Queue;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ClientVacuumSmoke {
    private enum Stage {
        TITLE, JOIN, CRAFT_TABLE, RECIPE, PREVIEW, CRAFTED, OPEN_MACHINE, MENU, FILTER_OUT, FILTER_IN,
        INCREASED, DECREASED, ACTIVE, COLLECTED, ITEM_OUT, ITEM_IN, RANGE, RESET_TABLE, RESET_INPUT,
        RESET_PREVIEW, RESET_CRAFTED, PLACE, PLACED, DONE
    }

    private record Vacuum(String name, Supplier<? extends Block> blockType, BlockPos pos, BlockPos destination,
                          int range, int rangeX, int plusY, int visibilityX, int visibilityY) {
        Block block() {
            return blockType.get();
        }
    }

    private static final BlockPos TABLE = new BlockPos(2747, 80, 13);
    private final Vacuum[] vacuums = {
        new Vacuum("vacuum_chest", EIOBlocks.VACUUM_CHEST::get, new BlockPos(2740, 80, 8),
            new BlockPos(2743, 80, 5), 1, 163, 89, 143, 112),
        new Vacuum("xp_vacuum", EIOBlocks.XP_VACUUM::get, new BlockPos(2750, 80, 8),
            new BlockPos(2754, 80, 7), 2, 147, 37, 161, 41)
    };
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int machineIndex;
    private int recipeSubmittedAt;
    private int placementReadyTick = -1;

    ClientVacuumSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 225_000_000_000L) {
                throw new AssertionError("Vacuum scenario stopped in " + stage + " for " + vacuums[machineIndex].name());
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            Vacuum vacuum = vacuums[machineIndex];
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.CRAFT_TABLE;
                }
                case CRAFT_TABLE -> openWhenReady(vacuum, "craft", TABLE, Stage.RECIPE);
                case RECIPE, PREVIEW, CRAFTED, RESET_INPUT, RESET_PREVIEW, RESET_CRAFTED -> craft(vacuum);
                case OPEN_MACHINE -> openWhenReady(vacuum, "menu", vacuum.pos(), Stage.MENU);
                case MENU, FILTER_OUT, FILTER_IN, INCREASED, DECREASED, ACTIVE, COLLECTED, ITEM_OUT, ITEM_IN -> operate(vacuum);
                case RANGE -> {
                    if (!ready(vacuum, "range") || !positioned(vacuum.pos(), 6.5) || !hasRangeParticle(vacuum)) return;
                    check(vacuum.name() + "_range_particle_has_expected_world_bounds", true);
                    capture(vacuum, "range");
                    signal(vacuum.name() + "-range");
                    stage = Stage.RESET_TABLE;
                }
                case RESET_TABLE -> openWhenReady(vacuum, "reset", TABLE, Stage.RESET_INPUT);
                case PLACE -> place(vacuum);
                case PLACED -> {
                    if (!ready(vacuum, "placed")) return;
                    if (machineIndex + 1 < vacuums.length) {
                        machineIndex++;
                        stage = Stage.CRAFT_TABLE;
                    } else {
                        finish(null);
                    }
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Vacuum vacuum) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var item = vacuum.block().asItem();
        switch (stage) {
            case RECIPE, RESET_INPUT -> {
                boolean erase = stage == Stage.RESET_INPUT;
                var recipe = minecraft.level.getRecipeManager().byKey(ResourceLocation.parse(
                    "enderio:" + (erase ? "erase_" : "") + vacuum.name())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                if (erase) {
                    var input = menu.getSlot(37).getItem();
                    if (!input.is(item) || !input.hasTag()) return;
                    check(vacuum.name() + "_configured_loot_reaches_the_crafting_screen",
                        new ActionRange(vacuum.range(), true).equals(EIODataComponents.ACTION_RANGE.get(input)));
                    clickSlot(menu, 37, ClickType.PICKUP);
                    clickSlot(menu, 1, ClickType.PICKUP);
                } else {
                    check(vacuum.name() + "_pulsating_crystal_unlocks_the_recipe", true);
                    minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                }
                recipeSubmittedAt = minecraft.player.tickCount;
                stage = erase ? Stage.RESET_PREVIEW : Stage.PREVIEW;
            }
            case PREVIEW, RESET_PREVIEW -> {
                var result = menu.getSlot(0).getItem();
                if (!result.is(item)) {
                    if (minecraft.player.tickCount - recipeSubmittedAt > 60) {
                        throw new AssertionError("Wrong crafting output for " + vacuum.name() + ": " + menu.getItems().subList(0, 10));
                    }
                    return;
                }
                boolean erase = stage == Stage.RESET_PREVIEW;
                check(vacuum.name() + (erase ? "_reset_preview_is_clean" : "_native_recipe_preview"), result.getCount() == 1 && !result.hasTag());
                if (erase) capture(vacuum, "reset");
                clickSlot(menu, 0, ClickType.QUICK_MOVE);
                stage = erase ? Stage.RESET_CRAFTED : Stage.CRAFTED;
            }
            case CRAFTED, RESET_CRAFTED -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem)
                    || minecraft.player.getInventory().items.stream().noneMatch(stack -> stack.is(item) && stack.getCount() == 1 && !stack.hasTag())) return;
                boolean erase = stage == Stage.RESET_CRAFTED;
                check(vacuum.name() + (erase ? "_survival_collects_one_clean_reset" : "_survival_collects_one_crafted_machine"), menu.getCarried().isEmpty());
                minecraft.player.closeContainer();
                signal(vacuum.name() + (erase ? "-reset" : "-crafted"));
                stage = erase ? Stage.PLACE : Stage.OPEN_MACHINE;
            }
            default -> { }
        }
    }

    private void operate(Vacuum vacuum) throws IOException {
        if (!(minecraft.screen instanceof MachineScreen<?> screen) || !(screen.getMenu() instanceof VacuumMenu<?> menu)) return;
        boolean chest = screen instanceof VacuumChestScreen;
        check(vacuum.name() + "_native_screen", chest ? vacuum.block() == EIOBlocks.VACUUM_CHEST.get() : screen instanceof XPVacuumScreen);
        switch (stage) {
            case MENU -> {
                if (menu.getRange() != vacuum.range() || menu.isRangeVisible() || menu.getRedstoneControl() != RedstoneControl.NEVER_ACTIVE) return;
                if (chest && (!isFilter(menu.getSlot(0).getItem()) || !isIron(menu.getSlot(1).getItem(), 2))) return;
                if (!chest && !xpAmount(menu, 140)) return;
                check(vacuum.name() + "_opening_menu_syncs_legacy_contents", true);
                capture(vacuum, "menu");
                if (chest) {
                    clickSlot(menu, 0, ClickType.QUICK_MOVE);
                    stage = Stage.FILTER_OUT;
                } else {
                    increase(vacuum, screen);
                }
            }
            case FILTER_OUT -> {
                int slot = playerSlot(menu, ClientVacuumSmoke::isFilter);
                if (menu.getSlot(0).hasItem() || slot < 0) return;
                clickSlot(menu, slot, ClickType.QUICK_MOVE);
                stage = Stage.FILTER_IN;
            }
            case FILTER_IN -> {
                if (!isFilter(menu.getSlot(0).getItem()) || playerSlot(menu, ClientVacuumSmoke::isFilter) >= 0) return;
                check("vacuum_chest_shift_click_round_trips_the_original_filter", true);
                increase(vacuum, screen);
            }
            case INCREASED -> {
                if (menu.getRange() != vacuum.range() + 1) return;
                press(screen, vacuum, "decrease", vacuum.rangeX(), vacuum.plusY() + 8);
                stage = Stage.DECREASED;
            }
            case DECREASED -> {
                if (menu.getRange() != vacuum.range()) return;
                check(vacuum.name() + "_both_range_controls_sync", true);
                press(screen, vacuum, "visibility", vacuum.visibilityX(), vacuum.visibilityY());
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check(vacuum.name() + "_redstone_widget_handles_scroll", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.ACTIVE;
            }
            case ACTIVE -> {
                if (!menu.isRangeVisible() || menu.getRedstoneControl() != RedstoneControl.ALWAYS_ACTIVE) return;
                signal(vacuum.name() + "-active");
                stage = Stage.COLLECTED;
            }
            case COLLECTED -> {
                if (chest) {
                    if (!isIron(menu.getSlot(1).getItem(), 5)) return;
                    check("vacuum_chest_matching_pickup_syncs_while_open", true);
                    capture(vacuum, "active");
                    clickSlot(menu, 1, ClickType.QUICK_MOVE);
                    stage = Stage.ITEM_OUT;
                } else {
                    if (!xpAmount(menu, 420)) return;
                    check("xp_vacuum_merged_orb_update_syncs_while_open", true);
                    capture(vacuum, "active");
                    closeMenu(vacuum);
                }
            }
            case ITEM_OUT -> {
                int slot = playerSlot(menu, stack -> isIron(stack, 5));
                if (menu.getSlot(1).hasItem() || slot < 0) return;
                clickSlot(menu, slot, ClickType.QUICK_MOVE);
                stage = Stage.ITEM_IN;
            }
            case ITEM_IN -> {
                if (!isIron(menu.getSlot(1).getItem(), 5) || playerSlot(menu, stack -> isIron(stack, 5)) >= 0) return;
                check("vacuum_chest_shift_click_preserves_all_five_tagged_ingots", true);
                closeMenu(vacuum);
            }
            default -> { }
        }
    }

    private void closeMenu(Vacuum vacuum) throws IOException {
        minecraft.player.closeContainer();
        signal(vacuum.name() + "-menu");
        stage = Stage.RANGE;
    }

    private void increase(Vacuum vacuum, MachineScreen<?> screen) {
        press(screen, vacuum, "increase", vacuum.rangeX(), vacuum.plusY());
        stage = Stage.INCREASED;
    }

    private void place(Vacuum vacuum) throws IOException {
        var stack = minecraft.player.getInventory().getItem(0);
        if (!ready(vacuum, "place") || !positioned(vacuum.destination(), 2.5) || !stack.is(vacuum.block().asItem()) || !stack.hasTag()) {
            placementReadyTick = -1;
            return;
        }
        // Allow the normal movement and held-slot packets to settle after the fixture's teleport.
        if (placementReadyTick < 0) placementReadyTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - placementReadyTick < 6) return;
        placementReadyTick = -1;
        check(vacuum.name() + "_legacy_controls_reach_client",
            new ActionRange(vacuum.range(), true).equals(EIODataComponents.ACTION_RANGE.get(stack))
            && RedstoneControl.ITEM_DATA.get(stack) == RedstoneControl.ACTIVE_WITH_SIGNAL);
        minecraft.player.getInventory().selected = 0;
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(vacuum.destination()), Direction.UP, vacuum.destination().below(), false);
        check(vacuum.name() + "_survival_places_legacy_drop",
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal(vacuum.name() + "-placed");
        stage = Stage.PLACED;
    }

    private static boolean xpAmount(VacuumMenu<?> menu, int amount) {
        if (!(menu instanceof XPVacuumMenu xp)) return false;
        var tank = xp.getFluidTank();
        return tank.capacity() == Integer.MAX_VALUE && tank.contents().getFluid() == EIOFluids.XP_JUICE.source().get()
            && tank.contents().getAmount() == amount;
    }

    private static boolean isFilter(ItemStack stack) {
        var rule = EIODataComponents.ITEM_FILTER.get(stack);
        return stack.is(EIOItems.BASIC_ITEM_FILTER.get()) && rule != null
            && isIron(rule.matches().get(0), 1);
    }

    private static boolean isIron(ItemStack stack, int count) {
        return stack.is(Items.IRON_INGOT) && stack.getCount() == count && stack.getTag() != null
            && stack.getTag().getInt("PortFixture") == 135;
    }

    private static int playerSlot(VacuumMenu<?> menu, Predicate<ItemStack> matcher) {
        for (int slot = 28; slot < menu.slots.size(); slot++) {
            if (matcher.test(menu.getSlot(slot).getItem())) return slot;
        }
        return -1;
    }

    private boolean hasRangeParticle(Vacuum vacuum) {
        Map<ParticleRenderType, Queue<Particle>> particles = ObfuscationReflectionHelper.getPrivateValue(
            ParticleEngine.class, minecraft.particleEngine, "f_107289_");
        var anchor = Vec3.atLowerCornerOf(vacuum.pos());
        return particles != null && particles.values().stream().flatMap(Queue::stream).anyMatch(particle -> {
            if (!(particle instanceof RangeParticle)) return false;
            var bounds = particle.getBoundingBox();
            return bounds.getXsize() == 2 * vacuum.range() + 1 && bounds.getCenter().distanceToSqr(anchor) < 0.01;
        });
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO vacuums", address, true), false);
        stage = Stage.JOIN;
    }

    private void openWhenReady(Vacuum vacuum, String name, BlockPos pos, Stage next) {
        if (!ready(vacuum, name) || !positioned(pos, 2.5)) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        stage = next;
    }

    private boolean positioned(BlockPos pos, double south) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(pos.getX() + 0.5, 80, pos.getZ() + south) < 0.1;
    }

    private void clickSlot(AbstractContainerMenu menu, int slot, ClickType clickType) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, clickType, minecraft.player);
    }

    private void press(MachineScreen<?> screen, Vacuum vacuum, String name, int x, int y) {
        check(vacuum.name() + "_" + name + "_widget_accepts_click", screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0));
    }

    private boolean ready(Vacuum vacuum, String name) {
        return Files.exists(output.resolve("server-" + vacuum.name() + "-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void capture(Vacuum vacuum, String name) throws IOException {
        String file = vacuum.name() + "-" + name;
        var frame = ClientSmoke.capture(file + ".png");
        captures.put(file, frame);
        check(file + "_silent_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_vacuums", result);
    }
}

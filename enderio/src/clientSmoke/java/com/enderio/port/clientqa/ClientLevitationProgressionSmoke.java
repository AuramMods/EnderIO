package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.machines.gui.screen.VatScreen;
import com.enderio.enderio.content.machines.vat.VatBlockEntity;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerBlockEntity;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Exercises native survival inputs against the production recipes and integrated server. */
final class ClientLevitationProgressionSmoke {
    private enum Stage { TITLE, WORLD, FIXTURE, CRAFT, CRAFT_RESULT, CRAFT_TAKEN, FEED_NUTRIENT, OPEN_DEW,
        DEW_INPUTS, DEW_START, DEW_FINISH, TAKE_DEW, RETURN_DEW, OPEN_VAPOR, VAPOR_INPUTS, VAPOR_START,
        REOPEN_VAT, VAPOR_RESUMED, VAPOR_FINISH, OPEN_CHARGER, CHARGE_INSERT, CHARGE_DONE, CHARGE_TAKEN,
        REFUEL, FUELED, CROUCH_ON, CLIMB, CROUCH_OFF, LANDED, STAFF_REOPENED, DONE }

    private static final BlockPos TABLE = new BlockPos(-2, 80, 1);
    private static final BlockPos VAT = new BlockPos(0, 80, 0);
    private static final BlockPos CHARGER = new BlockPos(2, 80, 1);
    private static final ResourceLocation STAFF_RECIPE = ResourceLocation.fromNamespaceAndPath("enderio", "staff_of_levity");
    private static final ResourceLocation DEW_RECIPE = ResourceLocation.fromNamespaceAndPath("enderio", "fermenting/fluid_dew_of_the_void_still");
    private static final ResourceLocation VAPOR_RECIPE = ResourceLocation.fromNamespaceAndPath("enderio", "fermenting/fluid_vapor_of_levity_still");
    private static final int FULL_CHARGE = 10_000;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String worldName = System.getProperty("enderio.clientqa.worldName");
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private CompletableFuture<?> fixture;
    private CompletableFuture<Boolean> authoritative;
    private int nextBucket;
    private int staffSlot;
    private int emptySlot;
    private boolean pouring;
    private float savedProgress;
    private int savedEnergy;
    private int savedVapor;

    ClientLevitationProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 230_000_000_000L) throw new AssertionError("Levitation progression stopped in " + stage);
            if (stage == Stage.TITLE) {
                createWorld();
                return;
            }
            if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return;
            if (minecraft.player.containerMenu instanceof CraftingMenu menu) {
                craft(menu);
            } else if (minecraft.screen instanceof VatScreen screen) {
                ferment(screen.getMenu());
            } else if (minecraft.player.containerMenu instanceof WiredChargerMenu menu) {
                charge(menu);
            } else if (minecraft.screen == null) {
                inWorld();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void createWorld() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleReady == 0) titleReady = System.nanoTime();
        if (System.nanoTime() - titleReady < 2_000_000_000L) return;
        stage = Stage.WORLD;
        var settings = new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(46L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions());
    }

    private void prepareFixture() {
        var server = minecraft.getSingleplayerServer();
        fixture = server.submit(() -> {
            var level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (int x = -4; x <= 4; x++) for (int z = -3; z <= 5; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
            level.setBlockAndUpdate(VAT, EIOBlocks.VAT.get().defaultBlockState());
            level.setBlockAndUpdate(CHARGER, EIOBlocks.WIRED_CHARGER.get().defaultBlockState());
            var charger = (WiredChargerBlockEntity) level.getBlockEntity(CHARGER);
            charger.getInventory().setStackInSlot(charger.getCapacitorSlotIndex(), new ItemStack(EIOItems.BASIC_CAPACITOR.get()));
            charger.getEnergyStorage().setEnergyStored(FULL_CHARGE);
            var player = server.getPlayerList().getPlayers().get(0);
            var inventory = player.getInventory();
            inventory.clearContent();
            for (int slot = 0; slot < 4; slot++) {
                inventory.setItem(slot, new ItemStack(EIOFluids.NUTRIENT_DISTILLATION.bucket().get()));
            }
            inventory.setItem(4, new ItemStack(EIOItems.PULSATING_POWDER.get(), 2));
            inventory.setItem(5, new ItemStack(EIOItems.ENDER_CRYSTAL_POWDER.get()));
            inventory.setItem(6, new ItemStack(EIOItems.PRESCIENT_POWDER.get()));
            inventory.setItem(7, new ItemStack(EIOItems.INFINITY_ROD.get(), 2));
            inventory.setItem(8, new ItemStack(EIOItems.PRESCIENT_CRYSTAL.get()));
            player.connection.teleport(0.5, 80, 3.5, 180, 15);
        });
        stage = Stage.FIXTURE;
    }

    private void inWorld() throws IOException {
        var player = minecraft.player;
        switch (stage) {
            case WORLD -> prepareFixture();
            case FIXTURE -> {
                if (!fixture.isDone() || player.distanceToSqr(0.5, 80, 3.5) > 0.1
                    || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)
                    || !item(8).is(EIOItems.PRESCIENT_CRYSTAL.get()) || !item(7).is(EIOItems.INFINITY_ROD.get())) return;
                fixture.join();
                check("real_integrated_survival_connection", !player.isCreative() && !player.isSpectator()
                    && minecraft.getConnection().getConnection().isMemoryConnection());
                useBlock(TABLE, 0);
                stage = Stage.CRAFT;
            }
            case FEED_NUTRIENT -> pourNutrient();
            case OPEN_DEW -> openVat(Stage.DEW_INPUTS);
            case TAKE_DEW -> {
                if (!item(0).is(Items.BUCKET)) return;
                useBlock(VAT, 0);
                stage = Stage.RETURN_DEW;
            }
            case RETURN_DEW -> {
                if (!item(0).is(EIOFluids.DEW_OF_THE_VOID.bucket().get())) return;
                check("vat_fills_a_native_dew_bucket", item(0).getCount() == 1);
                useBlock(VAT, 0);
                stage = Stage.OPEN_VAPOR;
            }
            case OPEN_VAPOR -> { if (item(0).is(Items.BUCKET)) openVat(Stage.VAPOR_INPUTS); }
            case REOPEN_VAT -> {
                if (!minecraft.level.getBlockState(VAT).is(EIOBlocks.VAT.get()) || player.distanceToSqr(0.5, 80, 3.5) > 1) return;
                openVat(Stage.VAPOR_RESUMED);
            }
            case OPEN_CHARGER -> {
                useBlock(CHARGER, emptySlot);
                stage = Stage.CHARGE_INSERT;
            }
            case REFUEL -> {
                if (ItemStackEnergy.getEnergyStored(item(staffSlot)) != FULL_CHARGE) return;
                useBlock(VAT, staffSlot);
                stage = Stage.FUELED;
            }
            case FUELED -> {
                if (!hasFluid(fuel(item(staffSlot)), EIOFluids.VAPOR_OF_LEVITY.source().get(), 1000)) return;
                check("staff_takes_real_vat_product_directly", ItemStackEnergy.getEnergyStored(item(staffSlot)) == FULL_CHARGE
                    && !item(staffSlot).hasFoil());
                minecraft.options.keyShift.setDown(true);
                stage = Stage.CROUCH_ON;
            }
            case CROUCH_ON, CROUCH_OFF -> {
                if (!player.isCrouching()) return;
                minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                minecraft.options.keyShift.setDown(false);
                stage = stage == Stage.CROUCH_ON ? Stage.CLIMB : Stage.LANDED;
            }
            case CLIMB -> {
                if (player.getY() < 82.25 || !player.hasEffect(MobEffects.LEVITATION)) return;
                check("crafted_and_fueled_staff_levitates_in_singleplayer", item(staffSlot).hasFoil());
                capture("staff-levitates");
                minecraft.options.keyShift.setDown(true);
                stage = Stage.CROUCH_OFF;
            }
            case LANDED -> landAndSave();
            case STAFF_REOPENED -> compareReopened();
            default -> { }
        }
    }

    private void craft(CraftingMenu menu) {
        var player = minecraft.player;
        if (stage == Stage.CRAFT) {
            var recipe = minecraft.level.getRecipeManager().byKey(STAFF_RECIPE).orElseThrow();
            // Allow the ordinary inventory-changed trigger and recipe packet to run before checking the book.
            if (player.tickCount < 40) return;
            check("prescient_crystal_unlocks_actual_staff_recipe", player.getRecipeBook().contains(recipe));
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
            stage = Stage.CRAFT_RESULT;
        }
        if (stage == Stage.CRAFT_RESULT && menu.getSlot(0).getItem().is(EIOItems.LEVITATION_STAFF.get())) {
            check("native_crafting_outputs_one_staff", menu.getSlot(0).getItem().getCount() == 1);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
            stage = Stage.CRAFT_TAKEN;
        }
        if (stage != Stage.CRAFT_TAKEN || menu.getSlot(0).hasItem()) return;
        for (int slot = 7; slot < 9; slot++) {
            if (!item(slot).is(EIOItems.LEVITATION_STAFF.get())) continue;
            staffSlot = slot;
            emptySlot = slot == 7 ? 8 : 7;
            check("staff_uses_two_rods_and_one_crystal", item(emptySlot).isEmpty() && ItemStackEnergy.getEnergyStored(item(staffSlot)) == 0);
            player.closeContainer();
            stage = Stage.FEED_NUTRIENT;
            return;
        }
    }

    private void pourNutrient() {
        if (!pouring) {
            if (!item(nextBucket).is(EIOFluids.NUTRIENT_DISTILLATION.bucket().get())) return;
            useBlock(VAT, nextBucket);
            pouring = true;
        } else if (item(nextBucket).is(Items.BUCKET)) {
            pouring = false;
            if (++nextBucket == 4) stage = Stage.OPEN_DEW;
        }
    }

    private void ferment(VatMenu menu) throws IOException {
        switch (stage) {
            case DEW_INPUTS -> {
                if (menu.getInputTank() == null || menu.getInputTank().contents().getAmount() != 4000) return;
                check("vat_menu_receives_four_real_nutrient_buckets", hasFluid(menu.getInputTank().contents(),
                    EIOFluids.NUTRIENT_DISTILLATION.source().get(), 4000));
                swap(menu, 0, 5);
                swap(menu, 1, 4);
                stage = Stage.DEW_START;
            }
            case DEW_START -> {
                if (menu.getCraftingProgress() < 0.05) return;
                check("reversed_reagents_match_the_shipped_dew_recipe", menu.getRecipe() != null && DEW_RECIPE.equals(menu.getRecipe().getId()));
                capture("dew-fermenting");
                stage = Stage.DEW_FINISH;
            }
            case DEW_FINISH -> {
                if (!hasFluid(menu.getOutputTank().contents(), EIOFluids.DEW_OF_THE_VOID.source().get(), 1000)) return;
                check("vat_exchanges_exact_nutrient_and_reagents_for_dew", menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().is(EIOItems.PULSATING_POWDER.get()) && menu.getSlot(1).getItem().getCount() == 1
                    && menu.getInputTank().contents().isEmpty());
                minecraft.player.closeContainer();
                stage = Stage.TAKE_DEW;
            }
            case VAPOR_INPUTS -> {
                if (menu.getInputTank() == null || !hasFluid(menu.getInputTank().contents(), EIOFluids.DEW_OF_THE_VOID.source().get(), 1000)) return;
                swap(menu, 0, 6);
                stage = Stage.VAPOR_START;
            }
            case VAPOR_START -> {
                if (menu.getCraftingProgress() < 0.25) return;
                check("vapour_uses_the_shipped_second_recipe", menu.getRecipe() != null && VAPOR_RECIPE.equals(menu.getRecipe().getId()));
                savedProgress = menu.getCraftingProgress();
                observations.put("preSaveFermentationProgress", savedProgress);
                capture("vapor-before-save");
                reopen(Stage.REOPEN_VAT);
            }
            case VAPOR_RESUMED -> {
                if (menu.getInputTank() == null || menu.getOutputTank() == null) return;
                if (menu.getRecipe() == null) return;
                var progress = menu.getCraftingProgress();
                observations.put("reopenedFermentationProgress", progress);
                check("disk_reopen_resumes_the_in_progress_vapor_batch", VAPOR_RECIPE.equals(menu.getRecipe().getId())
                    && progress >= savedProgress && progress < 1);
                stage = Stage.VAPOR_FINISH;
            }
            case VAPOR_FINISH -> {
                if (!hasFluid(menu.getOutputTank().contents(), EIOFluids.VAPOR_OF_LEVITY.source().get(), 1000)) return;
                check("resumed_vapor_batch_consumes_reagents_and_completes_once", menu.getInputTank().contents().isEmpty()
                    && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty());
                capture("vapor-produced");
                minecraft.player.closeContainer();
                stage = Stage.OPEN_CHARGER;
            }
            default -> { }
        }
    }

    private void charge(WiredChargerMenu menu) {
        switch (stage) {
            case CHARGE_INSERT -> {
                if (!menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()) || !item(staffSlot).is(EIOItems.LEVITATION_STAFF.get())) return;
                swap(menu, 1, staffSlot);
                stage = Stage.CHARGE_DONE;
            }
            case CHARGE_DONE -> {
                var charged = menu.getSlot(2).getItem();
                if (!charged.is(EIOItems.LEVITATION_STAFF.get()) || ItemStackEnergy.getEnergyStored(charged) != FULL_CHARGE) return;
                check("wired_charger_supplies_10000_fe_to_the_crafted_staff", !menu.getSlot(1).hasItem() && fuel(charged).isEmpty());
                swap(menu, 2, staffSlot);
                stage = Stage.CHARGE_TAKEN;
            }
            case CHARGE_TAKEN -> {
                if (menu.getSlot(2).hasItem() || ItemStackEnergy.getEnergyStored(item(staffSlot)) != FULL_CHARGE) return;
                minecraft.player.closeContainer();
                stage = Stage.REFUEL;
            }
            default -> { }
        }
    }

    private void landAndSave() {
        var staff = item(staffSlot);
        if (!minecraft.player.onGround() || staff.hasFoil() || minecraft.player.hasEffect(MobEffects.LEVITATION)) return;
        savedEnergy = ItemStackEnergy.getEnergyStored(staff);
        savedVapor = fuel(staff).getAmount();
        check("manual_midflight_toggle_stops_with_exact_paired_cost", savedEnergy < FULL_CHARGE && savedVapor > 0
            && FULL_CHARGE - savedEnergy == 1000 - savedVapor);
        observations.put("staffRemainingEnergy", savedEnergy);
        observations.put("staffRemainingVapor", savedVapor);
        authoritative = null;
        reopen(Stage.STAFF_REOPENED);
    }

    private void compareReopened() throws IOException {
        if (!item(staffSlot).is(EIOItems.LEVITATION_STAFF.get())) return;
        if (authoritative == null) {
            var server = minecraft.getSingleplayerServer();
            authoritative = server.submit(() -> {
                var staff = server.getPlayerList().getPlayers().get(0).getInventory().getItem(staffSlot);
                var charger = (WiredChargerBlockEntity) server.overworld().getBlockEntity(CHARGER);
                var vat = (VatBlockEntity) server.overworld().getBlockEntity(VAT);
                return !staff.hasFoil() && ItemStackEnergy.getEnergyStored(staff) == savedEnergy
                    && hasFluid(fuel(staff), EIOFluids.VAPOR_OF_LEVITY.source().get(), savedVapor)
                    && charger.getEnergyStorage().getEnergyStored() == 0 && vat.getOutputTank().isEmpty();
            });
            return;
        }
        if (!authoritative.isDone()) return;
        check("disk_reopen_preserves_staff_balances_and_empty_sources", authoritative.join()
            && !item(staffSlot).hasFoil() && ItemStackEnergy.getEnergyStored(item(staffSlot)) == savedEnergy
            && hasFluid(fuel(item(staffSlot)), EIOFluids.VAPOR_OF_LEVITY.source().get(), savedVapor));
        capture("staff-reopened");
        finish(null);
    }

    private void reopen(Stage next) {
        minecraft.player.closeContainer();
        minecraft.level.disconnect();
        minecraft.clearLevel(new TitleScreen());
        check("integrated_server_stops_before_" + next.name().toLowerCase(java.util.Locale.ROOT), minecraft.getSingleplayerServer() == null);
        stage = next;
        minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), worldName);
    }

    private void openVat(Stage next) {
        useBlock(VAT, emptySlot);
        stage = next;
    }

    private void useBlock(BlockPos pos, int slot) {
        minecraft.player.getInventory().selected = slot;
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.5), Direction.SOUTH, pos, false));
    }

    private void swap(AbstractContainerMenu menu, int menuSlot, int hotbarSlot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, hotbarSlot, ClickType.SWAP, minecraft.player);
    }

    private ItemStack item(int slot) {
        return minecraft.player.getInventory().getItem(slot);
    }

    private static FluidStack fuel(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElseThrow().getFluidInTank(0);
    }

    private static boolean hasFluid(FluidStack stack, Fluid fluid, int amount) {
        return stack.getFluid() == fluid && stack.getAmount() == amount;
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        System.out.println("QA levitation progression " + name + " " + passed);
        if (!passed) throw new AssertionError(name);
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        check(name + "_silent_background_capture", Boolean.TRUE.equals(frame.get("passed")));
        captures.put(name, frame);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("observations", observations);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_levitation_progression", result);
    }
}

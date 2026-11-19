package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.content.machines.wired_charger.WiredChargerMenu;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientInfinityRodProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, STORE, COLLECT, OPEN_CHARGER, CHARGE_INSERT,
        CHARGE_INPUT, CHARGE_OUTPUT, CHARGE_TAKEN, OPEN_TANK, TANK_INSERT, TANK_FINISH, TANK_TAKEN, FLIGHT, CROUCH, RISE, LAND, VERIFY, DONE }

    private static final BlockPos CHEST = new BlockPos(3032, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3031, 80, 8);
    private static final BlockPos CHARGER = new BlockPos(3036, 80, 8);
    private static final BlockPos TANK = new BlockPos(3040, 80, 8);
    private static final Vec3 FLIGHT = new Vec3(3043.5, 80, 11.5);
    private static final int CHARGED = 10_000;
    private static final int FUEL = 60;
    private static final String STAFF = "staff_of_levity";
    private static final Craft ROD = Craft.shaped("infinity_rod", 1, " NGNSNGN ", Map.of(
        'G', "grains_of_infinity", 'N', "dark_steel_nugget", 'S', "minecraft:stick"));
    private static final List<Craft> RECIPES = List.of(
        new Craft("dark_steel_nugget", "dark_steel_nugget", "dark_steel_ingot", 1, "dark_steel_nugget", 9),
        ROD, ROD, Craft.shaped(STAFF, 1, "  C R R  ", Map.of('C', "prescient_crystal", 'R', "infinity_rod")));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private Stage observedStage = Stage.TITLE;
    private long stageAt = started;
    private long titleAt;
    private int actionAt = -1;
    private int recipeIndex;
    private int resultBefore;
    private int staffSlot;
    private Map<String, Integer> inputsBefore = Map.of();

    ClientInfinityRodProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            long now = System.nanoTime();
            if (stage != observedStage) {
                observedStage = stage;
                stageAt = now;
            }
            if (minecraft.screen instanceof DisconnectedScreen || now - started > 148_000_000_000L
                || (stage.ordinal() > Stage.JOIN.ordinal() && now - stageAt > 40_000_000_000L)) {
                String screen = minecraft.screen == null ? "world" : minecraft.screen.getClass().getSimpleName();
                throw new AssertionError("Infinity Rod progression stopped at " + stage + ", recipe " + recipeIndex + ", screen " + screen);
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
                    var recipe = minecraft.level.getRecipeManager().byKey(id(ROD.recipe())).orElseThrow();
                    check("the_rod_recipe_awaits_the_stock_fire_drops", !player.getRecipeBook().contains(recipe)
                        && count("grains_of_infinity") == 0);
                    signal("connected");
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!settled("workshop", stand(TABLE))) return;
                    check("native_workshop_chest", open(CHEST));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 3) return;
                    for (int slot = 0; slot < 3; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("the_survival_player_has_the_fire_drops_alloys_and_bound_crystal",
                        count("grains_of_infinity") >= 4 && count("dark_steel_ingot") == 2 && count("prescient_crystal") == 1);
                    player.closeContainer();
                    check("native_crafting_table", open(TABLE));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, STORE, COLLECT -> craft(RECIPES.get(recipeIndex));
                case OPEN_CHARGER -> {
                    if (!settled("charger", stand(CHARGER)) || !equipFreshStaff()) return;
                    selectEmptyHand();
                    check("native_wired_charger_menu", open(CHARGER));
                    stage = Stage.CHARGE_INSERT;
                }
                case CHARGE_INSERT, CHARGE_INPUT, CHARGE_OUTPUT, CHARGE_TAKEN -> charge();
                case OPEN_TANK -> {
                    if (!settled("tank", stand(TANK))) return;
                    selectEmptyHand();
                    check("native_fluid_tank_menu", open(TANK));
                    stage = Stage.TANK_INSERT;
                }
                case TANK_INSERT, TANK_FINISH, TANK_TAKEN -> refuel();
                case FLIGHT -> {
                    if (!settled("flight", FLIGHT) || !player.onGround()) return;
                    player.getInventory().selected = staffSlot;
                    minecraft.options.keyShift.setDown(true);
                    stage = Stage.CROUCH;
                }
                case CROUCH -> {
                    if (!player.isCrouching()) return;
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    minecraft.options.keyShift.setDown(false);
                    stage = Stage.RISE;
                }
                case RISE -> {
                    if (player.getY() < 82.5 || !player.hasEffect(MobEffects.LEVITATION)) return;
                    var stack = staff();
                    int remaining = fuel(stack).getAmount();
                    if (!stack.hasFoil() || remaining <= 0 || remaining >= FUEL) return;
                    check("the_actively_levitating_staff_syncs_foil_and_exact_per_tick_costs",
                        hasTaggedVapor(stack, remaining) && CHARGED - ItemStackEnergy.getEnergyStored(stack) == FUEL - remaining);
                    signal("rising");
                    capture("ascent-with-original-staff");
                    stage = Stage.LAND;
                }
                case LAND -> {
                    if (!idle(staff(), CHARGED - FUEL, 0) || !player.onGround() || player.hasEffect(MobEffects.LEVITATION)) return;
                    check("the_original_staff_lands_on_fuel_exhaustion_with_9940_fe", player.getY() == 80.0);
                    signal("landed");
                    stage = Stage.VERIFY;
                }
                case VERIFY -> {
                    if (ready("done")) finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft step) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        String name = recipeIndex + "_" + step.name();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                inputsBefore = new LinkedHashMap<>();
                for (String ingredient : step.inputs().keySet()) inputsBefore.put(ingredient, count(ingredient));
                resultBefore = count(step.result());
                check(name + "_naturally_unlocks_with_original_inputs", grid.stream().noneMatch(Slot::hasItem)
                    && step.inputs().entrySet().stream().allMatch(entry -> inputsBefore.get(entry.getKey()) >= entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(step.result())) || grid.stream().filter(Slot::hasItem).count() != step.ingredientCount()) return;
                check(name + "_uses_the_stock_grid_and_yield", step.matchesGrid(grid) && stack.getCount() == step.results());
                if (recipeIndex == 1 || step.result().equals(STAFF)) capture(step.result() + "-recipe");
                if (step.result().equals(STAFF)) check("the_stock_recipe_returns_one_empty_inactive_staff", idle(stack, 0, 0));
                pickup(menu, 0);
                stage = Stage.STORE;
            }
            case STORE -> {
                var crafted = menu.getCarried();
                if (!crafted.is(item(step.result())) || crafted.getCount() != step.results()) return;
                var inventory = minecraft.player.getInventory();
                var empty = menu.slots.stream().filter(slot -> slot.container == inventory && !slot.hasItem()).findFirst().orElseThrow();
                pickup(menu, empty.index);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(step.result()) != resultBefore + step.results()
                    || !menu.getCarried().isEmpty() || menu.getSlot(0).hasItem()) return;
                if (!step.inputs().entrySet().stream().allMatch(entry ->
                    count(entry.getKey()) == inputsBefore.get(entry.getKey()) - entry.getValue())) return;
                check(name + "_spends_the_exact_original_ingredients", true);
                if (++recipeIndex == RECIPES.size()) {
                    minecraft.player.closeContainer();
                    signal("crafted");
                    stage = Stage.OPEN_CHARGER;
                } else stage = Stage.RECIPE;
            }
            default -> {}
        }
    }

    private void charge() throws IOException {
        if (!(minecraft.player.containerMenu instanceof WiredChargerMenu menu)) return;
        switch (stage) {
            case CHARGE_INSERT -> {
                if (!menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()) || !idle(staff(), 0, 0)) return;
                swap(menu, 1, staffSlot);
                stage = Stage.CHARGE_INPUT;
            }
            case CHARGE_INPUT -> {
                if (!idle(menu.getSlot(1).getItem(), 0, 0) || !staff().isEmpty()) return;
                signal("charger-inserted");
                stage = Stage.CHARGE_OUTPUT;
            }
            case CHARGE_OUTPUT -> {
                if (!idle(menu.getSlot(2).getItem(), CHARGED, 0)) return;
                check("native_charger_outputs_the_one_original_full_staff", !menu.getSlot(1).hasItem() && staff().isEmpty());
                swap(menu, 2, staffSlot);
                stage = Stage.CHARGE_TAKEN;
            }
            case CHARGE_TAKEN -> {
                if (menu.getSlot(2).hasItem() || !idle(staff(), CHARGED, 0)) return;
                minecraft.player.closeContainer();
                signal("charged");
                stage = Stage.OPEN_TANK;
            }
            default -> {}
        }
    }

    private void refuel() throws IOException {
        if (!(minecraft.player.containerMenu instanceof FluidTankMenu menu)) return;
        switch (stage) {
            case TANK_INSERT -> {
                if (!idle(staff(), CHARGED, 0)) return;
                swap(menu, 2, staffSlot);
                stage = Stage.TANK_FINISH;
            }
            case TANK_FINISH -> {
                var filled = menu.getSlot(2).getItem();
                if (!idle(filled, CHARGED, FUEL) || !hasTaggedVapor(filled, FUEL)) return;
                check("native_tank_fills_the_same_staff_and_keeps_it_in_the_partial_input_slot", !menu.getSlot(3).hasItem() && staff().isEmpty());
                capture("original-staff-refueled");
                swap(menu, 2, staffSlot);
                stage = Stage.TANK_TAKEN;
            }
            case TANK_TAKEN -> {
                if (menu.getSlot(2).hasItem() || !idle(staff(), CHARGED, FUEL)) return;
                minecraft.player.closeContainer();
                signal("fueled");
                stage = Stage.FLIGHT;
            }
            default -> {}
        }
    }

    private boolean equipFreshStaff() {
        var player = minecraft.player;
        int slot = player.getInventory().findSlotMatchingItem(new ItemStack(EIOItems.LEVITATION_STAFF.get()));
        if (slot < 0) throw new AssertionError("Missing the crafted Staff of Levity");
        if (slot >= 9) {
            swap(player.inventoryMenu, slot, 0);
            return false;
        }
        staffSlot = slot;
        return true;
    }

    private ItemStack staff() {
        return minecraft.player.getInventory().getItem(staffSlot);
    }

    private void selectEmptyHand() {
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!inventory.getItem(slot).isEmpty()) continue;
            inventory.selected = slot;
            return;
        }
        throw new AssertionError("No empty hotbar slot for accessing the machine");
    }

    private static FluidStack fuel(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElseThrow().getFluidInTank(0);
    }

    private static boolean idle(ItemStack stack, int energy, int amount) {
        return stack.is(EIOItems.LEVITATION_STAFF.get()) && !stack.hasFoil()
            && ItemStackEnergy.getEnergyStored(stack) == energy && fuel(stack).getAmount() == amount;
    }

    private static boolean hasTaggedVapor(ItemStack stack, int amount) {
        var vapor = fuel(stack);
        return vapor.getFluid() == EIOFluids.VAPOR_OF_LEVITY.source().get() && vapor.getAmount() == amount
            && vapor.getTag() != null && vapor.getTag().getString("PortBatch").equals("infinity_rod");
    }

    private int count(String name) {
        return minecraft.player.getInventory().countItem(item(name));
    }

    private static Vec3 stand(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, 80, 11.5);
    }

    private boolean open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private boolean settled(String name, Vec3 position) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(position) > 0.15) {
            actionAt = -1;
            return false;
        }
        if (actionAt < 0) actionAt = player.tickCount + 8;
        if (player.tickCount < actionAt) return false;
        actionAt = -1;
        return true;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Infinity Rod Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private void pickup(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, minecraft.player);
    }

    private void swap(AbstractContainerMenu menu, int slot, int hotbar) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, hotbar, ClickType.SWAP, minecraft.player);
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_hidden_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_infinity_rod_progression", result);
    }
}

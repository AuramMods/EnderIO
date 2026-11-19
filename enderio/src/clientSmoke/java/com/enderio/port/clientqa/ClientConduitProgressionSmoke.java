package com.enderio.port.clientqa;

import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMenu;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class ClientConduitProgressionSmoke {
    private enum Stage { TITLE, JOIN, TABLE, RECIPE, WRONG_VARIANT, RESULT, TAKEN, SMELTER, SWAP, CAPACITY, UPGRADE, UPGRADED, FINISH, DONE }

    private record Craft(String id, String item, int count, String variant, String lowerVariant, String alloy) {
        Craft(String capacitor) { this(capacitor, capacitor, 1, "", "", ""); }

        Craft(String recipe, String variant) { this(recipe, "conduit", 8, variant, "", ""); }

        Craft(String recipe, String variant, String lowerVariant, String alloy) {
            this(recipe, "conduit", 8, variant, lowerVariant, alloy);
        }

        boolean matches(ItemStack stack) {
            return stack.is(ClientConduitProgressionSmoke.item(item)) && (variant.isEmpty() || conduit(stack, variant));
        }
    }

    private static final BlockPos SMELTER = new BlockPos(3040, 80, 8);
    private static final List<Craft> ENERGY_RECIPES = List.of(new Craft("double_layer_capacitor"), new Craft("double_layer_capacitor"),
        new Craft("octadic_capacitor"), new Craft("enhanced_energy_conduit", "enhanced_energy"),
        new Craft("enhanced_energy_conduit_upgrade", "enhanced_energy", "energy", "energetic_alloy_ingot"),
        new Craft("ender_energy_conduit", "ender_energy"),
        new Craft("ender_energy_conduit_upgrade", "ender_energy", "enhanced_energy", "vibrant_alloy_ingot"));
    private static final List<Craft> ITEM_RECIPES = List.of(new Craft("item_conduit", "item"),
        new Craft("energetic_item_conduit", "enhanced_item"),
        new Craft("energetic_item_conduit_upgrade", "enhanced_item", "item", "energetic_alloy_ingot"),
        new Craft("vibrant_item_conduit", "ender_item"),
        new Craft("vibrant_item_conduit_upgrade", "ender_item", "enhanced_item", "vibrant_alloy_ingot"));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean items;
    private final BlockPos table;
    private final BlockPos left;
    private final BlockPos right;
    private final List<Craft> recipes;
    private final List<String> tiers;
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int availableAt = -1;
    private int recipeIndex;
    private int upgradeIndex;
    private int requestedAt;
    private int beforeCraft;
    private int beforeUpper;
    private int beforeLower;

    ClientConduitProgressionSmoke(boolean items) {
        this.items = items;
        table = items ? new BlockPos(3278, 80, 12) : new BlockPos(3031, 80, 8);
        left = items ? new BlockPos(3279, 80, 8) : new BlockPos(3039, 80, 9);
        right = left.east();
        recipes = items ? ITEM_RECIPES : ENERGY_RECIPES;
        tiers = items ? List.of("item", "enhanced_item", "ender_item") : List.of("energy", "enhanced_energy", "ender_energy");
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 165_000_000_000L) {
                throw new AssertionError("Conduit progression stopped at " + stage + ", recipe " + recipeIndex + ", upgrade " + upgradeIndex);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Conduit progression QA", address, true), false);
                stage = Stage.JOIN;
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
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!ready("table") || !at(table.getX() + 0.5, table.getZ() + 2.5) || !settled()) return;
                    open(table);
                    requestedAt = player.tickCount;
                    stage = Stage.RECIPE;
                }
                case RECIPE, WRONG_VARIANT, RESULT, TAKEN -> craft(recipes.get(recipeIndex));
                case SMELTER, SWAP, CAPACITY -> installCapacitor();
                case UPGRADE, UPGRADED -> replaceConduit();
                case FINISH -> {
                    if (!ready("done")) return;
                    capture("upgraded-conduit-route");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft step) throws IOException {
        var player = minecraft.player;
        if (player.tickCount - requestedAt > 130) throw new AssertionError("Crafting stalled at " + stage + " for " + step.id());
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        String name = step.id() + "_" + recipeIndex;
        switch (stage) {
            case RECIPE -> {
                beforeCraft = count(step::matches);
                if (step.lowerVariant().isEmpty()) {
                    var recipe = minecraft.level.getRecipeManager().byKey(id(step.id())).orElseThrow();
                    if (!player.getRecipeBook().contains(recipe)) return;
                    minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                    stage = Stage.RESULT;
                } else {
                    distribute(menu, stack -> stack.is(item("conduit_binder")), 1, 2, 3, 7, 8, 9);
                    distribute(menu, stack -> stack.is(item(step.alloy())), 4, 6);
                    distribute(menu, stack -> conduit(stack, step.variant()), 5);
                    stage = Stage.WRONG_VARIANT;
                }
                requestedAt = player.tickCount;
            }
            case WRONG_VARIANT -> {
                if (player.tickCount - requestedAt < 8 || !conduit(menu.getSlot(5).getItem(), step.variant())) return;
                check(name + "_rejects_the_wrong_tagged_variant", !menu.getSlot(0).hasItem());
                var wrongSource = playerSlot(menu, stack -> conduit(stack, step.variant()));
                click(menu, 5, 0);
                click(menu, wrongSource.index, 0);
                distribute(menu, stack -> conduit(stack, step.lowerVariant()), 5);
                requestedAt = player.tickCount;
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var result = menu.getSlot(0).getItem();
                if (!step.matches(result)) return;
                check(name + "_has_the_exact_native_result", result.getCount() == step.count());
                if (result.is(EIOItems.DOUBLE_LAYER_CAPACITOR.get())) {
                    check(name + "_has_the_tier_two_capacitor_default", CapacitorData.ITEM_DATA.get(result).base() == 2);
                }
                if (items) {
                    int rate = 32 << tiers.indexOf(step.variant());
                    String expected = "Effective Rate: " + rate + " Items/sec";
                    check(name + "_displays_its_effective_transfer_rate", result.getTooltipLines(player, TooltipFlag.NORMAL)
                        .stream().anyMatch(line -> line.getString().equals(expected)));
                }
                if (!step.lowerVariant().isEmpty()) {
                    if (!conduit(menu.getSlot(5).getItem(), step.lowerVariant())) return;
                    capture(step.id());
                }
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                requestedAt = player.tickCount;
                stage = Stage.TAKEN;
            }
            case TAKEN -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem)
                    || count(step::matches) != beforeCraft + step.count()) return;
                check(name + "_native_pickup_is_exact", menu.getCarried().isEmpty());
                recipeIndex++;
                requestedAt = player.tickCount;
                if (recipeIndex == recipes.size()) {
                    player.closeContainer();
                    signal("crafted");
                    stage = items ? Stage.UPGRADE : Stage.SMELTER;
                } else stage = Stage.RECIPE;
            }
            default -> { }
        }
    }

    private void installCapacitor() throws IOException {
        if (stage == Stage.SMELTER) {
            if (!ready("smelter") || !at(3039.5, 11.5) || !settled()) return;
            open(SMELTER);
            stage = Stage.SWAP;
            return;
        }
        if (!(minecraft.player.containerMenu instanceof AlloySmelterMenu menu)) return;
        if (stage == Stage.SWAP) {
            if (!menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()) || menu.getEnergyStorage().getEnergyStored() != 16000) return;
            var source = playerSlot(menu, stack -> stack.is(EIOItems.OCTADIC_CAPACITOR.get()));
            click(menu, source.index, 0);
            click(menu, 0, 0);
            click(menu, source.index, 0);
            stage = Stage.CAPACITY;
        } else if (menu.getSlot(0).getItem().is(EIOItems.OCTADIC_CAPACITOR.get())
            && menu.getEnergyStorage().getMaxEnergyStored() == 576_000) {
            check("octadic_capacitor_gives_ninefold_capacity_and_preserves_charge",
                menu.getEnergyStorage().getEnergyStored() == 16000 && menu.getCarried().isEmpty()
                    && count(stack -> stack.is(EIOItems.BASIC_CAPACITOR.get())) == 1);
            capture("octadic-smelter-capacity");
            minecraft.player.closeContainer();
            signal("capacitor-installed");
            stage = Stage.UPGRADE;
        }
    }

    private void replaceConduit() throws IOException {
        int tier = upgradeIndex / 2 + (items ? 0 : 1);
        String upper = tiers.get(tier);
        String lower = tier == 0 ? "" : tiers.get(tier - 1);
        BlockPos target = upgradeIndex % 2 == 0 ? left : right;
        if (stage == Stage.UPGRADE) {
            if (!ready("upgrade-" + upgradeIndex) || minecraft.screen != null) return;
            if (items && (!at(right.getX() + 0.5, right.getZ() + 3.5) || !settled())) return;
            var inventory = minecraft.player.getInventory();
            boolean found = false;
            for (int slot = 0; slot < 9; slot++) {
                if (conduit(inventory.getItem(slot), upper)) {
                    inventory.selected = slot;
                    found = true;
                    break;
                }
            }
            check("upgrade_" + upgradeIndex + "_uses_a_crafted_upper_tier", found);
            beforeUpper = count(stack -> conduit(stack, upper));
            if (tier == 0) {
                var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
                check("placement_" + upgradeIndex + "_uses_the_crafted_basic_conduit",
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
            } else {
                beforeLower = count(stack -> conduit(stack, lower));
                open(target);
            }
            stage = Stage.UPGRADED;
        } else if (minecraft.level.getBlockEntity(target) instanceof ConduitBundleBlockEntity bundle
            && bundle.getConduits().stream().anyMatch(holder -> holder.is(id(upper)))
            && count(stack -> conduit(stack, upper)) == beforeUpper - 1
            && (tier == 0 || count(stack -> conduit(stack, lower)) == beforeLower + 1)) {
            check(tier == 0 ? "placement_" + upgradeIndex + "_consumes_one_basic"
                : "upgrade_" + upgradeIndex + "_consumes_one_upper_and_returns_one_lower", true);
            signal("upgraded-" + upgradeIndex);
            upgradeIndex++;
            stage = upgradeIndex == (items ? 6 : 4) ? Stage.FINISH : Stage.UPGRADE;
        }
    }

    private void distribute(AbstractContainerMenu menu, Predicate<ItemStack> test, int... grid) {
        var from = playerSlot(menu, test);
        click(menu, from.index, 0);
        for (int target : grid) click(menu, target, 1);
        if (!menu.getCarried().isEmpty()) click(menu, from.index, 0);
    }

    private Slot playerSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
        return menu.slots.stream().filter(slot -> slot.container == minecraft.player.getInventory()
            && predicate.test(slot.getItem())).findFirst().orElseThrow();
    }

    private void click(AbstractContainerMenu menu, int slot, int button) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.PICKUP, minecraft.player);
    }

    private void open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private boolean at(double x, double z) {
        return minecraft.screen == null && minecraft.player.distanceToSqr(x, 80, z) < 0.15;
    }

    private boolean settled() {
        int tick = minecraft.player.tickCount;
        if (availableAt < 0) availableAt = tick + 8;
        if (tick < availableAt) return false;
        availableAt = -1;
        return true;
    }

    private int count(Predicate<ItemStack> predicate) {
        return minecraft.player.getInventory().items.stream().filter(predicate).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean conduit(ItemStack stack, String variant) {
        return stack.is(EIOItems.CONDUIT.get()) && id(variant).equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("enderio", path);
    }

    private boolean ready(String label) {
        return Files.exists(output.resolve("server-" + label));
    }

    private void signal(String label) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(label + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, dir.resolve(label + ".json"));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void capture(String label) throws IOException {
        var frame = ClientSmoke.capture(label + ".png");
        captures.put(label, frame);
        check(label + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete(items ? "client_item_progression" : "client_energy_progression", result);
    }
}

package com.enderio.port.clientqa;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.client.content.machines.gui.screen.SoulBinderScreen;
import com.enderio.enderio.content.machines.soul_binder.SoulBinderMenu;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import java.util.function.Supplier;

final class ClientSoulComponentSmoke {
    private enum Stage { TITLE, JOIN, OPEN, RESTORED, COMPLETE, TAKE, VERIFY, DONE }

    private record Binding(String name, int x, EntityType<?> mob, Supplier<Item> input, Supplier<Item> output) {
        BlockPos position() { return new BlockPos(x, 78, 8); }
    }

    private static final List<Binding> BINDINGS = List.of(
        new Binding("frank_n_zombie", 3163, EntityType.ZOMBIE, EIOItems.Z_LOGIC_CONTROLLER::get, EIOItems.FRANK_N_ZOMBIE::get),
        new Binding("sentient_ender", 3195, EntityType.WITCH, EIOItems.ENDER_RESONATOR::get, EIOItems.SENTIENT_ENDER::get));
    private static final int PRODUCT_SLOT = 3;
    private static final int VIAL_SLOT = 4;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;
    private int useAt = -1;

    ClientSoulComponentSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 180_000_000_000L) {
                throw new AssertionError("Soul component progression stopped at " + stage + ", binder " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (minecraft.level == null || player == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("initial_survival_inventory_is_empty", player.getInventory().isEmpty());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> open(BINDINGS.get(index));
                case RESTORED, COMPLETE, TAKE -> {
                    if (minecraft.screen instanceof SoulBinderScreen screen) inspect(BINDINGS.get(index), screen.getMenu());
                }
                case VERIFY -> {
                    if (!ready("finished")) return;
                    check("both_original_products_and_two_empty_vials_are_in_survival_inventory",
                        BINDINGS.stream().allMatch(binding -> player.getInventory().countItem(binding.output().get()) == 1)
                            && player.getInventory().countItem(EIOItems.SOUL_VIAL.get()) == 2
                            && player.getInventory().items.stream().filter(stack -> stack.is(EIOItems.SOUL_VIAL.get()))
                                .allMatch(ClientSoulComponentSmoke::emptyVial));
                    finish(null);
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
            new ServerData("Ender IO Soul Components", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(Binding binding) {
        var player = minecraft.player;
        if (!ready("open-" + binding.name()) || minecraft.screen != null
            || player.distanceToSqr(binding.x() + 0.5, 78, 11.5) > 0.15) {
            useAt = -1;
            return;
        }
        // Let the fixture teleport finish before sending the actual interaction.
        if (useAt < 0) useAt = player.tickCount + 8;
        if (player.tickCount < useAt) return;
        useAt = -1;
        BlockPos pos = binding.position();
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check(binding.name() + "_opens_the_native_binder", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
        stage = Stage.RESTORED;
    }

    private void inspect(Binding binding, SoulBinderMenu menu) throws IOException {
        var player = minecraft.player;
        var inputVial = menu.getSlot(1).getItem();
        var ingredient = menu.getSlot(2).getItem();
        var product = menu.getSlot(PRODUCT_SLOT).getItem();
        var vial = menu.getSlot(VIAL_SLOT).getItem();
        switch (stage) {
            case RESTORED -> {
                if (menu.getCraftingProgress() == 0 || menu.getFluidTank() == null || inputVial.isEmpty() || ingredient.isEmpty()) return;
                check(binding.name() + "_restores_original_inputs_paid_progress_and_xp",
                    menu.isCapacitorInstalled() && menu.getEnergyStorage().energyStored() == 0
                        && ClientSoulBinderSmoke.fluidIs(menu, 837) && Math.abs(menu.getCraftingProgress() - 12300f / 51200) < 0.00001f
                        && inputVial.is(EIOItems.SOUL_VIAL.get()) && inputVial.getCount() == 1
                        && EIODataComponents.SOUL.getOrDefault(inputVial, Soul.EMPTY).entityType() == binding.mob()
                        && ingredient.is(binding.input().get()) && ingredient.getCount() == 1 && product.isEmpty() && vial.isEmpty());
                check(binding.name() + "_displays_four_xp_levels", menu.getExperience() == 4);
                capture(binding.name() + "-restored");
                signal("ready-" + binding.name());
                stage = Stage.COMPLETE;
            }
            case COMPLETE -> {
                if (!ready("complete-" + binding.name()) || product.isEmpty() || vial.isEmpty()
                    || !ClientSoulBinderSmoke.fluidIs(menu, 37)) return;
                check(binding.name() + "_syncs_the_product_empty_vial_and_exact_remainders",
                    product.is(binding.output().get()) && product.getCount() == 1 && emptyVial(vial) && vial.getCount() == 1
                        && menu.getEnergyStorage().energyStored() == 317 && menu.getCraftingProgress() == 0
                        && inputVial.isEmpty() && ingredient.isEmpty());
                capture(binding.name() + "-complete");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, PRODUCT_SLOT, 0, ClickType.QUICK_MOVE, player);
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, VIAL_SLOT, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKE;
            }
            case TAKE -> {
                if (!product.isEmpty() || !vial.isEmpty() || player.getInventory().countItem(binding.output().get()) != 1
                    || player.getInventory().countItem(EIOItems.SOUL_VIAL.get()) != index + 1) return;
                check(binding.name() + "_native_quick_moves_clear_both_slots", menu.getCarried().isEmpty());
                player.closeContainer();
                signal("extracted-" + binding.name());
                stage = ++index == BINDINGS.size() ? Stage.VERIFY : Stage.OPEN;
            }
            default -> {}
        }
    }

    private static boolean emptyVial(ItemStack stack) {
        return stack.is(EIOItems.SOUL_VIAL.get()) && EIODataComponents.SOUL.getOrDefault(stack, Soul.EMPTY).isEmpty();
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
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_soul_components", result);
    }
}

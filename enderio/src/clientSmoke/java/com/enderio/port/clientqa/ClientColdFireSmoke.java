package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
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

final class ClientColdFireSmoke {
    private enum Stage { TITLE, JOIN, TABLE, RECIPE, RESULT, COLLECT, FILL, FILLED, TRY, OBSERVE, EXTINGUISH, CLEARED, DONE, STOPPED }

    private record Attempt(String name, BlockPos clicked, Direction face, int before, int after) {}

    private static final BlockPos TABLE = new BlockPos(3190, 80, 10);
    private static final BlockPos TANK = new BlockPos(3188, 80, 9);
    private static final BlockPos FIRST = new BlockPos(3190, 80, 8);
    private static final BlockPos SECOND = new BlockPos(3192, 80, 8);
    private static final List<Attempt> FIRST_ATTEMPTS = List.of(
        new Attempt("unsupported", new BlockPos(3189, 82, 9), Direction.WEST, 25, 25),
        new Attempt("occupied", new BlockPos(3191, 80, 9), Direction.UP, 25, 25),
        new Attempt("first", FIRST, Direction.UP, 25, 15));
    private static final List<Attempt> SECOND_ATTEMPTS = List.of(
        new Attempt("second", SECOND, Direction.UP, 15, 5),
        new Attempt("insufficient", new BlockPos(3192, 80, 10), Direction.UP, 5, 5));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final boolean legacy;
    private final List<Attempt> attempts = restarted ? SECOND_ATTEMPTS : FIRST_ATTEMPTS;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int attemptIndex;
    private int observeAfter;

    ClientColdFireSmoke(boolean legacy) {
        this.legacy = legacy;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.STOPPED) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 148_000_000_000L) {
                throw new AssertionError("Cold Fire stopped at " + stage + ", attempt " + attemptIndex);
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
                    stage = restarted || legacy ? Stage.TRY : Stage.TABLE;
                }
                case TABLE -> {
                    if (!marker("craft") || player.distanceToSqr(3190.5, 80, 11.5) > 0.1
                        || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    check("native_crafting_table_use", use(TABLE, Direction.SOUTH).consumesAction());
                    stage = Stage.RECIPE;
                }
                case RECIPE -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(EIOItems.COLD_FIRE_IGNITER.getId()).orElseThrow();
                    if (!player.getRecipeBook().contains(recipe)) return;
                    check("stock_igniter_recipe_is_unlocked", true);
                    minecraft.gameMode.handlePlaceRecipe(screen.getMenu().containerId, recipe, false);
                    stage = Stage.RESULT;
                }
                case RESULT -> {
                    if (!(minecraft.screen instanceof CraftingScreen screen)) return;
                    var menu = screen.getMenu();
                    var grid = menu.slots.subList(1, 10);
                    if (!menu.getSlot(0).getItem().is(EIOItems.COLD_FIRE_IGNITER.get())
                        || grid.stream().filter(Slot::hasItem).count() != 2) return;
                    check("stock_result_uses_dark_steel_and_flint", menu.getSlot(0).getItem().getCount() == 1
                        && grid.stream().anyMatch(slot -> slot.getItem().is(EIOItems.DARK_STEEL_INGOT.get()))
                        && grid.stream().anyMatch(slot -> slot.getItem().is(Items.FLINT)));
                    minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                    stage = Stage.COLLECT;
                }
                case COLLECT -> {
                    if (!selectIgniter(player) || !(minecraft.screen instanceof CraftingScreen screen)
                        || screen.getMenu().slots.subList(0, 10).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    stage = Stage.FILL;
                }
                case FILL -> {
                    if (minecraft.screen != null || !minecraft.level.getBlockState(TANK).is(EIOBlocks.FLUID_TANK.get())) return;
                    check("crafted_igniter_starts_empty", fuel(player.getMainHandItem()).isEmpty());
                    check("native_fluid_tank_interaction", use(TANK, Direction.SOUTH).consumesAction());
                    stage = Stage.FILLED;
                }
                case FILLED -> {
                    if (fuel(player.getMainHandItem()).getAmount() != 25) return;
                    verifyFuel(player, "filled", 25);
                    signal("filled");
                    stage = Stage.TRY;
                }
                case TRY -> {
                    var attempt = attempts.get(attemptIndex);
                    if (!marker(attempt.name()) || player.distanceToSqr(3190.5, 80, 11.5) > 0.1 || !selectIgniter(player)
                        || fuel(player.getMainHandItem()).getAmount() != attempt.before()) return;
                    verifyFuel(player, attempt.name() + "_before", attempt.before());
                    boolean succeeds = attempt.before() != attempt.after();
                    check(attempt.name() + "_native_use_result", use(attempt.clicked(), attempt.face()).consumesAction() == succeeds);
                    observeAfter = player.tickCount + 5;
                    stage = Stage.OBSERVE;
                }
                case OBSERVE -> {
                    var attempt = attempts.get(attemptIndex);
                    if (player.tickCount < observeAfter || fuel(player.getMainHandItem()).getAmount() != attempt.after()) return;
                    var state = minecraft.level.getBlockState(attempt.clicked().relative(attempt.face()));
                    if (attempt.before() != attempt.after()) {
                        if (!state.is(EIOBlocks.COLD_FIRE.get())) return;
                        capture(attempt.name());
                    } else {
                        check(attempt.name() + "_unchanged_target", "occupied".equals(attempt.name()) ? state.is(Blocks.GLASS) : state.isAir());
                    }
                    verifyFuel(player, attempt.name() + "_after", attempt.after());
                    signal(attempt.name());
                    if (++attemptIndex < attempts.size()) stage = Stage.TRY;
                    else stage = restarted ? Stage.EXTINGUISH : Stage.DONE;
                }
                case EXTINGUISH -> {
                    if (!marker("extinguish") || !minecraft.level.getBlockState(SECOND.above()).is(EIOBlocks.COLD_FIRE.get())) return;
                    check("survival_click_extinguishes_mature_cold_fire", minecraft.gameMode.startDestroyBlock(SECOND.above(), Direction.SOUTH));
                    stage = Stage.CLEARED;
                }
                case CLEARED -> {
                    if (!minecraft.level.getBlockState(SECOND.above()).isAir()) return;
                    signal("extinguished");
                    stage = Stage.DONE;
                }
                case DONE -> {
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
            new ServerData("Ender IO Cold Fire", address, true), false);
        stage = Stage.JOIN;
    }

    private InteractionResult use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }

    private static boolean selectIgniter(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (!player.getInventory().getItem(slot).is(EIOItems.COLD_FIRE_IGNITER.get())) continue;
            player.getInventory().selected = slot;
            return true;
        }
        return false;
    }

    private static FluidStack fuel(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
            .map(tank -> tank.getFluidInTank(0)).orElse(FluidStack.EMPTY);
    }

    private void verifyFuel(LocalPlayer player, String name, int amount) {
        var stack = player.getMainHandItem();
        var fluid = fuel(stack);
        check(name + "_exact_tagged_vapor", fluid.getFluid() == EIOFluids.VAPOR_OF_LEVITY.source().get()
            && fluid.getAmount() == amount && fluid.getTag() != null && fluid.getTag().getInt("PortBatch") == 180);
        check(name + "_client_tooltip_matches", stack.getTooltipLines(player, TooltipFlag.NORMAL).stream().map(Component::getString)
            .anyMatch(line -> line.equals(amount + " / 1000 Vapor of Levity")));
        if (legacy) {
            var tag = stack.getTag();
            check(name + "_legacy_tool_identity_matches", tag != null && tag.getInt("PortLegacy") == 181
                && !tag.contains("Fluid") && stack.getHoverName().getString().equals("Archive Igniter"));
        }
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + "-cold-fire.png");
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
        stage = Stage.STOPPED;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_cold_fire", result);
    }
}

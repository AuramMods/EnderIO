package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.AlloySmelterScreen;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMenu;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMode;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientAlloyBackpressureSmoke {
    private enum Stage { TITLE, JOIN, OPEN, PAID, SWAPPED, CLEARED, SETTLED, DONE }

    private record Job(String name, BlockPos smelter, AlloySmelterMode mode, int inputSlot, Item input,
                       int originalCount, Item product, int newOutputCount) { }

    private static final int OUTPUT = AlloySmelterMenu.LAST_INDEX;
    private static final Job ALLOY = new Job("alloy", new BlockPos(4962, 80, 8), AlloySmelterMode.ALLOYS,
        AlloySmelterMenu.INPUTS_INDEX + 2, Items.EGG, 6, Items.RED_DYE, 0);
    private static final Job FURNACE = new Job("furnace", new BlockPos(4970, 80, 8), AlloySmelterMode.FURNACE,
        AlloySmelterMenu.INPUTS_INDEX, Items.RAW_IRON, 3, Items.IRON_INGOT, 1);

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path evidence = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private Job job = ALLOY;
    private long titleAt;

    ClientAlloyBackpressureSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Alloy Smelter stopped during " + job.name + " " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Alloy Smelter QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.OPEN;
                return;
            }
            if (stage == Stage.OPEN) {
                if (!ready("open") || minecraft.screen != null
                    || player.distanceToSqr(Vec3.atBottomCenterOf(job.smelter.south(3))) > 0.15) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(job.smelter).relative(Direction.SOUTH, 0.5),
                    Direction.SOUTH, job.smelter, false);
                check(job.name + "_opens_the_native_menu", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                stage = Stage.PAID;
                return;
            }
            if (minecraft.screen instanceof AlloySmelterScreen screen) inspect(screen.getMenu());
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void inspect(AlloySmelterMenu menu) throws IOException {
        var player = minecraft.player;
        var input = menu.getSlot(job.inputSlot).getItem();
        var output = menu.getSlot(OUTPUT).getItem();
        switch (stage) {
            case PAID -> {
                if (menu.getCraftingProgress() != 1 || !output.is(job.product) || output.getCount() != 64
                    || !input.is(job.input) || input.getCount() != job.originalCount || menu.getMode() != job.mode) return;
                check(job.name + "_is_fully_paid", menu.getEnergyStorage().getEnergyStored() == 0);
                capture("paid");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, job.inputSlot, 0, ClickType.SWAP, player);
                stage = Stage.SWAPPED;
            }
            case SWAPPED -> {
                var original = player.getInventory().getItem(0);
                if (!original.is(job.input) || original.getCount() != job.originalCount
                    || (job == ALLOY ? !input.isEmpty() : !input.is(Items.RAW_IRON) || input.getCount() != 1)) return;
                if (!ready("clear")) {
                    if (!Files.exists(evidence.resolve("signals/" + job.name + "-swapped.json"))) signal(job.name + "-swapped");
                    return;
                }
                check(job.name + "_keeps_the_tagged_original_input_and_full_output", original.hasTag()
                    && original.getTag().getInt("PortAlloy") == 232 && output.is(job.product) && output.getCount() == 64);
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, OUTPUT, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.CLEARED;
            }
            case CLEARED -> {
                if (player.getInventory().items.stream().noneMatch(stack -> stack.is(job.product) && stack.getCount() == 64)) return;
                signal(job.name + "-cleared");
                stage = Stage.SETTLED;
            }
            case SETTLED -> {
                if (!ready("settled")) return;
                checks.put(job.name + "_shows_exact_output_after_the_swap", menu.getCraftingProgress() == 0
                    && menu.getEnergyStorage().getEnergyStored() == 0
                    && (job.newOutputCount == 0 ? output.isEmpty() : output.is(job.product) && output.getCount() == job.newOutputCount));
                capture("settled");
                player.closeContainer();
                signal(job.name + "-done");
                if (job == FURNACE) {
                    finish(null);
                } else {
                    job = FURNACE;
                    stage = Stage.OPEN;
                }
            }
            default -> { }
        }
    }

    private boolean ready(String name) {
        return Files.exists(evidence.resolve("server-" + job.name + "-" + name));
    }

    private void signal(String name) throws IOException {
        var dir = evidence.resolve("signals");
        Files.createDirectories(dir);
        var temp = dir.resolve(name + ".tmp");
        Files.writeString(temp, "{}");
        Files.move(temp, dir.resolve(name + ".json"));
    }

    private void capture(String phase) throws IOException {
        String name = job.name + "-" + phase;
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean.TRUE::equals));
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_alloy_backpressure", result);
    }
}

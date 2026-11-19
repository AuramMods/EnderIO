package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.SlicerScreen;
import com.enderio.enderio.content.machines.slicer.SlicerMenu;
import com.enderio.enderio.init.EIOBlocks;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

final class ClientLegacySlicerSmoke {
    private enum Stage { TITLE, JOIN, OPEN, MENU, ITEM, PLACED, FINISH, DONE }

    private record Fixture(String label, String recipe, int x, int energy, List<Item> ingredients) {
        private BlockPos pos() { return new BlockPos(x, 80, 8); }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private List<Fixture> fixtures;
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int index;
    private int interactionTick = -1;

    ClientLegacySlicerSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private static List<Fixture> createFixtures() {
        var soularium = EIOItems.SOULARIUM_INGOT.get();
        var energetic = EIOItems.ENERGETIC_ALLOY_INGOT.get();
        var silicon = EIOItems.SILICON.get();
        return List.of(
            new Fixture("original", "z_logic_controller", 3210, 0,
                List.of(soularium, Items.ZOMBIE_HEAD, soularium, silicon, Items.REDSTONE, silicon)),
            new Fixture("legacy", "ender_resonator", 3226, 16400,
                List.of(soularium, EIOBlocks.ENDERMAN_HEAD.get().asItem(), soularium, silicon, EIOItems.VIBRANT_ALLOY_INGOT.get(), silicon)),
            new Fixture("modern", "zombie_electrode", 3242, 16400,
                List.of(energetic, Items.ZOMBIE_HEAD, energetic, silicon, EIOItems.BASIC_CAPACITOR.get(), silicon)));
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 155_000_000_000L) {
                throw new AssertionError("Legacy Slice'N'Splice stopped during " + stage + " at fixture " + index);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Legacy Slice'N'Splice QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (fixtures == null) fixtures = createFixtures();
            var fixture = fixtures.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    String cue = index == 0 ? fixture.label() : fixture.label() + "-menu";
                    if (!ready(cue) || !atFixture(fixture)) return;
                    var pos = fixture.pos();
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
                    check(fixture.label() + "_opens_the_native_slicer_menu",
                        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.MENU;
                }
                case MENU -> {
                    if (!(minecraft.screen instanceof SlicerScreen screen) || !verifyMenu(fixture, screen.getMenu())) return;
                    signal(fixture.label() + "-menu");
                    if (index == fixtures.size() - 1) {
                        stage = Stage.FINISH;
                    } else {
                        player.closeContainer();
                        index++;
                        stage = Stage.ITEM;
                    }
                }
                case ITEM -> {
                    if (!ready(fixture.label()) || player.getInventory().getItem(0).isEmpty() || !atFixture(fixture)) return;
                    place(fixture);
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(fixture.pos()).is(EIOBlocks.SLICE_AND_SPLICE.get())
                        || !player.getMainHandItem().isEmpty()) return;
                    check(fixture.label() + "_survival_placement_spends_the_single_item", !player.getAbilities().instabuild);
                    signal(fixture.label() + "-placed");
                    stage = Stage.OPEN;
                }
                case FINISH -> {
                    if (ready("finished") && finishMenu()) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void place(Fixture fixture) {
        var player = minecraft.player;
        var item = player.getInventory().getItem(0);
        var saved = fixture.label().equals("modern") ? EIODataComponents.SLICER_TASK.get(item) : BlockItem.getBlockEntityData(item);
        var task = saved == null ? null : saved.getCompound("Task");
        check(fixture.label() + "_the_real_drop_contains_the_exact_paid_task", item.is(EIOBlocks.SLICE_AND_SPLICE.get().asItem())
            && item.getCount() == 1 && task != null && task.getInt("ProgressMade") == 3600
            && task.getInt("ProgressRequired") == 20000 && task.getString("RecipeId").equals("enderio:slicing/" + fixture.recipe()));
        player.getInventory().selected = 0;
        var floor = new BlockHitResult(Vec3.atBottomCenterOf(fixture.pos()), Direction.UP, fixture.pos().below(), false);
        check(fixture.label() + "_the_native_placement_packet_accepts_the_carried_machine",
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, floor).consumesAction());
    }

    private boolean verifyMenu(Fixture fixture, SlicerMenu menu) throws IOException {
        if (Math.abs(menu.getCraftingProgress() - 0.18f) >= 0.0001 || menu.getEnergyStorage().getEnergyStored() != fixture.energy()) return false;
        boolean inputs = true;
        for (int slot = 0; slot < fixture.ingredients().size(); slot++) {
            var stack = menu.getSlot(SlicerMenu.INPUTS_INDEX + slot).getItem();
            inputs &= stack.is(fixture.ingredients().get(slot)) && stack.getCount() == 1;
        }
        check(fixture.label() + "_syncs_exact_saved_payment_inventory_and_tools", inputs
            && toolMatches(menu.getSlot(1).getItem(), Items.IRON_AXE, 17)
            && toolMatches(menu.getSlot(2).getItem(), Items.SHEARS, 23) && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get()));
        capture(index == 0 ? "original-slicer" : fixture.label() + "-replanted-slicer");
        return true;
    }

    private boolean finishMenu() throws IOException {
        if (!(minecraft.screen instanceof SlicerScreen screen)) return false;
        var menu = screen.getMenu();
        if (menu.getCraftingProgress() != 0 || menu.getEnergyStorage().getEnergyStored() != 0
            || !toolMatches(menu.getSlot(1).getItem(), Items.IRON_AXE, 18)
            || !toolMatches(menu.getSlot(2).getItem(), Items.SHEARS, 24)) return false;
        check("modern_replanted_menu_syncs_empty_inputs_after_one_craft",
            menu.slots.subList(SlicerMenu.INPUTS_INDEX, SlicerMenu.LAST_INDEX + 1).stream().noneMatch(slot -> slot.hasItem()));
        capture("replanted-slicer-finished");
        minecraft.player.closeContainer();
        return true;
    }

    private static boolean toolMatches(ItemStack stack, Item item, int damage) {
        return stack.is(item) && stack.getCount() == 1 && stack.getDamageValue() == damage
            && stack.hasTag() && stack.getTag().getInt("PortLegacySlicer") == 160;
    }

    private boolean atFixture(Fixture fixture) {
        var player = minecraft.player;
        if (minecraft.screen != null || player.distanceToSqr(fixture.x() + 0.5, 80, 11.5) > 0.15) return false;
        if (interactionTick < 0) interactionTick = player.tickCount + 8;
        if (player.tickCount < interactionTick) return false;
        interactionTick = -1;
        return true;
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        var tmp = dir.resolve(name + ".tmp");
        Files.writeString(tmp, "{}");
        Files.move(tmp, dir.resolve(name + ".json"));
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
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_legacy_slicer", result);
    }
}

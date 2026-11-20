package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Checks the shipped composite model after real placement and in the player's inventory. */
final class ClientEnchanterPresentationSmoke {
    private static final String PAGES = "enderio:block/enchanter";
    private static final String COVER = "minecraft:item/book";
    private static final String STAND = "enderio:block/machine_bottom";
    // Baseline book bounds after both the element tilts and the child transform, in thousandths of a block.
    private static final int[] BOOK_BOUNDS = {182, 646, 191, 818, 962, 703};

    private record View(Direction facing, BlockPos block, Vec3 camera) {
        String name() {
            return facing.getName();
        }
    }

    private static final List<View> VIEWS = List.of(
        new View(Direction.NORTH, new BlockPos(5282, 80, 8), new Vec3(5282.5, 80, 5.5)),
        new View(Direction.EAST, new BlockPos(5290, 80, 8), new Vec3(5293.5, 80, 8.5)),
        new View(Direction.SOUTH, new BlockPos(5298, 80, 8), new Vec3(5298.5, 80, 11.5)),
        new View(Direction.WEST, new BlockPos(5306, 80, 8), new Vec3(5303.5, 80, 8.5)));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean embeddium;
    private boolean connecting;
    private boolean joined;
    private boolean requestedPlacement;
    private boolean inventoried;
    private boolean finished;
    private int viewIndex;
    private int readyTick = -1;
    private List<String> northBook;
    private CompletableFuture<Void> reload;

    ClientEnchanterPresentationSmoke(boolean embeddium) {
        this.embeddium = embeddium;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 155_000_000_000L) {
                throw new AssertionError("Enchanter presentation timed out or disconnected at " + placementState());
            }
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                var address = System.getProperty("enderio.clientqa.server");
                connecting = true;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO Enchanter presentation", address, true), false);
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            if (!joined) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                checks.put("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                checks.put("expected_renderer", ModList.get().isLoaded("embeddium") == embeddium);
                minecraft.options.hideGui = true;
                joined = true;
                signal("connected");
            }
            if (viewIndex < VIEWS.size()) {
                placeAndObserve(VIEWS.get(viewIndex));
            } else if (!inventoried) {
                observeInventory();
            } else if (marker("reload")) {
                observeReload();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void placeAndObserve(View view) throws IOException {
        var player = minecraft.player;
        if (!marker(view.name()) || minecraft.screen != null || player.distanceToSqr(view.camera()) > 0.01
            || player.getDirection().getOpposite() != view.facing()) {
            readyTick = -1;
            return;
        }
        if (!requestedPlacement) {
            var held = player.getInventory().getItem(0);
            if (!held.is(EIOBlocks.ENCHANTER.get().asItem()) || held.getCount() != 5 - viewIndex || !settled(5)) return;
            // The first camera can already match the login position while the teleport is being acknowledged.
            readyTick = -1;
            player.getInventory().selected = 0;
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(view.block()), Direction.UP, view.block().below(), false);
            var result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            checks.put("native_placement_" + view.name(), result.consumesAction());
            requestedPlacement = true;
            if (!result.consumesAction()) throw new AssertionError("Native use returned " + result + " at " + placementState());
            return;
        }
        var state = minecraft.level.getBlockState(view.block());
        if (!state.is(EIOBlocks.ENCHANTER.get()) || state.getValue(BlockStateProperties.HORIZONTAL_FACING) != view.facing()
            || player.getInventory().getItem(0).getCount() != 4 - viewIndex || !settled(20)) return;
        var frame = capture(view.name());
        inspectWorldModel(view, view.name(), frame);
        checkPagePixels(view.name(), frame);
        signal(view.name());
        viewIndex++;
        readyTick = -1;
        requestedPlacement = false;
    }

    private String placementState() {
        if (viewIndex >= VIEWS.size() || minecraft.player == null || minecraft.level == null) {
            return "view " + viewIndex + ", screen=" + minecraft.screen;
        }
        var view = VIEWS.get(viewIndex);
        var player = minecraft.player;
        return view.name() + ", requested=" + requestedPlacement + ", state=" + minecraft.level.getBlockState(view.block())
            + ", player=" + player.position() + ", direction=" + player.getDirection()
            + ", hotbar=" + player.getInventory().getItem(0) + ", screen=" + minecraft.screen;
    }

    private void observeInventory() throws IOException {
        if (!marker("inventory")) return;
        if (!(minecraft.screen instanceof InventoryScreen screen)) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return;
        }
        if (!settled(20)) return;
        var stack = screen.getMenu().getSlot(36).getItem();
        checks.put("one_enchanter_in_actual_hotbar_slot", stack.is(EIOBlocks.ENCHANTER.get().asItem()) && stack.getCount() == 1);
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 42);
        var passes = model.getRenderPasses(stack, true);
        var quads = passes.stream().flatMap(pass -> pass.getQuads(null, null, RandomSource.create(42)).stream()).toList();
        var frame = capture("inventory");
        inspectGeometry("inventory", quads, Direction.NORTH, frame);
        checks.put("inventory_has_separate_item_render_passes", passes.size() == 2);

        var image = ImageIO.read(output.resolve("inventory.png").toFile());
        int[] occupied = slotPixels(image, screen, 36);
        int[] empty = slotPixels(image, screen, 37);
        int changed = 0;
        for (int i = 0; i < occupied.length; i++) {
            if (occupied[i] != empty[i]) changed++;
        }
        frame.put("inventoryItemPixels", changed);
        checks.put("the_enchanter_icon_is_drawn_in_the_hotbar_slot", changed > 20 && screen.getMenu().getSlot(37).getItem().isEmpty());
        minecraft.setScreen(null);
        readyTick = -1;
        inventoried = true;
        signal("inventory");
    }

    private int[] slotPixels(java.awt.image.BufferedImage image, InventoryScreen screen, int index) {
        var slot = screen.getMenu().getSlot(index);
        double scale = minecraft.getWindow().getGuiScale();
        int x = (int) Math.round((screen.getGuiLeft() + slot.x) * scale);
        int y = (int) Math.round((screen.getGuiTop() + slot.y) * scale);
        int size = (int) Math.round(16 * scale);
        return image.getRGB(x, y, size, size, null, 0, size);
    }

    private void observeReload() throws IOException {
        if (reload == null) {
            reload = minecraft.reloadResourcePacks();
            return;
        }
        if (!reload.isDone() || minecraft.getOverlay() != null || !settled(20)) return;
        reload.join();
        var frame = capture("reloaded");
        for (var view : VIEWS) inspectWorldModel(view, "reloaded_" + view.name(), frame);
        checkPagePixels("reloaded", frame);
        signal("reloaded");
        finish(null);
    }

    private void inspectWorldModel(View view, String name, Map<String, Object> frame) {
        BlockState state = minecraft.level.getBlockState(view.block());
        checks.put("actual_block_facing_" + name, state.is(EIOBlocks.ENCHANTER.get())
            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == view.facing());
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(minecraft.level, view.block(), state, ModelData.EMPTY);
        inspectGeometry(name, model.getQuads(state, null, RandomSource.create(42), data, null), view.facing(), frame);
    }

    private void inspectGeometry(String name, List<BakedQuad> quads, Direction facing, Map<String, Object> frame) {
        var geometry = new ArrayList<String>();
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int pages = 0;
        int covers = 0;
        for (var quad : quads) {
            String sprite = quad.getSprite().contents().name().toString();
            if (sprite.equals(PAGES)) pages++;
            else if (sprite.equals(COVER)) covers++;
            else continue;
            for (int index = IQuadTransformer.POSITION; index < quad.getVertices().length; index += IQuadTransformer.STRIDE) {
                int[] point = northVertex(quad.getVertices(), index, facing);
                for (int axis = 0; axis < 3; axis++) {
                    bounds[axis] = Math.min(bounds[axis], point[axis]);
                    bounds[axis + 3] = Math.max(bounds[axis + 3], point[axis]);
                }
                geometry.add(sprite + Arrays.toString(point));
            }
        }
        geometry.sort(String::compareTo);
        if (northBook == null) northBook = List.copyOf(geometry);
        checks.put("book_and_stand_are_textured_" + name, pages == 8 && covers == 4
            && quads.stream().anyMatch(quad -> quad.getSprite().contents().name().toString().equals(STAND)));
        checks.put("mounted_book_matches_baseline_transform_" + name, java.util.stream.IntStream.range(0, bounds.length)
            .allMatch(index -> Math.abs((long) bounds[index] - BOOK_BOUNDS[index]) <= 2));
        checks.put("book_vertices_match_north_after_inverse_rotation_" + name, northBook.equals(geometry));
        frame.put("geometry_" + name, Map.of("pages", pages, "covers", covers, "bookBounds", bounds));
    }

    private static int[] northVertex(int[] vertices, int index, Direction facing) {
        float x = Float.intBitsToFloat(vertices[index]) - 0.5f;
        float y = Float.intBitsToFloat(vertices[index + 1]);
        float z = Float.intBitsToFloat(vertices[index + 2]) - 0.5f;
        return switch (facing) {
            case NORTH -> vertex(x, y, z);
            case EAST -> vertex(z, y, -x);
            case SOUTH -> vertex(-x, y, -z);
            case WEST -> vertex(-z, y, x);
            default -> throw new IllegalArgumentException("Expected a horizontal Enchanter facing");
        };
    }

    private static int[] vertex(float x, float y, float z) {
        return new int[] {Math.round((x + 0.5f) * 1000), Math.round(y * 1000), Math.round((z + 0.5f) * 1000)};
    }

    private void checkPagePixels(String name, Map<String, Object> frame) throws IOException {
        var image = ImageIO.read(output.resolve(name + ".png").toFile());
        int lightPages = 0;
        for (int y = image.getHeight() / 2 - 42; y < image.getHeight() / 2 + 15; y++) {
            for (int x = image.getWidth() / 2 - 50; x < image.getWidth() / 2 + 50; x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255;
                int green = rgb >> 8 & 255;
                int blue = rgb & 255;
                if (red > 150 && Math.abs(red - green) <= 2 && Math.abs(red - blue) <= 2) lightPages++;
            }
        }
        frame.put("drawnPagePixels", lightPages);
        checks.put("light_book_pages_are_drawn_" + name, lightPages > 30);
    }

    private boolean settled(int ticks) {
        if (readyTick == -1) readyTick = minecraft.player.tickCount;
        return minecraft.player.tickCount - readyTick >= ticks;
    }

    private Map<String, Object> capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png", 50);
        captures.put(name, frame);
        checks.put("hidden_muted_" + name, Boolean.TRUE.equals(frame.get("passed")));
        return frame;
    }

    private boolean marker(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var dir = output.resolve("signals");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".json"), "{}");
    }

    private void finish(Throwable failure) {
        finished = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null && checks.values().stream().allMatch(Boolean::booleanValue));
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_enchanter_presentation", result);
    }
}

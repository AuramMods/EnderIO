package com.enderio.port.clientqa;

import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.PaintUtils;
import com.enderio.enderio.content.paint.block.entity.DoublePaintedBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
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

final class ClientPaintedSlabSmoke {
    private record Placement(String name, BlockPos target, BlockPos clicked, Direction face, int slot, Block paint) {}

    private static final BlockPos LEGACY = new BlockPos(2552, 80, 12);
    private static final BlockPos MODERN = new BlockPos(2557, 80, 12);
    private static final List<Placement> PLACEMENTS = List.of(
        new Placement("old-top", LEGACY, LEGACY.above(), Direction.DOWN, 1, Blocks.DIAMOND_BLOCK),
        new Placement("old-bottom", LEGACY, LEGACY, Direction.DOWN, 0, Blocks.GOLD_BLOCK),
        new Placement("new-bottom", MODERN, MODERN.below(), Direction.UP, 2, Blocks.GOLD_BLOCK),
        new Placement("new-top", MODERN, MODERN, Direction.UP, 3, Blocks.DIAMOND_BLOCK));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private boolean connecting;
    private boolean connected;
    private boolean finished;
    private int step;
    private int captureTick = -1;

    ClientPaintedSlabSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 140_000_000_000L) {
                throw new AssertionError("Client failed to complete slab placement step " + step);
            }
            if (!connecting) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO painted slabs", address, true), false);
                connecting = true;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
            if (!connected) {
                check("dedicated_survival_tcp", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                    && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                connected = true;
            }
            if (step < PLACEMENTS.size()) {
                var placement = PLACEMENTS.get(step);
                if (!Files.exists(output.resolve("server-" + placement.name()))
                    || minecraft.player.distanceToSqr(placement.target().getX() + 0.5, 80, 14.5) > 0.1) return;
                if (step == 1 && !hasPaint(LEGACY, SlabType.TOP, null, Blocks.DIAMOND_BLOCK)) return;
                if (step == 3 && !hasPaint(MODERN, SlabType.BOTTOM, Blocks.GOLD_BLOCK, null)) return;
                if (place(placement)) step++;
                return;
            }
            if (!Files.exists(output.resolve("server-verified"))) return;
            if (!hasPaint(LEGACY, SlabType.DOUBLE, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK)
                || !hasPaint(MODERN, SlabType.DOUBLE, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK)) return;
            if (captureTick == -1) captureTick = minecraft.player.tickCount;
            if (minecraft.player.tickCount - captureTick < 20) return;
            check("both_double_slabs_have_exact_client_model_data", true);
            var frame = ClientSmoke.capture("mixed-painted-slabs.png");
            captures.put("mixed-painted-slabs", frame);
            check("silent_hidden_mixed_painted_slabs", Boolean.TRUE.equals(frame.get("passed")));
            finish(null);
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean place(Placement placement) throws IOException {
        var inventory = minecraft.player.getInventory();
        var stack = inventory.getItem(placement.slot());
        if (!stack.is(EIOBlocks.PAINTED_SLAB.get().asItem()) || stack.getCount() != 1) return false;
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
        check(placement.name() + "_item_paint_and_render_pass",
            BlockPaintData.of(placement.paint()).equals(PaintUtils.getItemPaint(stack)) && !model.getRenderPasses(stack, false).isEmpty());
        inventory.selected = placement.slot();
        double height = placement.target().equals(placement.clicked()) ? 0.5 : placement.face() == Direction.UP ? 1 : 0;
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(placement.clicked()).add(0.5, height, 0.5),
            placement.face(), placement.clicked(), false);
        check(placement.name() + "_placement", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        signal(placement.name());
        return true;
    }

    private boolean hasPaint(BlockPos pos, SlabType type, Block lower, Block upper) {
        if (!(minecraft.level.getBlockEntity(pos) instanceof DoublePaintedBlockEntity slab)
            || slab.getBlockState().getValue(SlabBlock.TYPE) != type) return false;
        var data = slab.getModelData();
        return data.get(DoublePaintedBlockEntity.PAINT) == lower && data.get(DoublePaintedBlockEntity.PAINT2) == upper;
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
        finished = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_painted_slabs", result);
    }
}

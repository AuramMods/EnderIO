package com.enderio.enderio.client.foundation.widgets.ioconfig;

import com.enderio.core.client.GuiSprites;
import com.enderio.core.client.gui.screen.BaseOverlay;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.client.foundation.model.ModelRenderUtil;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import com.enderio.enderio.foundation.network.packets.ServerboundCycleIOConfigPacket;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;
import com.enderio.enderio.foundation.network.EIONetwork;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

/**
 * Thanks XFactHD for help and providing a demo for a preview widget and raycast example
 * <a href="https://gist.github.com/XFactHD/4b214f98a1b30a590c6e0de6bd84602a">Preview Widget Gist</a>
 * <p>
 * Definition  of {@link GhostBuffers}, {@link GhostRenderLayer} and initBuffers method are taken from Patchouli (License information: <a href="https://github.com/VazkiiMods/Patchouli">here</a>)
 */
public class IOConfigOverlay extends BaseOverlay {

    private static final Quaternionf ROT_180_Z = Axis.ZP.rotation((float) Math.PI);
    private static final int Z_OFFSET = 100;
    private static final int OVERLAY_Z_OFFSET = 500;
    private static final ResourceLocation IO_CONFIG_OVERLAY = EnderIO.rl("buttons/io_config_overlay");
    private static final ResourceLocation SELECTED_ICON = EnderIO.rl("block/overlay/selected_face");
    private static final Minecraft MINECRAFT = Minecraft.getInstance();
    private static MultiBufferSource.BufferSource ghostBuffers;
    private static MultiBufferSource.BufferSource solidBuffers;
    private Vec3 worldOrigin = Vec3.ZERO;
    private float rayDepth = 1;
    private List<BlockPos> configurable = List.of();
    private final List<BlockPos> neighbours = new ArrayList<>();
    private float scale = 20;
    private float pitch;
    private float yaw;
    private boolean neighbourVisible = true;
    private Optional<SelectedFace> selection = Optional.empty();

    // Neighbour Button
    public static final ResourceLocation NEIGHBOURS_BTN = EnderIO.rl("buttons/neighbour");
    private final Rect2i neighBtnRect;

    public IOConfigOverlay(int x, int y, int width, int height, List<BlockPos> blocks) {
        super(x, y, width, height, Component.empty());
        setConfigurableBlocks(blocks);
        pitch = MINECRAFT.player.getXRot();
        yaw = MINECRAFT.player.getYRot();

        initBuffers();
        neighBtnRect = new Rect2i(getX() + getWidth() - 2 - 16, getY() + getHeight() - 2 - 16, 16, 16);
    }

    public void setConfigurableBlocks(List<BlockPos> blocks) {
        if (configurable.equals(blocks)) {
            return;
        }
        configurable = List.copyOf(blocks);
        selection = Optional.empty();

        var bounds = BoundingBox.encapsulatingPositions(configurable).orElseGet(() -> new BoundingBox(BlockPos.ZERO));
        float spanX = bounds.getXSpan();
        float spanY = bounds.getYSpan();
        float spanZ = bounds.getZSpan();
        worldOrigin = new Vec3(bounds.minX() + spanX / 2.0, bounds.minY() + spanY / 2.0, bounds.minZ() + spanZ / 2.0);
        rayDepth = (float) Math.sqrt(spanX * spanX + spanY * spanY + spanZ * spanZ) / 2 + 1;
        scale = Math.max(10, 20 - (Math.max(Math.max(spanX, spanY), spanZ) - 1) * 3);

        neighbours.clear();
        var seen = new HashSet<>(configurable);
        for (var pos : configurable) {
            for (var side : Direction.values()) {
                var neighbor = pos.relative(side);
                if (seen.add(neighbor)) {
                    neighbours.add(neighbor);
                }
            }
        }
    }

    @Override
    public int getAdditionalZOffset() {
        return OVERLAY_Z_OFFSET;
    }

    @Override
    public Object getValueForRestore() {
        return new RestoreData(this.visible, yaw, pitch, scale);
    }

    @Override
    public void restoreValue(Object value) {
        if (value instanceof RestoreData restoreData) {
            this.visible = restoreData.isVisible;
            this.yaw = restoreData.yaw;
            this.pitch = restoreData.pitch;
            this.scale = restoreData.scale;
        }
    }

    private record RestoreData(boolean isVisible, float yaw, float pitch, float scale) {
    }

    private static void initBuffers() {
        // Forge 1.20.1 builders own their batch state as well as their memory.
        // Reuse separate preview pools instead of borrowing active GUI builders.
        if (ghostBuffers == null) {
            ghostBuffers = new GhostBuffers();
            solidBuffers = new SolidBuffers();
        }
    }

    private Quaternionf previewRotation() {
        return new Quaternionf(ROT_180_Z).mul(Axis.XN.rotationDegrees(pitch)).mul(Axis.YP.rotationDegrees(yaw));
    }

    private Optional<SelectedFace> pickFace(double mouseX, double mouseY, Quaternionf rotation) {
        var level = MINECRAFT.level;
        if (level == null || !isMouseOver(mouseX, mouseY)) {
            return Optional.empty();
        }

        float x = (float) ((mouseX - (getX() + width / 2)) / scale);
        float y = (float) ((mouseY - (getY() + height / 2)) / scale);
        var inverse = new Quaternionf(rotation).conjugate();
        // The preview negates Z, so the front of the view starts at negative depth.
        var from = inverse.transform(new Vector3f(x, y, -rayDepth));
        var to = inverse.transform(new Vector3f(x, y, rayDepth));
        var start = worldOrigin.add(from.x, from.y, from.z);
        var end = worldOrigin.add(to.x, to.y, to.z);

        BlockHitResult closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (var pos : configurable) {
            var hit = level.getBlockState(pos).getShape(level, pos).clip(start, end, pos);
            if (hit != null) {
                double distance = hit.getLocation().distanceToSqr(start);
                if (distance < closestDistance) {
                    closest = hit;
                    closestDistance = distance;
                }
            }
        }
        return closest == null ? Optional.empty() : Optional.of(new SelectedFace(closest.getBlockPos(), closest.getDirection()));
    }

    public void toggleNeighbourVisibility() {
        neighbourVisible = !neighbourVisible;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible) {
            if (button == 0) {
                if (neighBtnRect.contains((int) mouseX, (int) mouseY)) {
                    toggleNeighbourVisibility();
                    this.playDownSound(MINECRAFT.getSoundManager());
                    return true;
                }
            }
            if (button == 1) {
                selection = pickFace(mouseX, mouseY, previewRotation());
                if (selection.isPresent()) {
                    var selectedFace = selection.get();
                    BlockEntity entity = MINECRAFT.level.getBlockEntity(selectedFace.blockPos);
                    if (entity instanceof IOConfigurable ioConfigurable && ioConfigurable.isIOConfigMutable()) {
                        EIONetwork.sendToServer(new ServerboundCycleIOConfigPacket(selectedFace.blockPos, selectedFace.side));
                        this.playDownSound(MINECRAFT.getSoundManager());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (visible && isValidClickButton(button) && isMouseOver(mouseX, mouseY)
                && !neighBtnRect.contains((int) mouseX, (int) mouseY)) {
            double dx = dragX / (double) MINECRAFT.getWindow().getGuiScaledWidth();
            double dy = dragY / (double) MINECRAFT.getWindow().getGuiScaledHeight();
            yaw += 4 * (float) dx * 180;
            pitch += 2 * (float) dy * 180;

            pitch = Math.min(80, Math.max(-80, pitch)); // clamp

        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (visible) {
            scale -= deltaY;
            scale = Math.min(40, Math.max(10, scale)); // clamp
            return true;
        }
        return false;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (visible) {
            guiGraphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
            RenderSystem.disableDepthTest();
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            RenderSystem.enableDepthTest();

            // Calculate widget center
            int centerX = getX() + (width / 2);
            int centerY = getY() + (height / 2);
            Quaternionf blockTransform = previewRotation();
            renderWorld(guiGraphics, centerX, centerY, blockTransform, partialTick);
            selection = pickFace(mouseX, mouseY, blockTransform);
            renderSelection(guiGraphics, centerX, centerY, blockTransform);
            renderOverlay(guiGraphics);

            guiGraphics.disableScissor();

            // after scissor to prevent clipping the tooltip
            renderNeighbourButton(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderWorld(GuiGraphics guiGraphics, int centerX, int centerY, Quaternionf transform,
            float partialTick) {
        guiGraphics.flush();
        Lighting.setupForFlatItems();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, Z_OFFSET);
        guiGraphics.pose().scale(scale, scale, -scale);
        guiGraphics.pose().mulPose(transform);

        // RenderNeighbours
        if (neighbourVisible) {
            for (var neighbour : neighbours) {
                renderBlock(guiGraphics, neighbour, ghostBuffers, partialTick);
            }

        }
        ghostBuffers.endBatch();

        // Render configurable
        for (var configurable : configurable) {
            renderBlock(guiGraphics, configurable, solidBuffers, partialTick);
        }
        solidBuffers.endBatch();

        guiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
    }

    private void renderBlock(GuiGraphics guiGraphics, BlockPos blockPos, MultiBufferSource.BufferSource buffers,
            float partialTick) {
        guiGraphics.pose().pushPose();
        translateToBlock(guiGraphics, blockPos);

        ModelData modelData = Optional.ofNullable(MINECRAFT.level.getModelDataManager().getAt(blockPos))
                .orElse(ModelData.EMPTY);

        BlockState blockState = MINECRAFT.level.getBlockState(blockPos);
        RenderShape rendershape = blockState.getRenderShape();
        if (rendershape != RenderShape.INVISIBLE) {
            var renderer = MINECRAFT.getBlockRenderer();
            BakedModel bakedmodel = renderer.getBlockModel(blockState);
            modelData = bakedmodel.getModelData(MINECRAFT.level, blockPos, blockState, modelData);
            int blockColor = MINECRAFT.getBlockColors().getColor(blockState, MINECRAFT.level, blockPos, 0);
            float r = FastColor.ARGB32.red(blockColor) / 255F;
            float g = FastColor.ARGB32.green(blockColor) / 255F;
            float b = FastColor.ARGB32.blue(blockColor) / 255F;
            for (RenderType renderType : bakedmodel.getRenderTypes(blockState, RandomSource.create(42), modelData)) {
                renderer.getModelRenderer()
                        .renderModel(guiGraphics.pose().last(),
                                buffers.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false)), blockState,
                                bakedmodel, r, g, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, modelData,
                                renderType);
            }
            BlockEntity blockEntity = MINECRAFT.level.getBlockEntity(blockPos);
            if (blockEntity != null) {
                var beRenderer = MINECRAFT.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
                if (beRenderer != null) {
                    beRenderer.render(blockEntity, partialTick, guiGraphics.pose(), buffers, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                }

            }
        }
        guiGraphics.pose().popPose();
    }

    private void translateToBlock(GuiGraphics guiGraphics, BlockPos pos) {
        guiGraphics.pose().translate(pos.getX() - worldOrigin.x, pos.getY() - worldOrigin.y, pos.getZ() - worldOrigin.z);
    }

    private void renderSelection(GuiGraphics guiGraphics, int centerX, int centerY, Quaternionf transform) {
        if (selection.isEmpty()) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, Z_OFFSET);
        guiGraphics.pose().scale(scale, scale, -scale);
        guiGraphics.pose().mulPose(transform);

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        TextureAtlasSprite tex = MINECRAFT.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SELECTED_ICON);
        RenderSystem.setShaderTexture(0, tex.atlasLocation());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        var selectedFace = selection.get();
        translateToBlock(guiGraphics, selectedFace.blockPos);
        Vector3f[] vec = ModelRenderUtil.createQuadVerts(selectedFace.side, 0, 1, 1);
        Matrix4f matrix4f = guiGraphics.pose().last().pose();
        bufferbuilder.vertex(matrix4f, vec[0].x(), vec[0].y(), vec[0].z())
                .uv(tex.getU0(), tex.getV0()).color(1F, 1F, 1F, 1F).endVertex();
        bufferbuilder.vertex(matrix4f, vec[1].x(), vec[1].y(), vec[1].z())
                .uv(tex.getU0(), tex.getV1()).color(1F, 1F, 1F, 1F).endVertex();
        bufferbuilder.vertex(matrix4f, vec[2].x(), vec[2].y(), vec[2].z())
                .uv(tex.getU1(), tex.getV1()).color(1F, 1F, 1F, 1F).endVertex();
        bufferbuilder.vertex(matrix4f, vec[3].x(), vec[3].y(), vec[3].z())
                .uv(tex.getU1(), tex.getV0()).color(1F, 1F, 1F, 1F).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        guiGraphics.pose().popPose();
    }

    private void renderOverlay(GuiGraphics guiGraphics) {
        if (selection.isPresent()) {
            var selectedFace = selection.get();
            BlockEntity entity = MINECRAFT.level.getBlockEntity(selectedFace.blockPos);
            if (entity instanceof IOConfigurable ioConfigurable) {
                var ioMode = ioConfigurable.getIOMode(selectedFace.side);
                IOModeMap map = IOModeMap.getMapFromMode(ioMode);
                Rect2i iconBounds = map.getRect();
                GuiSprites.blit(guiGraphics, IO_CONFIG_OVERLAY, 48, 16, iconBounds.getX(), iconBounds.getY(), getX() + 4,
                        getY() + height - 4 - MINECRAFT.font.lineHeight - iconBounds.getHeight(), iconBounds.getWidth(),
                        iconBounds.getHeight());
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, OVERLAY_Z_OFFSET); // to ensure that string is drawn on top
                guiGraphics.drawString(MINECRAFT.font, map.getComponent(), getX() + 4,
                        getY() + height - 2 - MINECRAFT.font.lineHeight, 0xFFFFFFFF);
                guiGraphics.pose().popPose();
            }
        }
    }

    private void renderNeighbourButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GuiSprites.blit(guiGraphics, NEIGHBOURS_BTN, neighBtnRect.getX(), neighBtnRect.getY(), 16, 16);
        if (neighBtnRect.contains(mouseX, mouseY)) {
            guiGraphics.renderTooltip(MINECRAFT.font, EIOCommonLang.TOGGLE_NEIGHBOUR.copy().withStyle(ChatFormatting.WHITE),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    private record SelectedFace(BlockPos blockPos, Direction side) {
    }

    private static class GhostBuffers extends MultiBufferSource.BufferSource {
        private GhostBuffers() {
            super(new BufferBuilder(256), new LinkedHashMap<>());
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType remapped = GhostRenderLayer.remap(type);
            fixedBuffers.computeIfAbsent(remapped, layer -> new BufferBuilder(layer.bufferSize()));
            return super.getBuffer(remapped);
        }
    }

    private static class SolidBuffers extends MultiBufferSource.BufferSource {
        private SolidBuffers() {
            super(new BufferBuilder(256), new LinkedHashMap<>());
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType remapped = SolidRenderLayer.remap(type);
            fixedBuffers.computeIfAbsent(remapped, layer -> new BufferBuilder(layer.bufferSize()));
            return super.getBuffer(remapped);
        }
    }

    // Solid buffers, but without depth testing.
    private static class SolidRenderLayer extends RenderType {
        private static final Map<RenderType, RenderType> REMAPPED_TYPES = new IdentityHashMap<>();

        private SolidRenderLayer(RenderType original) {
            super(String.format("%s_%s_solid", original, EnderIO.MOD_ID), original.format(), original.mode(),
                    original.bufferSize(), original.affectsCrumbling(), true, () -> {
                        original.setupRenderState();

                        RenderSystem.disableDepthTest();
                    }, () -> {
                        RenderSystem.enableDepthTest();

                        original.clearRenderState();
                    });
        }

        public static RenderType remap(RenderType in) {
            if (in instanceof SolidRenderLayer) {
                return in;
            } else {
                return REMAPPED_TYPES.computeIfAbsent(in, SolidRenderLayer::new);
            }
        }
    }

    private static class GhostRenderLayer extends RenderType {
        private static final Map<RenderType, RenderType> REMAPPED_TYPES = new IdentityHashMap<>();

        private GhostRenderLayer(RenderType original) {
            super(String.format("%s_%s_ghost", original, EnderIO.MOD_ID), original.format(), original.mode(),
                    original.bufferSize(), original.affectsCrumbling(), true, () -> {
                        original.setupRenderState();

                        RenderSystem.disableDepthTest();
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1, 1, 1,
                                MachinesConfig.CLIENT.IO_CONFIG_NEIGHBOUR_TRANSPARENCY.get().floatValue());
                    }, () -> {
                        RenderSystem.setShaderColor(1, 1, 1, 1);
                        RenderSystem.disableBlend();
                        RenderSystem.enableDepthTest();

                        original.clearRenderState();
                    });
        }

        public static RenderType remap(RenderType in) {
            if (in instanceof GhostRenderLayer) {
                return in;
            } else {
                return REMAPPED_TYPES.computeIfAbsent(in, GhostRenderLayer::new);
            }
        }
    }

}

package com.spearmacebackport.client;

import java.util.List;

import javax.vecmath.Matrix4f;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.IPerspectiveAwareModel;

/**
 * Spear dual-art model: the short icon for GUI/GROUND/FIXED contexts and
 * the long held-spear art for first/third person. The switch is purely the
 * render transform type — no item state or entity comparison — so it cannot
 * render backwards the way the held-item predicate did.
 */
public class SpearPerspectiveModel implements IPerspectiveAwareModel {
    private final IBakedModel gui;
    private final IBakedModel hand;

    public SpearPerspectiveModel(IBakedModel gui, IBakedModel hand) {
        this.gui = gui;
        this.hand = hand;
    }

    private IBakedModel pick(ItemCameraTransforms.TransformType type) {
        switch (type) {
            case GUI:
            case GROUND:
            case FIXED:
                return gui;
            default:
                return hand;
        }
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(
            ItemCameraTransforms.TransformType cameraTransformType) {
        IBakedModel model = pick(cameraTransformType);
        if (model instanceof IPerspectiveAwareModel) {
            return ((IPerspectiveAwareModel) model).handlePerspective(cameraTransformType);
        }
        return Pair.of(model, new Matrix4f());
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return gui.getQuads(state, side, rand);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return gui.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return gui.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return gui.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return gui.getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return gui.getOverrides();
    }
}

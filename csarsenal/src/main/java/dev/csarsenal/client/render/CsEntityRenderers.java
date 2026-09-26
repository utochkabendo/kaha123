package dev.csarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.entity.C4Entity;
import dev.csarsenal.entity.GrenadeEntity;
import dev.csarsenal.entity.InfernoEntity;
import dev.csarsenal.weapon.GrenadeType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class CsEntityRenderers {
    private static final ResourceLocation BLANK = CsArsenal.id("textures/material/metal_dark.png");

    public static final class Grenade extends EntityRenderer<GrenadeEntity> {
        public Grenade(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.08f;
        }

        @Override
        public void render(GrenadeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            GrenadeType t = e.grenadeType();
            Mesh mesh = Meshes.get(t.id);
            if (mesh != null) {
                ps.pushPose();
                ps.translate(0, 0.06f, 0);
                if (!e.resting) {
                    ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                    ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.spinO, e.spin)));
                } else {
                    ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 360));
                    ps.mulPose(Axis.ZP.rotationDegrees(90));
                }
                for (String part : mesh.parts.keySet()) {
                    if (part.equals("pin")) continue; // thrown grenades have no pin
                    MeshRenderer.part(mesh, part, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                }
                ps.popPose();
            }
            super.render(e, yaw, pt, ps, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GrenadeEntity e) {
            return BLANK;
        }
    }

    public static final class C4 extends EntityRenderer<C4Entity> {
        public C4(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.25f;
        }

        @Override
        public void render(C4Entity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            Mesh mesh = Meshes.get("c4");
            if (mesh != null) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                ps.translate(0, 0.02f, 0);
                MeshRenderer.all(mesh, ps, buf, light, 0xFFFFFFFF, MeshRenderer.WEAPON_TEX);
                // blinking LED: faster as the timer runs out
                int left = e.ticksLeft();
                int period = Math.max(2, Math.min(20, left / 30 + 2));
                if ((e.tickCount % period) < 2) {
                    MuzzleFlash.render(ps, buf, new Vector3f(0.03f, 0.035f, -0.055f), 0.02f, 1f, 0f);
                }
                ps.popPose();
            }
            super.render(e, yaw, pt, ps, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(C4Entity e) {
            return BLANK;
        }
    }

    public static final class Inferno extends EntityRenderer<InfernoEntity> {
        public Inferno(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0f;
        }

        @Override
        public ResourceLocation getTextureLocation(InfernoEntity e) {
            return BLANK;
        }
    }

    private CsEntityRenderers() {
    }
}

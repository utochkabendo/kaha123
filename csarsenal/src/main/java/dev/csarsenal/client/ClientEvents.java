package dev.csarsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.csarsenal.ClientConfig;
import dev.csarsenal.CsArsenal;
import dev.csarsenal.anim.HitShape;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.client.fx.ClientFx;
import dev.csarsenal.client.fx.ClientSounds;
import dev.csarsenal.client.fx.InfernoSound;
import dev.csarsenal.client.fx.SmokeManager;
import dev.csarsenal.client.fx.WorldFx;
import dev.csarsenal.client.gui.BuyMenuScreen;
import dev.csarsenal.client.hud.CsHud;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.move.SubtickInput;
import dev.csarsenal.client.render.AgentPose;
import dev.csarsenal.client.render.AgentRenderer;
import dev.csarsenal.client.render.ViewModelRenderer;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.entity.GrenadeEntity;
import dev.csarsenal.entity.InfernoEntity;
import dev.csarsenal.network.Packets;
import dev.csarsenal.registry.ModParticles;
import dev.csarsenal.server.CsMode;
import dev.csarsenal.weapon.C4Item;
import dev.csarsenal.weapon.CsItem;
import dev.csarsenal.weapon.EquipmentItem;
import dev.csarsenal.weapon.GrenadeType;
import dev.csarsenal.weapon.RecoilPattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

@EventBusSubscriber(modid = CsArsenal.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {
    private static final RandomSource R = RandomSource.create();

    private static float pt(Minecraft mc) {
        return mc.getTimer().getGameTimeDeltaPartialTick(true);
    }

    // ------------------------------------------------------------------------------------------ per frame (sub-tick)
    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        AgentPose.newFrame();
        if (mc.player == null) return;
        boolean cs = CsMode.active(mc.player);
        SubtickInput.walkDown = cs && Keys.rawDown(Keys.WALK);
        SubtickInput.crouchDown = cs && Keys.rawDown(Keys.CROUCH);
        SubtickInput.sample(mc);
        ClientWeapon.INSTANCE.frame(mc, e.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    // ------------------------------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientSounds.tick();
        if (p == null || mc.level == null) return;
        boolean cs = CsMode.active(p);
        if (cs) {
            // CS has no sprint
            mc.options.keySprint.setDown(false);
            if (p.isSprinting()) p.setSprinting(false);
        }
        while (Keys.RELOAD.consumeClick()) if (ClientWeapon.INSTANCE.def != null) ClientWeapon.INSTANCE.startReload();
        while (Keys.INSPECT.consumeClick()) if (ClientWeapon.INSTANCE.def != null) ClientWeapon.INSTANCE.inspect();
        while (Keys.BUY.consumeClick()) if (mc.screen == null) mc.setScreen(new BuyMenuScreen());
        while (Keys.DROP.consumeClick()) {
            if (CsItem.holding(p)) PacketDistributor.sendToServer(new Packets.Action(Packets.Action.DROP, p.getInventory().selected, 0));
        }
        WorldFx.tick(mc.level);
        SmokeManager.tick(mc);
        ClientFx.cameraShake *= 0.85;
        // grenade / inferno client effects
        for (Entity ent : mc.level.entitiesForRendering()) {
            if (ent instanceof GrenadeEntity g && g.grenadeType() == GrenadeType.SMOKE && g.detonated()) {
                SmokeManager.start(g);
            } else if (ent instanceof InfernoEntity inf) {
                int n = inf.visibleCells();
                for (int i = 0; i < n; i++) {
                    BlockPos c = inf.cells.get(i);
                    if (R.nextFloat() < 0.55f) {
                        mc.level.addParticle(ModParticles.FLAME.get(), c.getX() + R.nextDouble(), c.getY() + 0.05, c.getZ() + R.nextDouble(),
                                (R.nextDouble() - 0.5) * 0.01, 0.02 + R.nextDouble() * 0.03, (R.nextDouble() - 0.5) * 0.01);
                    }
                    if (R.nextFloat() < 0.05f) {
                        mc.level.addParticle(ModParticles.SMOKE.get(), c.getX() + R.nextDouble(), c.getY() + 0.8, c.getZ() + R.nextDouble(), 0, 0.03, 0);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof InfernoEntity inf) {
            Minecraft.getInstance().getSoundManager().play(new InfernoSound(inf));
        }
    }

    // ------------------------------------------------------------------------------------------ input
    @SubscribeEvent
    public static void movementInput(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer p) || !CsMode.active(p)) return;
        // CS controls: Ctrl = crouch, Shift = walk (handled by CsMovement)
        e.getInput().shiftKeyDown = SubtickInput.crouchDown;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getHand() != InteractionHand.MAIN_HAND) return;
        var stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof CsItem)) return;
        boolean allowUse = stack.getItem() instanceof C4Item || stack.getItem() instanceof EquipmentItem;
        if (e.isAttack() || (e.isUseItem() && !allowUse)) {
            // C4 defusing: right clicking a planted bomb must still reach the entity
            if (e.isUseItem() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof dev.csarsenal.entity.C4Entity) return;
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    // ------------------------------------------------------------------------------------------ rendering
    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientWeapon.INSTANCE.def == null) return;
        e.setCanceled(true);
        if (e.getHand() == InteractionHand.MAIN_HAND) {
            ViewModelRenderer.render(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(), e.getPartialTick(), mc.player);
        }
    }

    @SubscribeEvent
    public static void player(RenderPlayerEvent.Pre e) {
        if (!ClientConfig.bool(ClientConfig.REPLACE_PLAYER_MODEL, true)) return;
        if (AgentRenderer.render(e.getEntity(), e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        ClientWeapon w = ClientWeapon.INSTANCE;
        float track = RecoilPattern.RECOIL_SCALE * RecoilPattern.VIEW_TRACKING;
        float shake = (float) ClientFx.cameraShake;
        e.setPitch(e.getPitch() - w.punch[1] * track - w.shakePitch * 0.35f + (R.nextFloat() - 0.5f) * shake);
        e.setYaw(e.getYaw() + w.punch[0] * track + w.shakeYaw * 0.35f + (R.nextFloat() - 0.5f) * shake);
        e.setRoll(e.getRoll() + w.shakeRoll * 0.3f);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        if (w.def == null) return;
        if (!e.usedConfiguredFov()) {
            e.setFOV(ClientConfig.num(ClientConfig.VIEWMODEL_FOV, 68.0));
            return;
        }
        if (w.isScoped()) {
            double f = Math.toRadians(e.getFOV());
            e.setFOV(Math.toDegrees(2 * Math.atan(w.zoomFactor() * Math.tan(f / 2))));
        }
    }

    @SubscribeEvent
    public static void turn(CalculatePlayerTurnEvent e) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        if (!w.isScoped()) return;
        double k = w.zoomFactor() * ClientConfig.num(ClientConfig.ZOOM_SENSITIVITY, 1.0);
        double s = e.getMouseSensitivity();
        double base = s * 0.6 + 0.2;
        double scaled = Math.cbrt(k) * base;
        e.setMouseSensitivity(Math.max(0, (scaled - 0.2) / 0.6));
    }

    @SubscribeEvent
    public static void gui(RenderGuiLayerEvent.Pre e) {
        if (!CsHud.active()) return;
        var n = e.getName();
        if (n.equals(VanillaGuiLayers.CROSSHAIR)) e.setCanceled(true);
        if (ClientConfig.bool(ClientConfig.HIDE_VANILLA_BARS, true) && (n.equals(VanillaGuiLayers.PLAYER_HEALTH) || n.equals(VanillaGuiLayers.ARMOR_LEVEL))) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void level(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        WorldFx.render(e.getPoseStack(), e.getCamera(), buf, pt);
        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes() && mc.level != null) {
            debugHitboxes(e.getPoseStack(), e.getCamera().getPosition(), buf, pt);
        }
        buf.endBatch();
    }

    private static void debugHitboxes(PoseStack ps, Vec3 cam, MultiBufferSource.BufferSource buf, float pt) {
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity) || en.distanceToSqr(cam) > 48 * 48) continue;
            if (en == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            List<HitShape> shapes = Hitboxes.forEntity(en, pt);
            for (HitShape s : shapes) drawShape(vc, ps, s);
        }
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }

    private static void drawShape(VertexConsumer vc, PoseStack ps, HitShape s) {
        int col = switch (s.group) {
            case HEAD -> 0xFFFF3030;
            case CHEST -> 0xFF30FF30;
            case STOMACH -> 0xFF30FFFF;
            case LEFT_ARM, RIGHT_ARM -> 0xFFFFFF30;
            case LEFT_LEG, RIGHT_LEG -> 0xFF3080FF;
            default -> 0xFFFFFFFF;
        };
        Matrix4f m = ps.last().pose();
        if (s.box) {
            double[][] c = new double[8][3];
            for (int i = 0; i < 8; i++) {
                double sx = (i & 1) == 0 ? -s.hx : s.hx, sy = (i & 2) == 0 ? -s.hy : s.hy, sz = (i & 4) == 0 ? -s.hz : s.hz;
                c[i][0] = s.cx + s.axes[0] * sx + s.axes[3] * sy + s.axes[6] * sz;
                c[i][1] = s.cy + s.axes[1] * sx + s.axes[4] * sy + s.axes[7] * sz;
                c[i][2] = s.cz + s.axes[2] * sx + s.axes[5] * sy + s.axes[8] * sz;
            }
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] ed : edges) line(vc, ps, m, c[ed[0]], c[ed[1]], col);
        } else {
            double[] a = {s.ax, s.ay, s.az}, b = {s.bx, s.by, s.bz};
            Vec3 d = new Vec3(s.bx - s.ax, s.by - s.ay, s.bz - s.az);
            Vec3 u = Math.abs(d.normalize().y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 v = d.lengthSqr() > 1e-8 ? d.cross(u).normalize() : new Vec3(0, 0, 1);
            for (int k = 0; k < 12; k++) {
                double t0 = k * Math.PI / 6, t1 = (k + 1) * Math.PI / 6;
                for (double[] o : new double[][]{a, b}) {
                    double[] p0 = {o[0] + (u.x * Math.cos(t0) + v.x * Math.sin(t0)) * s.r, o[1] + (u.y * Math.cos(t0) + v.y * Math.sin(t0)) * s.r, o[2] + (u.z * Math.cos(t0) + v.z * Math.sin(t0)) * s.r};
                    double[] p1 = {o[0] + (u.x * Math.cos(t1) + v.x * Math.sin(t1)) * s.r, o[1] + (u.y * Math.cos(t1) + v.y * Math.sin(t1)) * s.r, o[2] + (u.z * Math.cos(t1) + v.z * Math.sin(t1)) * s.r};
                    line(vc, ps, m, p0, p1, col);
                }
                if (k % 3 == 0) {
                    double[] p0 = {a[0] + (u.x * Math.cos(t0) + v.x * Math.sin(t0)) * s.r, a[1] + (u.y * Math.cos(t0) + v.y * Math.sin(t0)) * s.r, a[2] + (u.z * Math.cos(t0) + v.z * Math.sin(t0)) * s.r};
                    double[] p1 = {b[0] + (u.x * Math.cos(t0) + v.x * Math.sin(t0)) * s.r, b[1] + (u.y * Math.cos(t0) + v.y * Math.sin(t0)) * s.r, b[2] + (u.z * Math.cos(t0) + v.z * Math.sin(t0)) * s.r};
                    line(vc, ps, m, p0, p1, col);
                }
            }
        }
    }

    private static void line(VertexConsumer vc, PoseStack ps, Matrix4f m, double[] a, double[] b, int col) {
        float nx = (float) (b[0] - a[0]), ny = (float) (b[1] - a[1]), nz = (float) (b[2] - a[2]);
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return;
        nx /= l;
        ny /= l;
        nz /= l;
        vc.addVertex(m, (float) a[0], (float) a[1], (float) a[2]).setColor(col).setNormal(ps.last(), nx, ny, nz);
        vc.addVertex(m, (float) b[0], (float) b[1], (float) b[2]).setColor(col).setNormal(ps.last(), nx, ny, nz);
    }

    // ------------------------------------------------------------------------------------------ sounds
    /** CS: walking (shift) and crouching are silent */
    @SubscribeEvent
    public static void steps(PlayLevelSoundEvent.AtPosition e) {
        if (!e.getLevel().isClientSide() || e.getSound() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !CsMovement.INSTANCE.active) return;
        if (!(CsMovement.INSTANCE.walking || mc.player.isCrouching())) return;
        if (!e.getSound().value().getLocation().getPath().endsWith(".step")) return;
        if (e.getPosition().distanceToSqr(mc.player.position()) < 0.25) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientWeapon.INSTANCE.reset();
        CsMovement.INSTANCE.reset();
        SubtickInput.reset();
        SmokeManager.reset();
        WorldFx.reset();
        AgentPose.reset();
        CsHud.reset();
        ClientData.clear();
    }

    private ClientEvents() {
    }
}

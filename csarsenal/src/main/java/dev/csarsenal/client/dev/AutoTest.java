package dev.csarsenal.client.dev;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.client.weapon.ClientWeapon;
import dev.csarsenal.registry.ModItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development only: -Dcsarsenal.autotest=<scenario list> runs scripted scenes in a singleplayer world and saves
 * screenshots (used to visually verify view models, animations, poses and the HUD without a human).
 */
public final class AutoTest {
    public static final String PROP = System.getProperty("csarsenal.autotest", "");
    public static boolean enabled() {
        return !PROP.isEmpty();
    }

    private record Step(int delay, Consumer<Minecraft> action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, wait = 0, tick;
    private static boolean started;
    private static String lastVm = "";
    public static boolean walk, crouch;

    private static void logMove(Minecraft mc, String label) {
        var m = dev.csarsenal.client.move.CsMovement.INSTANCE;
        CsArsenal.LOG.info("AUTOTEST move {} speed={} u/s ground={} z={}", label, Math.round(m.horizontalSpeed()), m.onGround,
                mc.player == null ? 0 : String.format("%.2f", mc.player.getZ()));
    }

    private static void step(int delay, Consumer<Minecraft> a) {
        STEPS.add(new Step(delay, a));
    }

    private static void cmd(Minecraft mc, String c) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c));
    }

    private static void hold(Minecraft mc, String id) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null || mc.player == null) return;
        java.util.UUID u = mc.player.getUUID();
        s.execute(() -> {
            ServerPlayer sp = s.getPlayerList().getPlayer(u);
            if (sp != null) {
                sp.getInventory().selected = 0;
                ItemStack st = ModItems.stack(id);
                sp.setItemInHand(InteractionHand.MAIN_HAND, st);
            }
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "cs_" + name + ".png", mc.getMainRenderTarget(), m -> {
        });
        CsArsenal.LOG.info("AUTOTEST screenshot {}", name);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.yHeadRot = yaw;
        mc.player.yBodyRot = yaw;
    }

    private static void build() {
        String[] scenes = PROP.split(",");
        step(40, mc -> {
            cmd(mc, "gamemode creative @a");
            cmd(mc, "time set 6000");
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "weather clear");
            cmd(mc, "gamerule doMobSpawning false");
            cmd(mc, "kill @e[type=!player]");
            cmd(mc, "tp @a 0 100 0");
            cmd(mc, "fill -12 99 -12 12 99 12 minecraft:smooth_stone");
            cmd(mc, "fill -12 100 -12 12 110 12 minecraft:air");
            cmd(mc, "fill -12 100 10 12 106 10 minecraft:stone_bricks");
            cmd(mc, "fill -3 100 6 3 103 6 minecraft:oak_planks");
            mc.options.hideGui = false;
        });
        step(60, mc -> {
            cmd(mc, "tp @a 0 100 0 0 0");
            look(mc, 0, 0);
        });
        step(80, mc -> look(mc, 0, 0));
        for (String sc : scenes) {
            String s = sc.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("set:")) {
                // set:key=value;key=value  view model placement overrides (replaces the previous set)
                String body = s.substring(4);
                step(1, mc -> {
                    dev.csarsenal.client.render.ViewModelRenderer.TUNE.clear();
                    for (String kv : body.split(";")) {
                        String[] q = kv.split("=");
                        if (q.length == 2) dev.csarsenal.client.render.ViewModelRenderer.TUNE.put(q[0].trim(), Float.parseFloat(q[1].trim()));
                    }
                });
            } else if (s.startsWith("vm:")) {
                // vm:<item>:<name>  idle view model shot (switching items waits for the draw animation)
                String[] q = s.split(":");
                String id = q[1], name = q.length > 2 ? q[2] : q[1];
                boolean same = id.equals(lastVm);
                lastVm = id;
                step(same ? 1 : 5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); if (!same) hold(mc, id); look(mc, 0, 0); });
                step(same ? 4 : 45, mc -> shot(mc, "vm_" + name));
            } else if (s.startsWith("fp:")) {
                // fp:<item> idle / fire / reload / inspect sequence
                String id = s.substring(3);
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0); });
                step(45, mc -> shot(mc, id + "_idle"));
                step(1, mc -> ClientWeapon.INSTANCE.testTrigger(true));
                step(3, mc -> shot(mc, id + "_fire"));
                step(8, mc -> { ClientWeapon.INSTANCE.testTrigger(false); });
                step(30, mc -> ClientWeapon.INSTANCE.testReload());
                step(Math.max(4, (int) (reloadTicks(id) * 0.25)), mc -> shot(mc, id + "_reload25"));
                step(Math.max(4, (int) (reloadTicks(id) * 0.3)), mc -> shot(mc, id + "_reload55"));
                step(Math.max(4, (int) (reloadTicks(id) * 0.3)), mc -> shot(mc, id + "_reload85"));
                step(reloadTicks(id) / 2 + 10, mc -> ClientWeapon.INSTANCE.inspect());
                step(20, mc -> shot(mc, id + "_inspect25"));
                step(22, mc -> shot(mc, id + "_inspect55"));
                step(40, mc -> { });
            } else if (s.startsWith("anim:")) {
                // anim:<item>:<slashA|slashB|stab|inspect|reload|draw|fire>:<t;t;...>  frames at exact animation times
                String[] q = s.split(":");
                String id = q[1], kind = q[2];
                boolean same = id.equals(lastVm);
                lastVm = id;
                step(same ? 1 : 5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); if (!same) hold(mc, id); look(mc, 0, 0); });
                if (!same) step(45, mc -> { });
                if (kind.equals("reload")) step(1, mc -> ClientWeapon.INSTANCE.testReload());
                for (String ts : q[3].split(";")) {
                    float t = Float.parseFloat(ts);
                    step(2, mc -> capture(id + "_" + kind + "_" + ts, () -> pin(kind, t)));
                }
                step(2, mc -> {
                    ClientWeapon w = ClientWeapon.INSTANCE;
                    w.inspectStart = w.knifeStart = w.boltStart = w.silencerStart = -100;
                    w.silencerTarget = w.silencerOn;
                    w.pinPulled = false;
                    w.throwStart = w.pinStart = -100;
                    w.reloading = false;
                });
            } else if (s.startsWith("spray:")) {
                // spray:<item>:<yaw>  full magazine without mouse compensation, then a screenshot of the holes
                String[] q = s.split(":");
                String id = q[1];
                float yaw = q.length > 2 ? Float.parseFloat(q[2]) : 0;
                var d = dev.csarsenal.weapon.Weapons.byId(id);
                int ticks = d == null ? 60 : (int) Math.ceil(d.magSize * d.cycleTime * 20) + 6;
                lastVm = id;
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, yaw, 0); });
                step(45, mc -> { look(mc, yaw, 0); ClientWeapon.INSTANCE.testTrigger(true); });
                step(3, mc -> shot(mc, id + "_spray_mid"));
                step(ticks, mc -> ClientWeapon.INSTANCE.testTrigger(false));
                step(30, mc -> { look(mc, yaw, 0); });
                step(5, mc -> shot(mc, id + "_spray"));
            } else if (s.startsWith("kseq:")) {
                // kseq:<item>  frame sequences of slash, stab and inspect
                String id = s.substring(5);
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0); });
                step(40, mc -> ClientWeapon.INSTANCE.testKnife(false));
                for (int i = 1; i <= 5; i++) {
                    int f = i;
                    step(1, mc -> shot(mc, id + "_slash_" + f));
                }
                step(20, mc -> ClientWeapon.INSTANCE.testKnife(false));
                for (int i = 1; i <= 5; i++) {
                    int f = i;
                    step(1, mc -> shot(mc, id + "_slashb_" + f));
                }
                step(25, mc -> ClientWeapon.INSTANCE.testKnife(true));
                for (int i = 1; i <= 6; i++) {
                    int f = i;
                    step(2, mc -> shot(mc, id + "_stab_" + f));
                }
                step(30, mc -> ClientWeapon.INSTANCE.inspect());
                for (int i = 1; i <= 10; i++) {
                    int f = i;
                    step(8, mc -> shot(mc, id + "_insp_" + f));
                }
                step(20, mc -> { });
            } else if (s.startsWith("knife:")) {
                String id = s.substring(6);
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0); });
                step(35, mc -> shot(mc, id + "_idle"));
                step(1, mc -> ClientWeapon.INSTANCE.testKnife(false));
                step(3, mc -> shot(mc, id + "_slash"));
                step(20, mc -> ClientWeapon.INSTANCE.testKnife(true));
                step(7, mc -> shot(mc, id + "_stab"));
                step(25, mc -> ClientWeapon.INSTANCE.inspect());
                step(18, mc -> shot(mc, id + "_inspect25"));
                step(25, mc -> shot(mc, id + "_inspect55"));
                step(50, mc -> { });
            } else if (s.startsWith("tp:")) {
                String id = s.substring(3);
                step(5, mc -> { hold(mc, id); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); look(mc, 180, 0); });
                step(40, mc -> shot(mc, id + "_tp"));
                step(1, mc -> { look(mc, 180, 35); mc.getEntityRenderDispatcher().setRenderHitBoxes(true); });
                step(20, mc -> shot(mc, id + "_tp_hitbox_down"));
                step(1, mc -> { look(mc, 150, -30); mc.getEntityRenderDispatcher().setRenderHitBoxes(false); });
                step(20, mc -> shot(mc, id + "_tp_up"));
                step(1, mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); look(mc, 200, 5); });
                step(20, mc -> shot(mc, id + "_tp_back"));
                step(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            } else if (s.startsWith("side:")) {
                // look at our own player from the side through an armor stand camera
                String id = s.substring(5);
                step(5, mc -> { hold(mc, id); look(mc, 0, 0); cmd(mc, "summon minecraft:armor_stand -2.2 99.9 1.0 {Invisible:1b,NoGravity:1b,Tags:[\"cam\"],Rotation:[-114f,10f]}"); });
                step(20, mc -> camTo(mc));
                step(30, mc -> shot(mc, id + "_side"));
                step(1, mc -> look(mc, 0, 40));
                step(15, mc -> shot(mc, id + "_side_down"));
                step(1, mc -> { look(mc, 0, 0); ClientWeapon.INSTANCE.testReload(); });
                step(15, mc -> shot(mc, id + "_side_reload"));
                step(1, mc -> { look(mc, 0, 0); mc.getEntityRenderDispatcher().setRenderHitBoxes(true); });
                step(30, mc -> shot(mc, id + "_side_hitbox"));
                step(1, mc -> { mc.getEntityRenderDispatcher().setRenderHitBoxes(false); mc.setCameraEntity(mc.player); cmd(mc, "kill @e[tag=cam]"); });
            } else if (s.startsWith("gren:")) {
                String id = s.substring(5);
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0); });
                step(35, mc -> shot(mc, id + "_idle"));
                step(1, mc -> ClientWeapon.INSTANCE.testPin());
                step(12, mc -> shot(mc, id + "_pin"));
                step(10, mc -> { });
            } else if (s.startsWith("scope:")) {
                String id = s.substring(6);
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0); });
                step(35, mc -> ClientWeapon.INSTANCE.testScope());
                step(10, mc -> shot(mc, id + "_scope"));
                step(5, mc -> ClientWeapon.INSTANCE.zoom = 0);
            } else if (s.startsWith("nade:")) {
                // nade:<type>:<ticks>  detonate a grenade ahead of the player, screenshot after <ticks>
                String[] q = s.split(":");
                var type = dev.csarsenal.weapon.GrenadeType.valueOf(q[1].toUpperCase());
                int after = q.length > 2 ? Integer.parseInt(q[2]) : 60;
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, "ak47"); look(mc, 0, 8); });
                step(30, mc -> spawnNade(mc, type, type == dev.csarsenal.weapon.GrenadeType.FLASH ? 2.5 : 5));
                step(after, mc -> shot(mc, "nade_" + q[1] + "_" + after));
                if (type == dev.csarsenal.weapon.GrenadeType.FLASH) {
                    step(20, mc -> shot(mc, "nade_flash_late"));
                    step(40, mc -> shot(mc, "nade_flash_later"));
                }
                step(60, mc -> cmd(mc, "kill @e[type=!player]"));
            } else if (s.startsWith("kill:")) {
                // kill:<item>:<mob>  shoot a mob standing 6 blocks ahead (checks damage + the kill reward)
                String[] q = s.split(":");
                String id = q[1], mob = q[2];
                lastVm = id;
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); look(mc, 0, 0);
                    cmd(mc, "summon minecraft:" + mob + " 0.5 100 6.5 {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}"); });
                step(45, mc -> { look(mc, 0, mob.equals("zombie") ? -1.5f : 0); ClientWeapon.INSTANCE.testTrigger(true); });
                step(4, mc -> shot(mc, "kill_" + id + "_" + mob + "_hit"));
                step(20, mc -> ClientWeapon.INSTANCE.testTrigger(false));
                step(20, mc -> {
                    shot(mc, "kill_" + id + "_" + mob);
                    CsArsenal.LOG.info("AUTOTEST money {}", mc.player == null ? -1 : dev.csarsenal.client.ClientData.money(mc.player));
                });
                step(5, mc -> cmd(mc, "kill @e[type=minecraft:" + mob + "]"));
            } else if (s.startsWith("move:")) {
                // move:<item>:<run|walk|crouch>  hold W for 36 ticks and log the CS speed, then release
                String[] q = s.split(":");
                String id = q[1], mode = q.length > 2 ? q[2] : "run";
                lastVm = id;
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); hold(mc, id); cmd(mc, "tp @a 0.5 100 5.5 180 0"); });
                step(40, mc -> { look(mc, 180, 0); walk = mode.equals("walk"); crouch = mode.equals("crouch"); mc.options.keyUp.setDown(true); });
                int[] at = {1, 2, 3, 5, 8, 12, 20, 30};
                int prev = 0;
                for (int t : at) {
                    int d = t - prev;
                    prev = t;
                    step(d, mc -> logMove(mc, id + " " + mode + " t=" + t));
                }
                step(6, mc -> { mc.options.keyUp.setDown(false); logMove(mc, id + " " + mode + " release"); });
                for (int t = 1; t <= 6; t++) {
                    int tt = t;
                    step(1, mc -> logMove(mc, id + " " + mode + " stop+" + tt));
                }
                step(10, mc -> { walk = crouch = false; logMove(mc, id + " " + mode + " end"); });
            } else if (s.equals("camside") || s.equals("camfront")) {
                // look at our own player through an armor stand (right-front side or straight front)
                String pos = s.equals("camside") ? "-1.5 99.8 1.3 {Invisible:1b,NoGravity:1b,Tags:[\"cam\"],Rotation:[-118f,14f]}"
                        : "0.5 99.9 3.2 {Invisible:1b,NoGravity:1b,Tags:[\"cam\"],Rotation:[180f,10f]}";
                step(5, mc -> { cmd(mc, "tp @a 0.5 100 0.5 0 0"); look(mc, 0, 0); cmd(mc, "summon minecraft:armor_stand " + pos); });
                step(20, mc -> camTo(mc));
                step(5, mc -> { });
            } else if (s.equals("camself")) {
                step(2, mc -> { mc.setCameraEntity(mc.player); cmd(mc, "kill @e[tag=cam]"); });
            } else if (s.startsWith("cmd:")) {
                String c = s.substring(4);
                step(5, mc -> cmd(mc, c));
            } else if (s.startsWith("buy")) {
                // buy[:name]  open the buy menu (stays open) and take a screenshot
                String name = s.contains(":") ? s.substring(4) : "buy";
                step(5, mc -> mc.setScreen(new dev.csarsenal.client.gui.BuyMenuScreen()));
                step(20, mc -> shot(mc, name));
            } else if (s.startsWith("press:")) {
                // press:<digit>  a number key on the open screen, then a screenshot
                int k = Integer.parseInt(s.substring(6));
                step(2, mc -> { if (mc.screen != null) mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_0 + k, 0, 0); });
                step(12, mc -> shot(mc, "press_" + k + "_" + (tick)));
            } else if (s.equals("close")) {
                step(2, mc -> mc.setScreen(null));
                step(10, mc -> shot(mc, "closed_" + tick));
            } else if (s.equals("smoke")) {
                step(5, mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); look(mc, 0, 10); cmd(mc, "summon csarsenal:grenade 0 100.2 5"); });
                step(5, mc -> spawnSmoke(mc));
                step(80, mc -> shot(mc, "smoke"));
            }
        }
        step(20, mc -> {
            CsArsenal.LOG.info("AUTOTEST done");
            mc.stop();
        });
    }

    // ------------------------------------------------------------------ render synced captures
    private static Runnable pendingSetup;
    private static String pendingName;
    private static boolean setupDone;

    /** capture a frame: `setup` runs right before the frame renders, the screenshot is taken right after it */
    private static void capture(String name, Runnable setup) {
        pendingName = name;
        pendingSetup = setup;
        setupDone = false;
    }

    public static void preFrame(Minecraft mc) {
        if (pendingSetup == null) return;
        try {
            pendingSetup.run();
        } catch (Exception e) {
            CsArsenal.LOG.error("AUTOTEST setup failed", e);
        }
        setupDone = true;
    }

    public static void postFrame(Minecraft mc) {
        if (pendingSetup == null || !setupDone) return;
        shot(mc, pendingName);
        pendingSetup = null;
        pendingName = null;
    }

    /** pin an animation of the local weapon controller at normalised time t */
    private static void pin(String kind, float t) {
        ClientWeapon w = ClientWeapon.INSTANCE;
        double now = w.now;
        switch (kind) {
            case "slashA", "slashB" -> { w.knifeHeavy = false; w.knifeSide = kind.equals("slashA") ? 0 : 1; w.knifeStart = now - t * 0.45; }
            case "stab" -> { w.knifeHeavy = true; w.knifeStart = now - t * 1.0; }
            case "inspect" -> w.inspectStart = now - t * ClientWeapon.INSPECT_TIME;
            case "reload" -> { w.reloading = true; w.reloadStart = now - t * w.reloadDuration; }
            case "draw" -> w.drawStart = now - t * w.drawDuration;
            case "fire" -> w.lastShot = now - t;
            case "pin" -> { w.pinPulled = true; w.pinStart = now - t * (ClientWeapon.PIN_TIME + 0.15); }
            case "throw" -> { w.pinPulled = false; w.throwStart = now - t * ClientWeapon.THROW_TIME; }
            case "bolt" -> { w.boltStart = now - t * w.def.cycleTime; w.lastShot = w.boltStart; }
            case "silencer" -> { w.silencerTarget = !w.silencerOn; w.silencerStart = now - t * ClientWeapon.SILENCER_TIME; }
            default -> {
            }
        }
    }

    private static void camTo(Minecraft mc) {
        if (mc.level == null) return;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                mc.setCameraEntity(e);
                return;
            }
        }
    }

    private static void spawnNade(Minecraft mc, dev.csarsenal.weapon.GrenadeType type, double dist) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            var level = s.overworld();
            var g = new dev.csarsenal.entity.GrenadeEntity(dev.csarsenal.registry.ModEntities.GRENADE.get(), level);
            g.setup(type, null, new net.minecraft.world.phys.Vec3(0.5, 101.2, dist), new net.minecraft.world.phys.Vec3(0, -0.1, 0.02));
            level.addFreshEntity(g);
        });
    }

    private static void spawnSmoke(Minecraft mc) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            var level = s.overworld();
            var g = new dev.csarsenal.entity.GrenadeEntity(dev.csarsenal.registry.ModEntities.GRENADE.get(), level);
            g.setup(dev.csarsenal.weapon.GrenadeType.SMOKE, null, new net.minecraft.world.phys.Vec3(0.5, 100.1, 5.5), net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(g);
        });
    }

    private static int reloadTicks(String id) {
        var d = dev.csarsenal.weapon.Weapons.byId(id);
        return d == null ? 40 : (int) (d.reloadTime * 20);
    }

    /** called every client tick */
    public static void tick(Minecraft mc) {
        if (!enabled()) return;
        tick++;
        if (mc.player == null || mc.level == null) return;
        if (!started) {
            started = true;
            build();
            index = 0;
            wait = STEPS.isEmpty() ? 0 : STEPS.get(0).delay;
        }
        if (index < 0 || index >= STEPS.size()) return;
        if (pendingSetup != null) return;   // wait for a render synced capture
        if (--wait > 0) return;
        Step s = STEPS.get(index);
        try {
            s.action.accept(mc);
        } catch (Exception e) {
            CsArsenal.LOG.error("AUTOTEST step failed", e);
        }
        index++;
        if (index < STEPS.size()) wait = STEPS.get(index).delay;
    }

    public static void writeMarker(String text) {
        try {
            Files.writeString(Path.of("autotest_status.txt"), text);
        } catch (IOException ignored) {
        }
    }

    private AutoTest() {
    }
}

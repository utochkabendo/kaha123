package dev.csarsenal.client;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.anim.Hitboxes;
import dev.csarsenal.client.fx.CsParticles;
import dev.csarsenal.client.hud.CsHud;
import dev.csarsenal.client.move.CsMovement;
import dev.csarsenal.client.render.AgentPose;
import dev.csarsenal.client.render.CsEntityRenderers;
import dev.csarsenal.client.render.Meshes;
import dev.csarsenal.client.render.WeaponItemRenderer;
import dev.csarsenal.network.Net;
import dev.csarsenal.registry.ModEntities;
import dev.csarsenal.registry.ModItems;
import dev.csarsenal.registry.ModParticles;
import dev.csarsenal.server.CsMovementHook;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.registries.DeferredItem;

@EventBusSubscriber(modid = CsArsenal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        Net.client = ClientPackets.INSTANCE;
        CsMovementHook.handler = CsMovement.INSTANCE;
        Hitboxes.poser = AgentPose.INSTANCE;
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(Keys.RELOAD);
        e.register(Keys.INSPECT);
        e.register(Keys.WALK);
        e.register(Keys.CROUCH);
        e.register(Keys.BUY);
        e.register(Keys.DROP);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, CsArsenal.id("scope"), CsHud::scope);
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, CsArsenal.id("crosshair"), CsHud::crosshair);
        e.registerAbove(VanillaGuiLayers.HOTBAR, CsArsenal.id("status"), CsHud::status);
        e.registerAboveAll(CsArsenal.id("flash"), CsHud::flash);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.GRENADE.get(), CsEntityRenderers.Grenade::new);
        e.registerEntityRenderer(ModEntities.C4.get(), CsEntityRenderers.C4::new);
        e.registerEntityRenderer(ModEntities.INFERNO.get(), CsEntityRenderers.Inferno::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r) r.addLayer(new dev.csarsenal.client.render.GunLayer(r));
        }
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.SMOKE.get(), CsParticles.SmokeProvider::new);
        e.registerSpriteSet(ModParticles.IMPACT.get(), CsParticles.ImpactProvider::new);
        e.registerSpriteSet(ModParticles.SPARK.get(), CsParticles.SparkProvider::new);
        e.registerSpriteSet(ModParticles.BLOOD.get(), CsParticles.BloodProvider::new);
        e.registerSpriteSet(ModParticles.FLAME.get(), CsParticles.FlameProvider::new);
        e.registerSpriteSet(ModParticles.FIREBALL.get(), CsParticles.FireballProvider::new);
        e.registerSpriteSet(ModParticles.MUZZLE_SMOKE.get(), CsParticles.MuzzleSmokeProvider::new);
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent e) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return WeaponItemRenderer.get();
            }
        };
        Item[] items = ModItems.ORDERED.stream().map(d -> (Item) d.get()).toArray(Item[]::new);
        e.registerItem(ext, items);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(Meshes.INSTANCE);
    }

    private ClientSetup() {
    }
}

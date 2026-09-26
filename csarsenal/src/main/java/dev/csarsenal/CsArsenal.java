package dev.csarsenal;

import com.mojang.logging.LogUtils;
import dev.csarsenal.network.Net;
import dev.csarsenal.registry.ModAttachments;
import dev.csarsenal.registry.ModComponents;
import dev.csarsenal.registry.ModEntities;
import dev.csarsenal.registry.ModItems;
import dev.csarsenal.registry.ModParticles;
import dev.csarsenal.registry.ModSounds;
import dev.csarsenal.server.CsCommands;
import dev.csarsenal.server.ServerEvents;
import dev.csarsenal.weapon.Weapons;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CsArsenal.MODID)
public final class CsArsenal {
    public static final String MODID = "csarsenal";
    public static final Logger LOG = LogUtils.getLogger();

    public CsArsenal(IEventBus modBus, ModContainer container) {
        Weapons.init();
        ModComponents.REGISTER.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);
        ModAttachments.REGISTER.register(modBus);
        modBus.addListener(Net::register);

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.addListener(CsCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}

package dev.csarsenal.registry;

import dev.csarsenal.CsArsenal;
import dev.csarsenal.server.CsPlayerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CsArsenal.MODID);

    public static final Supplier<AttachmentType<CsPlayerData>> CS_DATA = REGISTER.register("cs_data",
            () -> AttachmentType.builder(() -> CsPlayerData.EMPTY).serialize(CsPlayerData.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }
}

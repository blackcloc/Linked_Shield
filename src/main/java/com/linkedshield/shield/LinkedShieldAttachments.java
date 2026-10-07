package com.linkedshield.shield;

import com.linkedshield.LinkedShieldMod;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.party.PartyStore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** 数据附件注册：玩家护盾 + 全局小队表。 */
public final class LinkedShieldAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LinkedShieldMod.MODID);

    /** 玩家护盾，随玩家存档保存、死亡后保留。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ShieldData>> SHIELD =
            ATTACHMENT_TYPES.register("shield", () -> AttachmentType.builder(() -> {
                var shield = ConfigManager.get().shield;
                return ShieldData.of(shield.defaultCurrentShield, shield.defaultMaxShield);
            }).serialize(ShieldData.CODEC).copyOnDeath().build());

    /** 全局小队表，挂在主世界上。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PartyStore>> PARTY_STORE =
            ATTACHMENT_TYPES.register("party_store", () -> AttachmentType.<PartyStore>builder(() -> new PartyStore())
                    .serialize(PartyStore.CODEC).build());

    private LinkedShieldAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}

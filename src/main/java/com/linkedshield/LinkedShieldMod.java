package com.linkedshield;

import com.linkedshield.api.LinkedShieldDataComponents;
import com.linkedshield.command.LinkedShieldCommands;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.dev.LinkedShieldDamageProbe;
import com.linkedshield.dev.LinkedShieldSelfTest;
import com.linkedshield.network.LinkedShieldNetwork;
import com.linkedshield.network.PartySync;
import com.linkedshield.shield.LinkedShieldAttachments;
import com.linkedshield.shield.ShieldService;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * LinkedShield Party —— FF14 风格小队组队 + 护盾 / 连携回盾。
 *
 * <p>Minecraft 1.21.11 / NeoForge 21.11.x</p>
 */
@Mod(LinkedShieldMod.MODID)
public class LinkedShieldMod {

    public static final String MODID = "linkedshield";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LinkedShieldMod(IEventBus modEventBus, ModContainer modContainer) {
        // 生成 / 读取 config/linkedshield/settings.json，并写出 HTML 配置编辑器
        ConfigManager.load();

        LinkedShieldAttachments.register(modEventBus);
        // 对外接口：物品组件 linkedshield:shield_bonus（饰品/装备加护盾上限）
        LinkedShieldDataComponents.COMPONENTS.register(modEventBus);
        modEventBus.addListener(LinkedShieldNetwork::register);

        // 指令
        NeoForge.EVENT_BUS.addListener(LinkedShieldCommands::onRegisterCommands);
        // 护盾：战斗标记（受伤即进入战斗，用于连携延迟）
        NeoForge.EVENT_BUS.addListener(ShieldService::onIncomingDamage);
        // 护盾：真正结算（护甲/附魔减伤之后）
        NeoForge.EVENT_BUS.addListener(ShieldService::onLivingDamage);
        // 护盾：连携回盾
        NeoForge.EVENT_BUS.addListener(ShieldService::onServerTick);
        // 小队状态同步
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> PartySync.tick(event.getServer()));
        // 进服提示配置文件位置
        NeoForge.EVENT_BUS.addListener(LinkedShieldMod::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(LinkedShieldMod::onServerStarted);
        // 无人值守自检（-Dlinkedshield.selftest=true）
        NeoForge.EVENT_BUS.addListener(LinkedShieldSelfTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener(LinkedShieldSelfTest::onServerTick);
        // 伤害管线实测（-Dlinkedshield.damagetest=true，需要玩家在线）
        NeoForge.EVENT_BUS.addListener(LinkedShieldDamageProbe::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(LinkedShieldDamageProbe::onServerTick);

        LOGGER.info("[LinkedShield] 已加载：护盾 / 小队 / 连携回盾（MC 1.21.11 + NeoForge）");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("[LinkedShield] 配置文件：{}", ConfigManager.settingsFile().toAbsolutePath());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 进服只做一件事：算一次外部护盾上限加成（饰品 / 物品上的 linkedshield:shield_bonus）。
        // 1.0.2 起不再往聊天栏发任何提示（配置页可用 /linkedshield config 打开）。
        ShieldService.refreshBonuses(player);
    }
}

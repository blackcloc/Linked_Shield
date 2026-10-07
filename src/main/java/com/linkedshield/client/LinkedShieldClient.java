package com.linkedshield.client;

import com.linkedshield.LinkedShieldMod;
import com.linkedshield.network.PartyStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;

/** 客户端注册：HUD 图层、组队界面按键、网络包处理。 */
@EventBusSubscriber(modid = LinkedShieldMod.MODID, value = Dist.CLIENT)
public final class LinkedShieldClient {

    /** 默认按 U 打开组队界面。 */
    public static final int DEFAULT_PARTY_KEY = GLFW.GLFW_KEY_U;

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, "main"));

    private static KeyMapping openPartyKey;
    /** 截图/联调用：-Dlinkedshield.guidemo=true 时进世界后自动打开一次组队界面。 */
    private static final boolean GUI_DEMO = Boolean.getBoolean("linkedshield.guidemo");
    private static int demoTicks;

    private LinkedShieldClient() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, path);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 自己的护盾条画在原版血条之上（默认就在血条正上方）
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, id("self_shield"), new SelfShieldLayer());
        // 连携生效时血条旁边的小图标
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, id("linkedshield_icon"), new LinkedShieldIconLayer());
        // 队友面板画在所有东西之上，保证一直在屏幕最左侧可见
        event.registerAboveAll(id("party_list"), new PartyHudLayer());
        // 收到组队邀请时右上角的小点
        event.registerAboveAll(id("party_notify"), new PartyNotifyLayer());
        LinkedShieldMod.LOGGER.info("[LinkedShield] 已注册 HUD 图层：队友面板 + 自身护盾条 + 连携图标 + 邀请提醒");
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        openPartyKey = new KeyMapping("key.linkedshield.open_party", InputConstants.Type.KEYSYM,
                DEFAULT_PARTY_KEY, CATEGORY);
        event.register(openPartyKey);
    }

    @SubscribeEvent
    public static void onRegisterClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(PartyStatePayload.TYPE, (payload, context) -> ClientPartyState.update(payload));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPartyState.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (openPartyKey == null || minecraft.player == null) {
            return;
        }
        while (openPartyKey.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new PartyScreen());
            } else if (minecraft.screen instanceof PartyScreen) {
                minecraft.setScreen(null);
            }
        }
        if (GUI_DEMO && minecraft.screen == null && demoTicks++ == 120) {
            minecraft.setScreen(new PartyScreen());
        }
    }

    /** 底部提示里显示的按键名。 */
    public static String openKeyName() {
        try {
            return openPartyKey == null ? "P" : openPartyKey.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return "P";
        }
    }
}

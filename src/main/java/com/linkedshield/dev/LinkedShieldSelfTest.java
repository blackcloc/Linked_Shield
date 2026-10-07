package com.linkedshield.dev;

import com.linkedshield.api.LinkedShieldDataComponents;
import com.linkedshield.api.LinkedShieldEvent;
import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.network.PartyActionPayload;
import com.linkedshield.network.PartyStatePayload;
import com.linkedshield.party.Party;
import com.linkedshield.party.PartyStore;
import com.linkedshield.shield.DamageClassifier;
import com.linkedshield.shield.ShieldData;
import com.linkedshield.shield.ShieldMath;
import com.linkedshield.shield.ShieldService;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自动化自检：加 JVM 参数 {@code -Dlinkedshield.selftest=true} 启动服务器时，
 * 进服 40 tick 后跑一遍配置生成 / 小队 / 数据包 / 护盾公式 / 连携公式的检查，
 * 打印 PASS/FAIL 日志并自动关服。用于无人值守的“跑一次游戏测试”。
 */
public final class LinkedShieldSelfTest {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String PREFIX = "[LinkedShield][SELFTEST] ";

    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;
    private static int countdown = -1;

    private LinkedShieldSelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("linkedshield.selftest");
    }

    public static void onServerStarted(ServerStartedEvent event) {
        if (enabled() && countdown < 0) {
            countdown = 40;
            LOG.info(PREFIX + "服务器已启动，40 tick 后开始自检");
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (countdown < 0) {
            return;
        }
        if (--countdown > 0) {
            return;
        }
        countdown = -1;
        run(event.getServer());
    }

    private static void run(MinecraftServer server) {
        int total = 0;
        try {
            testConfigFiles();
            testPartyStore();
            testFriendlyFire();
            testPartyCodec();
            testPartyName();
            testShieldCodec();
            testPayloadCodec(server);
            testShieldMath();
            testDamageClassification(server);
            testLinkedShieldMath();
            testExtensionApi();
        } catch (Throwable t) {
            fail("unexpected exception: " + t);
            LOG.error(PREFIX + "自检过程中出现异常", t);
        }
        total = checks;
        if (FAILURES.isEmpty()) {
            LOG.info(PREFIX + "PASSED 全部 {} 项检查", total);
            LOG.info(PREFIX + "自检结束，关闭服务器");
            server.halt(false);
            return;
        }
        LOG.error(PREFIX + "FAILED {}/{} 项检查", FAILURES.size(), total);
        for (String failure : FAILURES) {
            LOG.error(PREFIX + "  - " + failure);
        }
        LOG.error(PREFIX + "自检失败：以退出码 1 结束进程，gradle runSelfTest 会失败");
        // tick 里抛出的异常会被 MinecraftServer 的 crash 处理吞掉（只写 crash-report，
        // 不改变进程退出码），所以这里直接 halt(1) 保证 gradle 任务非零退出。仅 dev 自检路径。
        Runtime.getRuntime().halt(1);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            FAILURES.add(name);
            LOG.error(PREFIX + "FAIL: " + name);
        } else {
            LOG.info(PREFIX + "ok: " + name);
        }
    }

    private static void fail(String name) {
        checks++;
        FAILURES.add(name);
        LOG.error(PREFIX + "FAIL: " + name);
    }

    /* ------------------------------------------------------------------ 各项检查 */

    /**
     * 语言文件按 Minecraft 模组标准走：en_us 是权威文件（必须最全），
     * en_gb / ru_ru 可以只写差异（缺键由 MC 回退到 en_us），但不能是空文件或坏 JSON。
     */
    private static void testLangFiles() {
        java.util.Set<String> zh = langKeys("zh_cn");
        java.util.Set<String> us = langKeys("en_us");
        java.util.Set<String> gb = langKeys("en_gb");
        java.util.Set<String> ru = langKeys("ru_ru");
        check("lang/zh_cn.json 可解析且非空", zh.size() > 100);
        check("lang/en_us.json 可解析且非空", us.size() > 100);
        check("lang/ru_ru.json 可解析且非空", ru.size() > 100);
        check("lang/en_gb.json 存在（英式英语，可只写差异）", gb.size() > 0);
        java.util.Set<String> missingUs = new java.util.TreeSet<>(zh);
        missingUs.removeAll(us);
        check("en_us 覆盖 zh_cn 全部键（缺 " + missingUs.size() + "）", missingUs.isEmpty());
        java.util.Set<String> missingRu = new java.util.TreeSet<>(zh);
        missingRu.removeAll(ru);
        check("ru_ru 覆盖 zh_cn 全部键（缺 " + missingRu.size() + "）", missingRu.isEmpty());
        check("en_gb 有英式拼写差异（armour/colour/unrecognised）",
                String.join(" ", gb).contains("armour") || langRaw("en_gb").contains("armour"));
    }

    private static java.util.Set<String> langKeys(String lang) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        try (var in = LinkedShieldSelfTest.class.getResourceAsStream("/assets/linkedshield/lang/" + lang + ".json")) {
            if (in == null) {
                return keys;
            }
            var json = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : json.keySet()) {
                if (key.startsWith("linkedshield.") || key.startsWith("key.")) {
                    keys.add(key);
                }
            }
        } catch (Exception e) {
            LOG.error(PREFIX + "读取 lang/{} 失败: {}", lang, e.getMessage());
        }
        return keys;
    }

    private static String langRaw(String lang) {
        try (var in = LinkedShieldSelfTest.class.getResourceAsStream("/assets/linkedshield/lang/" + lang + ".json")) {
            return in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 对外接口：物品组件 linkedshield:shield_bonus + 事件 + 有效上限计算。 */
    private static void testExtensionApi() {
        check("已注册物品组件 linkedshield:shield_bonus", LinkedShieldDataComponents.SHIELD_BONUS.get() != null);
        check("组件 id 正确", "linkedshield:shield_bonus".equals(LinkedShieldDataComponents.shieldBonusId().toString()));

        ItemStack stack = new ItemStack(Items.DIAMOND_CHESTPLATE);
        check("普通物品没有护盾上限加成", stack.get(LinkedShieldDataComponents.SHIELD_BONUS.get()) == null);
        stack.set(LinkedShieldDataComponents.SHIELD_BONUS.get(), 25.0D);
        check("物品组件可写入并读回 25", Double.valueOf(25.0D).equals(stack.get(LinkedShieldDataComponents.SHIELD_BONUS.get())));

        ShieldData data = ShieldData.of(50.0D, 100.0D);
        check("没有加成时有效上限 = 基础上限", ShieldService.effectiveMaxShield(data) == 100.0D);
        data.setBonusMaxShield(30.0D);
        check("有效上限 = 基础上限 + 加成 (130)", ShieldService.effectiveMaxShield(data) == 130.0D);
        check("占比按有效上限算 (50/130)", Math.abs(ShieldService.ratio(data) - 50.0D / 130.0D) < 1.0E-6D);

        LinkedShieldEvent.MaxShield event = new LinkedShieldEvent.MaxShield(null, 100.0D, 10.0D);
        event.addBonus(30.0D);
        check("MaxShield 事件可累加护盾上限", event.getTotal() == 140.0D);
        event.setCanceled(true);
        check("MaxShield 事件可被取消", event.isCanceled());

        check("API 常量：ChangeReason 有 4 种来源",
                LinkedShieldEvent.ChangeReason.values().length == 4);
    }

    /** 首次运行必须在 config/linkedshield/ 生成 settings.json 与可点击的 HTML 配置页。 */
    private static void testConfigFiles() throws Exception {
        Path settings = ConfigManager.settingsFile();
        Path html = ConfigManager.htmlFile();
        check("config/linkedshield/settings.json 已生成", Files.exists(settings));
        check("config/linkedshield/config_editor.html 已生成", Files.exists(html));
        if (!Files.exists(html)) {
            return;
        }
        String text = Files.readString(html);
        check("HTML 内嵌当前配置", text.contains("maxPartySize"));
        check("HTML 内嵌默认值", text.contains("maxPartySize"));
        check("HTML 带编辑器版本号", text.contains("linkedshield-editor-version"));
        check("HTML 无未替换占位符", !text.contains("__LINKEDSHIELD_"));
        // 多语言：HTML 内嵌 4 套编辑器文案 + 国旗切换
        check("HTML 内嵌 4 种语言", text.contains("\"zh_cn\"") && text.contains("\"en_us\"")
                && text.contains("\"en_gb\"") && text.contains("\"ru_ru\""));
        check("HTML 有国旗切换按钮", text.contains("flagBtn") && text.contains("🇬🇧") && text.contains("🇷🇺"));
        check("HTML 有原版参照物（生命/经验/快捷栏）", text.contains("renderVanillaRefs") && text.contains("vhotbar"));
        check("HTML 预览是 1:1（无缩放、16:9）", text.contains("aspect-ratio:16/9") && !text.contains("hudPreviewScale"));
        check("HTML 预览排在数值下面", text.indexOf("id=\"form\"") < text.indexOf("previewBox"));
        check("HTML 保存默认写回本文件夹", text.contains("showDirectoryPicker") && text.contains("saveInPlace")
                && text.contains("getFileHandle('settings.json'"));
        check("HTML 有另存为按钮", text.contains("btnSaveAs") && text.contains("showSaveFilePicker"));
        check("HTML 文案走 lang 键", text.contains("linkedshield.editor.field.") && text.contains("linkedshield.editor.ui."));
        testLangFiles();

        // 聊天栏那条“点一下就能打开 HTML”的链接
        var link = ConfigManager.clickableHtmlLink();
        var click = link.getStyle().getClickEvent();
        check("聊天栏配置链接可点击打开本地文件", click instanceof net.minecraft.network.chat.ClickEvent.OpenFile);
        check("聊天栏配置链接显示文件名", link.getString().contains("config_editor.html"));

        // 1.0.1 的默认值
        LinkedShieldSettings defaults = new LinkedShieldSettings();
        check("默认小队上限 = 6", defaults.party.maxPartySize == 6);
        check("默认连携半径 = 10", defaults.linkedshield.radius == 10.0D);
        check("默认连携启动延迟 = 2 秒", defaults.linkedshield.startDelaySeconds == 2.0D);
        check("默认不再要求脱战才回盾", !defaults.linkedshield.requireOutOfCombat);
        check("默认脱战 = 受伤后 2 秒", defaults.combat.combatTagSeconds == 2.0D);
        check("默认连携脱战延迟也是 2 秒", defaults.linkedshield.outOfCombatDelaySeconds == 2.0D);
        check("护盾文本默认只显示实时数值", "CURRENT".equals(defaults.hud.selfShield.numberFormat));
        check("默认法术按远程全额抵挡", "FULL".equals(defaults.damageRules.magicBehavior));
        check("默认队友面板在左侧", "LEFT".equals(defaults.hud.partyList.anchor));
        check("默认显示连携图标", defaults.hud.linkedshieldIcon.visible);

        LinkedShieldSettings original = ConfigManager.get();
        int maxBackup = original.party.maxPartySize;
        original.party.maxPartySize = 5;
        ConfigManager.save(original);
        LinkedShieldSettings reloaded = ConfigManager.reload();
        check("配置可热重载（改 5 生效）", reloaded.party.maxPartySize == 5);
        reloaded.party.maxPartySize = maxBackup;
        ConfigManager.save(reloaded);
        ConfigManager.reload();
    }

    private static void testPartyStore() {
        UUID leader = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        PartyStore store = new PartyStore();
        Party party = store.create(leader);
        check("创建小队：队长就位", party.isLeader(leader) && party.size() == 1);
        check("areTeammates 自己与自己不是队友", !store.areTeammates(leader, leader));
        store.join(party, memberA);
        store.join(party, memberB);
        check("加入小队：3 人", party.size() == 3);
        check("成员反查小队", store.partyOf(memberA) == party);
        check("队长排在第一位", party.orderedMembers().get(0).equals(leader));
        check("areTeammates 同队两人互为队友（双向）",
                store.areTeammates(leader, memberA) && store.areTeammates(memberB, leader));
        check("areTeammates 队外玩家不是队友", !store.areTeammates(leader, outsider));
        check("areTeammates 空 UUID 不是队友", !store.areTeammates(leader, null));
        store.leave(memberA);
        check("离开小队：剩 2 人且反查为空", party.size() == 2 && store.partyOf(memberA) == null);
        check("其余成员仍在队", store.inParty(leader) && store.inParty(memberB));
        check("areTeammates 离队后不再是队友", !store.areTeammates(leader, memberA));
        store.disband(party);
        check("解散小队：全部清空", store.partyOf(leader) == null && store.partyOf(memberB) == null);
        check("areTeammates 解散后不再是队友", !store.areTeammates(leader, memberB));
    }

    /**
     * 友军伤害免疫的判定逻辑（纯函数，不需要玩家在线）：
     * 只有「配置关闭友伤 + 有明确的攻击者 + 攻击者不是自己 + 两人确实同队」才免疫。
     */
    private static void testFriendlyFire() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        PartyStore store = new PartyStore();
        Party party = store.create(leader);
        store.join(party, member);

        check("友伤免疫默认开启（party.friendlyFire 默认 false）", !new LinkedShieldSettings().party.friendlyFire);
        check("友伤免疫：同队两人之间的一击被免疫",
                ShieldService.blocksFriendlyFire(false, store, member, leader));
        check("友伤免疫：配置改成允许友伤后不再免疫",
                !ShieldService.blocksFriendlyFire(true, store, member, leader));
        check("友伤免疫：队外玩家照常受伤",
                !ShieldService.blocksFriendlyFire(false, store, outsider, leader));
        check("友伤免疫：自己打自己不免疫（交回原版处理）",
                !ShieldService.blocksFriendlyFire(false, store, leader, leader));
        check("友伤免疫：没有归属玩家的伤害不免疫",
                !ShieldService.blocksFriendlyFire(false, store, leader, null));
        store.disband(party);
        check("友伤免疫：小队解散后不再免疫",
                !ShieldService.blocksFriendlyFire(false, store, member, leader));
    }

    private static void testPartyCodec() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyStore store = new PartyStore();
        Party party = store.create(leader);
        store.join(party, member);

        Tag tag = PartyStore.CODEC.codec().encodeStart(NbtOps.INSTANCE, store).result().orElse(null);
        check("小队存档编码成功", tag != null);
        if (tag == null) {
            return;
        }
        PartyStore decoded = PartyStore.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
        check("小队存档解码成功", decoded != null);
        if (decoded == null) {
            return;
        }
        Party decodedParty = decoded.partyOf(leader);
        check("解码后队长正确", decodedParty != null && decodedParty.isLeader(leader));
        check("解码后成员正确", decodedParty != null && decodedParty.size() == 2 && decodedParty.contains(member));
    }

    private static void testShieldCodec() {
        ShieldData data = new ShieldData(63.5D, 120.0D, 1234L, 99L);
        Tag tag = ShieldData.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).result().orElse(null);
        check("护盾存档编码成功", tag != null);
        if (tag == null) {
            return;
        }
        ShieldData back = ShieldData.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
        check("护盾存档往返一致", back != null
                && back.getShield() == 63.5D
                && back.getMaxShield() == 120.0D
                && back.getLastDamageTick() == 1234L
                && back.getLastBreakTick() == 99L);
    }

    private static void testPayloadCodec(MinecraftServer server) {
        List<PartyStatePayload.Effect> effects = List.of(
                new PartyStatePayload.Effect("minecraft:speed", 0xFF7CAFC6, false, 1200),
                new PartyStatePayload.Effect("minecraft:poison", 0xFF4E9331, true, 40),
                new PartyStatePayload.Effect("minecraft:regeneration", 0xFFCD5CAB, false, -1));
        PartyStatePayload payload = new PartyStatePayload(
                List.of(new PartyStatePayload.Member(UUID.randomUUID(), "Tester_01", 17.5F, 20.0F, 55.25D, 100.0D, 7, true, effects),
                        new PartyStatePayload.Member(UUID.randomUUID(), "队友二号", 3.0F, 20.0F, 0.0D, 80.0D, 31, false, List.of())),
                List.of(new PartyStatePayload.Nearby(UUID.randomUUID(), "路人甲", 4, false),
                        new PartyStatePayload.Nearby(UUID.randomUUID(), "路人乙", 9, true)),
                42.5D, 100.0D, 6, true, true, "拂晓血盟", "Tester_01", "路人甲", true, 2);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PartyStatePayload.STREAM_CODEC.encode(buf, payload);
        int written = buf.writerIndex();
        check("小队状态包写出数据", written > 20);
        buf.readerIndex(0);
        PartyStatePayload decoded = PartyStatePayload.STREAM_CODEC.decode(buf);
        check("小队状态包往返一致（含中文名/队伍名/邀请/附近/效果）", payload.equals(decoded));
        check("小队状态包无剩余字节", buf.readableBytes() == 0);
        check("小队状态包队友效果数量往返一致（3 + 0）",
                decoded.members().get(0).effects().size() == 3 && decoded.members().get(1).effects().isEmpty());
        check("小队状态包队友效果字段往返一致（含无限时长/负面标记）",
                decoded.members().get(0).effects().equals(effects));

        // C2S 动作包
        RegistryFriendlyByteBuf actionBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PartyActionPayload action = PartyActionPayload.rename("拂晓血盟");
        PartyActionPayload.STREAM_CODEC.encode(actionBuf, action);
        actionBuf.readerIndex(0);
        check("组队动作包往返一致", action.equals(PartyActionPayload.STREAM_CODEC.decode(actionBuf)));
    }

    private static void testPartyName() {
        UUID leader = UUID.randomUUID();
        PartyStore store = new PartyStore();
        Party party = store.create(leader);
        check("默认队伍名 = 队长名 的小队", "Steve 的小队".equals(party.displayName("Steve")));
        party.setName("拂晓血盟");
        check("自定义队伍名生效", "拂晓血盟".equals(party.displayName("Steve")));

        Tag tag = PartyStore.CODEC.codec().encodeStart(NbtOps.INSTANCE, store).result().orElse(null);
        check("带名字的小队存档编码成功", tag != null);
        if (tag != null) {
            PartyStore decoded = PartyStore.CODEC.codec().parse(NbtOps.INSTANCE, tag).result().orElse(null);
            Party decodedParty = decoded == null ? null : decoded.partyOf(leader);
            check("队伍名随存档往返", decodedParty != null && "拂晓血盟".equals(decodedParty.getName()));
        }
    }

    private static void testShieldMath() {
        LinkedShieldSettings cfg = new LinkedShieldSettings();
        double minPierce = cfg.shield.minPierceRatio;

        ShieldMath.Outcome out = ShieldMath.ranged(100.0D, 100.0D, 30.0F, true);
        check("远程：满盾完全免伤且扣 30 盾", out.damage() == 0.0F && out.shield() == 70.0D && !out.broke());

        out = ShieldMath.ranged(10.0D, 100.0D, 30.0F, true);
        check("远程：盾不足时溢出 20 点伤害并破盾",
                Math.abs(out.damage() - 20.0F) < 1.0E-4 && out.shield() == 0.0D && out.broke());

        out = ShieldMath.melee(100.0D, 100.0D, 20.0F, minPierce, true);
        check("近战：满盾只吃 10% 穿盾伤害", Math.abs(out.damage() - 2.0F) < 1.0E-4);
        check("近战：被挡下的 18 点消耗护盾", Math.abs(out.shield() - 82.0D) < 1.0E-6);

        out = ShieldMath.melee(50.0D, 100.0D, 20.0F, minPierce, true);
        check("近战：50% 护盾时约 50% 穿盾", Math.abs(out.damage() - 10.0F) < 1.0E-4);
        check("近战：50% 护盾扣 10 盾", Math.abs(out.shield() - 40.0D) < 1.0E-6);

        out = ShieldMath.melee(90.0D, 100.0D, 100.0F, minPierce, true);
        check("近战：90% 护盾时 10% 穿盾", Math.abs(out.damage() - 10.0F) < 1.0E-4);
        check("近战：90% 护盾被打空且破盾", out.shield() == 0.0D && out.broke());

        out = ShieldMath.melee(0.0D, 100.0D, 20.0F, minPierce, true);
        check("近战：护盾归零时承受 100% 伤害", Math.abs(out.damage() - 20.0F) < 1.0E-4 && out.shield() == 0.0D);

        out = ShieldMath.melee(100.0D, 100.0D, 20.0F, minPierce, false);
        check("近战：关闭扣盾时只减伤不掉值",
                out.shield() == 100.0D && Math.abs(out.damage() - 2.0F) < 1.0E-4);

        check("穿盾比例：满盾 = minPierceRatio", Math.abs(ShieldMath.pierceRatio(100, 100, minPierce) - minPierce) < 1.0E-9);
        check("穿盾比例：半盾 = 0.5", Math.abs(ShieldMath.pierceRatio(50, 100, minPierce) - 0.5D) < 1.0E-9);
        check("穿盾比例：空盾 = 1.0", Math.abs(ShieldMath.pierceRatio(0, 100, minPierce) - 1.0D) < 1.0E-9);
    }

    /** 伤害分类：核查“原版标签/显式集合 → 类别 → 处理方式”，以及配置覆盖与未识别类型回退。 */
    private static void testDamageClassification(MinecraftServer server) {
        DamageSources sources = server.overworld().damageSources();
        LinkedShieldSettings cfg = new LinkedShieldSettings();

        // 投射物：#minecraft:is_projectile
        expectCategory("投射物 arrow", sources.source(DamageTypes.ARROW), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 trident", sources.source(DamageTypes.TRIDENT), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 fireball", sources.source(DamageTypes.FIREBALL), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 wither_skull", sources.source(DamageTypes.WITHER_SKULL), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 wind_charge", sources.source(DamageTypes.WIND_CHARGE), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 thrown", sources.source(DamageTypes.THROWN), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("投射物 spit（配置补进列表）", sources.source(DamageTypes.SPIT), DamageClassifier.Category.PROJECTILE, cfg);

        // 爆炸：#minecraft:is_explosion
        expectCategory("爆炸 explosion(TNT)", sources.source(DamageTypes.EXPLOSION), DamageClassifier.Category.EXPLOSION, cfg);
        expectCategory("爆炸 player_explosion(床/重生锚)", sources.source(DamageTypes.PLAYER_EXPLOSION), DamageClassifier.Category.EXPLOSION, cfg);
        expectCategory("爆炸 fireworks(烟花火箭)", sources.source(DamageTypes.FIREWORKS), DamageClassifier.Category.EXPLOSION, cfg);
        expectCategory("爆炸 bad_respawn_point", sources.source(DamageTypes.BAD_RESPAWN_POINT), DamageClassifier.Category.EXPLOSION, cfg);

        // 近战攻击
        expectCategory("近战 player_attack", sources.source(DamageTypes.PLAYER_ATTACK), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 mob_attack", sources.source(DamageTypes.MOB_ATTACK), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 mob_attack_no_aggro", sources.source(DamageTypes.MOB_ATTACK_NO_AGGRO), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 mace_smash", sources.source(DamageTypes.MACE_SMASH), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 sting(蜜蜂)", sources.source(DamageTypes.STING), DamageClassifier.Category.MELEE, cfg);

        // 持续伤害（按“远程”全额抵挡）：火焰系 + 毒/魔法 + 凋零 + 冰冻
        expectCategory("持续 in_fire", sources.source(DamageTypes.IN_FIRE), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 on_fire", sources.source(DamageTypes.ON_FIRE), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 campfire", sources.source(DamageTypes.CAMPFIRE), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 hot_floor", sources.source(DamageTypes.HOT_FLOOR), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 lava", sources.source(DamageTypes.LAVA), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 magic（毒药）", sources.source(DamageTypes.MAGIC), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 wither（凋零效果）", sources.source(DamageTypes.WITHER), DamageClassifier.Category.DOT, cfg);
        expectCategory("持续 freeze（冰冻）", sources.source(DamageTypes.FREEZE), DamageClassifier.Category.DOT, cfg);

        // 法术与声波（旧实现把它们误判成近战）
        expectCategory("法术 sonic_boom(监守者)", sources.source(DamageTypes.SONIC_BOOM), DamageClassifier.Category.MAGIC, cfg);
        expectCategory("法术 indirect_magic(守卫者激光)", sources.source(DamageTypes.INDIRECT_MAGIC), DamageClassifier.Category.MAGIC, cfg);
        expectCategory("法术 dragon_breath", sources.source(DamageTypes.DRAGON_BREATH), DamageClassifier.Category.MAGIC, cfg);

        // 远程（含原版没进 is_projectile 标签的：闪电、反伤）
        expectCategory("远程 lightning_bolt(闪电)", sources.source(DamageTypes.LIGHTNING_BOLT), DamageClassifier.Category.PROJECTILE, cfg);
        expectCategory("远程 thorns(反伤)", sources.source(DamageTypes.THORNS), DamageClassifier.Category.PROJECTILE, cfg);

        // 近战：接触 / 撞击 / 坠落物
        expectCategory("近战 cactus(仙人掌)", sources.source(DamageTypes.CACTUS), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 sweet_berry_bush(甜浆果丛)", sources.source(DamageTypes.SWEET_BERRY_BUSH), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 cramming(挤压)", sources.source(DamageTypes.CRAMMING), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 fall(摔落)", sources.source(DamageTypes.FALL), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 ender_pearl(末影珍珠)", sources.source(DamageTypes.ENDER_PEARL), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 fly_into_wall(飞行撞击)", sources.source(DamageTypes.FLY_INTO_WALL), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 stalagmite(石笋)", sources.source(DamageTypes.STALAGMITE), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 falling_anvil(铁砧)", sources.source(DamageTypes.FALLING_ANVIL), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 falling_block(下落方块)", sources.source(DamageTypes.FALLING_BLOCK), DamageClassifier.Category.MELEE, cfg);
        expectCategory("近战 falling_stalactite(钟乳石)", sources.source(DamageTypes.FALLING_STALACTITE), DamageClassifier.Category.MELEE, cfg);
        expectCategory("环境 drown(溺水)", sources.source(DamageTypes.DROWN), DamageClassifier.Category.ENVIRONMENT, cfg);
        expectCategory("环境 starve(饥饿)", sources.source(DamageTypes.STARVE), DamageClassifier.Category.ENVIRONMENT, cfg);
        expectCategory("环境 in_wall(卡墙)", sources.source(DamageTypes.IN_WALL), DamageClassifier.Category.ENVIRONMENT, cfg);

        // 各类的默认处理方式
        check("默认处理 投射物 = FULL", DamageClassifier.behaviorFor(DamageClassifier.Category.PROJECTILE, cfg) == DamageClassifier.Behavior.FULL);
        check("默认处理 持续伤害 = FULL（按远程）", DamageClassifier.behaviorFor(DamageClassifier.Category.DOT, cfg) == DamageClassifier.Behavior.FULL);
        check("默认处理 近战 = PIERCE", DamageClassifier.behaviorFor(DamageClassifier.Category.MELEE, cfg) == DamageClassifier.Behavior.PIERCE);
        check("默认处理 爆炸 = PIERCE（按近战公式）", DamageClassifier.behaviorFor(DamageClassifier.Category.EXPLOSION, cfg) == DamageClassifier.Behavior.PIERCE);
        check("默认处理 法术 = FULL（声波/法术按远程）", DamageClassifier.behaviorFor(DamageClassifier.Category.MAGIC, cfg) == DamageClassifier.Behavior.FULL);
        check("默认处理 环境 = NONE", DamageClassifier.behaviorFor(DamageClassifier.Category.ENVIRONMENT, cfg) == DamageClassifier.Behavior.NONE);
        check("默认处理 其它 = NONE", DamageClassifier.behaviorFor(DamageClassifier.Category.OTHER, cfg) == DamageClassifier.Behavior.NONE);
        check("类别只剩 7 种（反伤/坠落物已并入远程与近战）", DamageClassifier.Category.values().length == 7);

        // 配置覆盖（含大小写容错、非法值回退、额外列表优先级）
        LinkedShieldSettings custom = new LinkedShieldSettings();
        custom.damageRules.explosionBehavior = "full";
        check("配置覆盖 爆炸→full 生效", DamageClassifier.behaviorFor(DamageClassifier.Category.EXPLOSION, custom) == DamageClassifier.Behavior.FULL);
        custom.damageRules.explosionBehavior = "???";
        check("配置覆盖 非法值回退默认 PIERCE", DamageClassifier.behaviorFor(DamageClassifier.Category.EXPLOSION, custom) == DamageClassifier.Behavior.PIERCE);
        custom.damageRules.extraMeleeTypes = "minecraft:fireball";
        check("配置覆盖 extraMeleeTypes 优先于标签",
                DamageClassifier.categorize(sources.source(DamageTypes.FIREBALL), custom) == DamageClassifier.Category.MELEE);

        // 兼容模式 + 未识别类型的实体回退
        LinkedShieldSettings legacy = new LinkedShieldSettings();
        legacy.damageRules.useDamageTypeRules = false;
        check("兼容模式 无实体 → OTHER",
                DamageClassifier.categorize(sources.source(DamageTypes.ARROW), legacy) == DamageClassifier.Category.OTHER);

        Registry<DamageType> registry = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        DamageType rawArrow = registry.get(DamageTypes.ARROW.identifier()).orElseThrow().value();
        Entity arrowEntity = EntityType.ARROW.create(server.overworld(), EntitySpawnReason.COMMAND);
        if (arrowEntity == null) {
            fail("无法创建测试用箭实体，跳过未识别类型回退检查");
        } else {
            DamageSource unknownProjectile = new DamageSource(Holder.direct(rawArrow), arrowEntity, arrowEntity);
            check("未识别类型 带投射物实体 → 回退 PROJECTILE",
                    DamageClassifier.categorize(unknownProjectile, cfg) == DamageClassifier.Category.PROJECTILE);
            LinkedShieldSettings noFallback = new LinkedShieldSettings();
            noFallback.damageRules.fallbackToEntityType = false;
            check("关闭回退后 未识别类型 → OTHER",
                    DamageClassifier.categorize(unknownProjectile, noFallback) == DamageClassifier.Category.OTHER);
        }
    }

    private static void expectCategory(String label, DamageSource source,
                                       DamageClassifier.Category expected, LinkedShieldSettings cfg) {
        DamageClassifier.Category actual = DamageClassifier.categorize(source, cfg);
        boolean ok = actual == expected;
        check("分类 " + label + " → " + expected.label() + (ok ? "" : "（实际 " + actual.label() + "）"), ok);
    }

    private static void testLinkedShieldMath() {        LinkedShieldSettings cfg = new LinkedShieldSettings();
        check("连携速率：0 名队友不回复", ShieldMath.linkedshieldRate(0, cfg) == 0.0D);

        LinkedShieldSettings soloCfg = new LinkedShieldSettings();
        soloCfg.linkedshield.soloRate = 1.25D;
        check("单人速率可配置（soloRate）", ShieldMath.linkedshieldRate(0, soloCfg) == 1.25D);
        check("连携速率：1 名队友 = 2.5/s", ShieldMath.linkedshieldRate(1, cfg) == 2.5D);
        check("连携速率：2 名队友 = 3.75/s", ShieldMath.linkedshieldRate(2, cfg) == 3.75D);
        check("连携速率：3 名队友 = 5/s", ShieldMath.linkedshieldRate(3, cfg) == 5.0D);
        check("连携速率：5 名队友仍为 5/s", ShieldMath.linkedshieldRate(5, cfg) == 5.0D);

        double shield = 0.0D;
        for (int i = 0; i < 20; i++) {
            shield = Math.min(100.0D, shield + ShieldMath.linkedshieldRate(1, cfg) / 20.0D);
        }
        check("连携回复 1 秒 = 2.5 点", Math.abs(shield - 2.5D) < 1.0E-6);

        long now = 10000L;
        check("战斗中不回复", !ShieldMath.outOfCombat(now - 20L, Long.MIN_VALUE, now, cfg));
        check("受伤后 2 秒才算脱战（1.95 秒仍算战斗）", !ShieldMath.outOfCombat(now - 39L, Long.MIN_VALUE, now, cfg));
        check("脱战 2 秒后开始回复（默认战斗标签）", ShieldMath.outOfCombat(now - 41L, Long.MIN_VALUE, now, cfg));
        check("刚刚破盾有额外延迟", !ShieldMath.outOfCombat(Long.MIN_VALUE, now - 20L, now, cfg));
        check("旧存档时间戳视为已脱战", ShieldMath.outOfCombat(now + 5000L, Long.MIN_VALUE, now, cfg));

        // 连携启动条件：连携 > 1 人并持续 startDelaySeconds（默认 2 秒）
        check("无连携时不启动", !ShieldMath.linkedshieldReady(Long.MIN_VALUE, now, Long.MIN_VALUE, now, cfg));
        check("连携成立 1 秒还不启动", !ShieldMath.linkedshieldReady(now - 20L, now, Long.MIN_VALUE, now, cfg));
        check("连携成立满 2 秒开始启动", ShieldMath.linkedshieldReady(now - 40L, now, Long.MIN_VALUE, now, cfg));
        check("默认不要求脱战（挨打也照样连携）", ShieldMath.linkedshieldReady(now - 40L, now - 5L, Long.MIN_VALUE, now, cfg));
        check("刚破盾仍会推迟连携", !ShieldMath.linkedshieldReady(now - 40L, now - 400L, now - 10L, now, cfg));
        LinkedShieldSettings combatCfg = new LinkedShieldSettings();
        combatCfg.linkedshield.requireOutOfCombat = true;
        check("开启“要求脱战”后挨打不连携", !ShieldMath.linkedshieldReady(now - 40L, now - 5L, Long.MIN_VALUE, now, combatCfg));
        check("开启“要求脱战”且脱战后可连携", ShieldMath.linkedshieldReady(now - 400L, now - 400L, Long.MIN_VALUE, now, combatCfg));
        combatCfg.linkedshield.requireOutOfCombat = true;
        check("“要求脱战”下受伤 1 秒仍不连携", !ShieldMath.linkedshieldReady(now - 400L, now - 20L, Long.MIN_VALUE, now, combatCfg));
        check("“要求脱战”下受伤 3 秒后恢复连携", ShieldMath.linkedshieldReady(now - 400L, now - 60L, Long.MIN_VALUE, now, combatCfg));
    }
}

package com.linkedshield.dev;

import com.linkedshield.api.LinkedShieldAPI;
import com.linkedshield.api.LinkedShieldDataComponents;
import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.shield.LinkedShieldAttachments;
import com.linkedshield.shield.DamageClassifier;
import com.linkedshield.shield.ShieldData;
import com.linkedshield.shield.ShieldService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 伤害管线实测：加 {@code -Dlinkedshield.damagetest=true} 启动，玩家进服后
 * 用真实的 {@code hurtServer} 打一串伤害，把「掉血 / 扣盾」的实测值打日志 + 聊天栏，
 * 并断言是否符合预期（尤其是“护盾按减甲后的伤害扣，不替护甲买单”）。
 *
 * <p>配合 {@code gradlew runHudDemo -PquickPlayWorld=world -Dlinkedshield.damagetest=true} 使用。</p>
 */
public final class LinkedShieldDamageProbe {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String PREFIX = "[LinkedShield][DMGTEST] ";
    private static final double EPS = 1.0E-3;

    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;
    private static int countdown = -1;
    private static boolean done;
    /** 分步执行：护甲的 ARMOR 属性要等实体 tick 后才生效，所以每步之间隔若干 tick。 */
    private static final int STEP_SPACING_TICKS = 5;
    private static final int ARMOR_WAIT_TIMEOUT_TICKS = 100;
    private static int stepIndex = -1;
    private static int waitTicks;
    private static int armorWait;
    private static boolean prepared;
    private static int expectedArmor;
    private static ServerPlayer subject;
    private static List<Step> steps;

    private LinkedShieldDamageProbe() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("linkedshield.damagetest");
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (enabled() && !done && countdown < 0) {
            countdown = 60; // 等世界/玩家状态稳定
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || done) {
            return;
        }
        if (countdown > 0) {
            countdown--;
            return;
        }
        if (countdown == 0) {
            List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
            if (players.isEmpty()) {
                countdown = 20; // 继续等玩家
                return;
            }
            subject = players.get(0);
            subject.setGameMode(GameType.SURVIVAL);
            steps = buildSteps();
            stepIndex = 0;
            countdown = -1;
            waitTicks = 0;
            say(subject, "=== LinkedShield 伤害管线实测开始（护盾上限 100）===");
        }
        if (steps == null) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (stepIndex >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(stepIndex);
        if (!prepared) {
            // 先穿装备、设护盾、回血，然后等 ARMOR 属性真正生效再打
            prepareStep(step);
            prepared = true;
            armorWait = ARMOR_WAIT_TIMEOUT_TICKS;
            return;
        }
        if (subject.getArmorValue() != expectedArmor && armorWait-- > 0) {
            return; // 护甲属性还没更新
        }
        stepIndex++;
        prepared = false;
        hit(step);
        waitTicks = STEP_SPACING_TICKS;
    }

    /* ------------------------------------------------------------------ 实测步骤 */

    private record Step(String name, boolean armor, double shield, float amount,
                        java.util.function.BiFunction<ServerPlayer, DamageSources, DamageSource> sourceFactory,
                        Check check) {
    }

    @FunctionalInterface
    private interface Check {
        void accept(double healthLost, double shieldLost, int armorValue);
    }

    private static List<Step> buildSteps() {
        List<Step> list = new ArrayList<>();
        // A：无甲近战 —— 满盾 20 伤害只吃 10%（2 点），护盾扣掉被挡下的 18
        list.add(new Step("A 无甲/近战20/满盾", false, 100.0D, 20.0F,
                (p, s) -> s.playerAttack(p),
                (hp, sh, armor) -> {
                    expect("A 掉血 = 2（满盾 10% 穿盾）", hp, 2.0D);
                    expect("A 扣盾 = 18", sh, 18.0D);
                }));
        // B：钻石全套同伤害 —— 护盾应按减甲后的伤害扣，掉血和扣盾都小于 A
        list.add(new Step("B 钻石甲/近战20/满盾", true, 100.0D, 20.0F,
                (p, s) -> s.playerAttack(p),
                (hp, sh, armor) -> {
                    expectTrue("B 护甲值已生效（>0）", armor > 0);
                    expectTrue("B 掉血 < A（护甲生效）", hp > 0 && hp < 2.0D);
                    expectTrue("B 扣盾 < A（护盾不再替护甲买单）", sh > 0 && sh < 18.0D);
                }));
        // C：投射物 —— 全额抵挡，护盾足够时完全不掉血
        list.add(new Step("C 钻石甲/箭30/满盾", true, 100.0D, 30.0F,
                (p, s) -> s.arrow(arrow(p), p),
                (hp, sh, armor) -> {
                    expect("C 掉血 = 0（投射物全额抵挡）", hp, 0.0D);
                    expectTrue("C 扣盾 > 0 且 < 30（按减甲后伤害扣）", sh > 0 && sh < 30.0D);
                }));
        // D：爆炸 —— 与近战同一套百分比穿盾公式
        list.add(new Step("D 钻石甲/爆炸20/满盾", true, 100.0D, 20.0F,
                (p, s) -> s.explosion(null, null),
                (hp, sh, armor) -> {
                    expectTrue("D 掉血 > 0（爆炸按近战公式穿盾）", hp > 0);
                    expectTrue("D 扣盾 > 0", sh > 0);
                }));
        // E：环境伤害（溺水）—— 默认 NONE，一点都不吃护盾
        list.add(new Step("E 钻石甲/溺水6/满盾", true, 100.0D, 6.0F,
                (p, s) -> s.drown(),
                (hp, sh, armor) -> {
                    expectTrue("E 掉血 > 0（溺水按原版结算）", hp > 0);
                    expect("E 扣盾 = 0（环境伤害不吃护盾）", sh, 0.0D);
                }));
        // E2：岩浆属于持续伤害 —— 默认按远程全额抵挡
        list.add(new Step("E2 钻石甲/岩浆6/满盾", true, 100.0D, 6.0F,
                (p, s) -> s.lava(),
                (hp, sh, armor) -> {
                    expect("E2 掉血 = 0（岩浆算持续伤害→全额抵挡）", hp, 0.0D);
                    expectTrue("E2 扣盾 > 0", sh > 0);
                }));
        // E3：仙人掌现在算近战 —— 按百分比穿盾
        list.add(new Step("E3 钻石甲/仙人掌6/满盾", true, 100.0D, 6.0F,
                (p, s) -> s.cactus(),
                (hp, sh, armor) -> {
                    expectTrue("E3 掉血 > 0（仙人掌按近战公式穿盾）", hp > 0);
                    expectTrue("E3 扣盾 > 0", sh > 0);
                }));
        // F：反伤现在算远程 —— 全额抵挡
        list.add(new Step("F 钻石甲/荆棘2/满盾", true, 100.0D, 2.0F,
                (p, s) -> s.thorns(p),
                (hp, sh, armor) -> {
                    expect("F 掉血 = 0（反伤按远程全额抵挡）", hp, 0.0D);
                    expectTrue("F 扣盾 > 0", sh > 0);
                }));
        // G：投射物护盾不够 —— 溢出部分打到血量
        list.add(new Step("G 无甲/箭20/盾10", false, 10.0D, 20.0F,
                (p, s) -> s.arrow(arrow(p), p),
                (hp, sh, armor) -> {
                    expect("G 扣盾 = 10（护盾打空）", sh, 10.0D);
                    expect("G 掉血 = 10（溢出）", hp, 10.0D);
                }));
        // H：护盾归零 —— 近战 100% 穿盾
        list.add(new Step("H 无甲/近战12/盾0", false, 0.0D, 12.0F,
                (p, s) -> s.playerAttack(p),
                (hp, sh, armor) -> {
                    expect("H 掉血 = 12（护盾归零 = 100% 穿盾）", hp, 12.0D);
                    expect("H 扣盾 = 0", sh, 0.0D);
                }));
        // I：半盾 —— 穿盾比例约 50%
        list.add(new Step("I 无甲/近战20/盾50", false, 50.0D, 20.0F,
                (p, s) -> s.playerAttack(p),
                (hp, sh, armor) -> {
                    expect("I 掉血 = 10（50% 护盾 → 50% 穿盾）", hp, 10.0D);
                    expect("I 扣盾 = 10", sh, 10.0D);
                }));
        // J：90% 护盾 —— 依旧只吃 10%（强制下限）
        list.add(new Step("J 无甲/生物近战20/盾90", false, 90.0D, 20.0F,
                (p, s) -> s.mobAttack(p),
                (hp, sh, armor) -> {
                    expect("J 掉血 = 2（90% 护盾 = 10% 穿盾）", hp, 2.0D);
                    expect("J 扣盾 = 18", sh, 18.0D);
                }));
        // K：远程全额抵挡 + 护盾够用
        list.add(new Step("K 无甲/箭5/盾30", false, 30.0D, 5.0F,
                (p, s) -> s.arrow(arrow(p), p),
                (hp, sh, armor) -> {
                    expect("K 掉血 = 0", hp, 0.0D);
                    expect("K 扣盾 = 5", sh, 5.0D);
                }));
        // L：持续伤害（火焰）—— 默认按“远程”全额抵挡
        list.add(new Step("L 钻石甲/火焰5/满盾", true, 100.0D, 5.0F,
                (p, s) -> s.inFire(),
                (hp, sh, armor) -> {
                    expect("L 掉血 = 0（持续伤害按远程全额抵挡）", hp, 0.0D);
                    expectTrue("L 扣盾 > 0 且 < 5（按减甲后伤害扣）", sh > 0 && sh < 5.0D);
                }));
        // M：毒（magic）—— 同样按远程处理
        list.add(new Step("M 钻石甲/毒4/满盾", true, 100.0D, 4.0F,
                (p, s) -> s.magic(),
                (hp, sh, armor) -> {
                    expect("M 掉血 = 0（毒按远程全额抵挡）", hp, 0.0D);
                    expectTrue("M 扣盾 > 0", sh > 0);
                }));
        // N：监守者声波（法术）—— 现在按远程全额抵挡
        list.add(new Step("N 钻石甲/声波8/满盾", true, 100.0D, 8.0F,
                (p, s) -> s.sonicBoom(p),
                (hp, sh, armor) -> {
                    expect("N 掉血 = 0（声波按远程全额抵挡）", hp, 0.0D);
                    expectTrue("N 扣盾 > 0", sh > 0);
                }));
        // O：闪电 —— 按远程全额抵挡
        list.add(new Step("O 钻石甲/闪电8/满盾", true, 100.0D, 8.0F,
                (p, s) -> s.lightningBolt(),
                (hp, sh, armor) -> {
                    expect("O 掉血 = 0（闪电按远程全额抵挡）", hp, 0.0D);
                    expectTrue("O 扣盾 > 0", sh > 0);
                }));
        return list;
    }

    private static void prepareStep(Step step) {
        ServerPlayer player = subject;
        equipArmor(player, step.armor());
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurtTime = 0;
        expectedArmor = step.armor() ? 20 : 0; // 钻石全套 = 20 点护甲
    }

    private static void hit(Step step) {
        ServerPlayer player = subject;
        LinkedShieldSettings cfg = ConfigManager.get();
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        // 护盾值在出手这一刻才设置：等待护甲生效期间，持续伤害（火焰等）会悄悄扣盾
        data.setMaxShield(100.0D);
        data.setShield(step.shield());
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurtTime = 0;

        DamageSource source = step.sourceFactory().apply(player, player.level().damageSources());
        DamageClassifier.Category category = DamageClassifier.categorize(source, cfg);
        DamageClassifier.Behavior behavior = DamageClassifier.behaviorFor(category, cfg);
        int armorValue = player.getArmorValue();

        double shieldBefore = data.getShield();
        double healthBefore = player.getHealth();
        player.hurtServer(player.level(), source, step.amount());

        double healthLost = healthBefore - player.getHealth();
        double shieldLost = shieldBefore - data.getShield();

        String line = String.format("%s | %s 类别=%s 处理=%s 护甲=%d 伤害=%.1f | 掉血 %.2f，扣盾 %.2f，剩盾 %.2f",
                step.name(), DamageClassifier.idOf(source), category.label(), behavior, armorValue, step.amount(),
                healthLost, shieldLost, data.getShield());
        LOG.info(PREFIX + line);
        say(player, line);
        step.check().accept(healthLost, shieldLost, armorValue);
        if (player.isDeadOrDying()) {
            fail(step.name() + "：玩家被打死了，后续步骤不可信");
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void finish() {
        done = true;
        steps = null;
        ServerPlayer player = subject;
        if (player != null && !player.isRemoved()) {
            // P：拓展接口实测 —— 给副手物品挂 linkedshield:shield_bonus 组件，看有效上限是否涨上去
            ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
            data.setMaxShield(ConfigManager.get().shield.defaultMaxShield);
            data.setShield(ConfigManager.get().shield.defaultCurrentShield);
            ShieldService.refreshBonuses(player);
            double base = ShieldService.effectiveMaxShield(player);
            ItemStack off = new ItemStack(Items.SHIELD);
            off.set(LinkedShieldDataComponents.SHIELD_BONUS.get(), 40.0D);
            player.setItemSlot(EquipmentSlot.OFFHAND, off);
            ShieldService.refreshBonuses(player);
            double withBonus = ShieldService.effectiveMaxShield(player);
            expectTrue("P 物品组件加成生效（基础 " + (int) base + " + 40 = " + (int) withBonus + "）",
                    Math.abs(withBonus - (base + 40.0D)) < 0.01D);
            expectTrue("P API 读到的加成 = 40", Math.abs(LinkedShieldAPI.getBonusMaxShield(player) - 40.0D) < 0.01D);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            ShieldService.refreshBonuses(player);
            expectTrue("P 取下物品后加成为 0", Math.abs(LinkedShieldAPI.getBonusMaxShield(player)) < 0.01D);
            equipArmor(player, false);
            player.setHealth(player.getMaxHealth());
        }
        if (FAILURES.isEmpty()) {
            String summary = "=== 实测通过：全部 " + checks + " 项断言 ===";
            LOG.info(PREFIX + summary);
            say(player, summary);
        } else {
            LOG.error(PREFIX + "实测失败 " + FAILURES.size() + "/" + checks + " 项");
            say(player, "=== 实测失败 " + FAILURES.size() + "/" + checks + " 项 ===");
            for (String failure : FAILURES) {
                LOG.error(PREFIX + "  - " + failure);
                say(player, "FAIL: " + failure);
            }
        }
    }

    private static net.minecraft.world.entity.projectile.arrow.Arrow arrow(ServerPlayer player) {
        ServerLevel level = player.level();
        net.minecraft.world.entity.projectile.arrow.Arrow arrow =
                net.minecraft.world.entity.EntityType.ARROW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (arrow == null) {
            throw new IllegalStateException("无法创建测试用箭实体");
        }
        arrow.setPos(player.getX(), player.getY() + 1.0D, player.getZ());
        return arrow;
    }

    private static void equipArmor(ServerPlayer player, boolean armor) {
        player.setItemSlot(EquipmentSlot.HEAD, armor ? new ItemStack(Items.DIAMOND_HELMET) : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, armor ? new ItemStack(Items.DIAMOND_CHESTPLATE) : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, armor ? new ItemStack(Items.DIAMOND_LEGGINGS) : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, armor ? new ItemStack(Items.DIAMOND_BOOTS) : ItemStack.EMPTY);
    }

    private static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    private static void expect(String name, double actual, double expected) {
        checks++;
        if (Math.abs(actual - expected) > EPS) {
            fail(name + "（实测 " + String.format("%.2f", actual) + "）");
        }
    }

    private static void expectTrue(String name, boolean condition) {
        checks++;
        if (!condition) {
            fail(name);
        }
    }

    private static void fail(String reason) {
        FAILURES.add(reason);
        LOG.error(PREFIX + "FAIL: " + reason);
    }
}
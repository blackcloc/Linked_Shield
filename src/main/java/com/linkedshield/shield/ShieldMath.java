package com.linkedshield.shield;

import com.linkedshield.config.LinkedShieldSettings;
import net.minecraft.util.Mth;

/**
 * 护盾数值核心公式（纯函数，方便被 /linkedshield 指令与自检复用）。
 *
 * <ul>
 *   <li>远程：护盾吸满，扣掉等量护盾；护盾不够时溢出伤害打到血量。</li>
 *   <li>近战：穿盾比例 = clamp(1 - 当前护盾/最大护盾, minPierceRatio, 1)，
 *       被挡下的部分按配置决定是否扣护盾。</li>
 * </ul>
 */
public final class ShieldMath {

    private ShieldMath() {
    }

    /**
     * 一次伤害结算的结果。
     *
     * @param damage 实际打在生命值上的伤害
     * @param shield 结算后的护盾值
     * @param broke  本次是否把护盾打空（用于“破盾”提示与回复延迟）
     */
    public record Outcome(float damage, double shield, boolean broke) {
    }

    /** 护盾百分比 0~1。 */
    public static double ratio(double shield, double maxShield) {
        if (maxShield <= 0.0D) {
            return 0.0D;
        }
        return Mth.clamp(shield / maxShield, 0.0D, 1.0D);
    }

    /** 近战穿盾比例：满盾时也有 minPierceRatio（默认 10%）的强制穿透。 */
    public static double pierceRatio(double shield, double maxShield, double minPierceRatio) {
        return Mth.clamp(1.0D - ratio(shield, maxShield), minPierceRatio, 1.0D);
    }

    /** 远程伤害：护盾全额抵挡。 */
    public static Outcome ranged(double shield, double maxShield, float amount, boolean fullyAbsorbed) {
        double capacity = fullyAbsorbed ? amount : amount * ratio(shield, maxShield);
        double absorbed = Math.min(Math.max(0.0D, shield), capacity);
        double shieldAfter = Math.max(0.0D, shield - absorbed);
        float remaining = (float) Math.max(0.0D, amount - absorbed);
        return new Outcome(remaining, shieldAfter, shield > 0.0D && shieldAfter <= 0.0D);
    }

    /** 近战伤害：按护盾百分比穿盾。 */
    public static Outcome melee(double shield, double maxShield, float amount,
                                double minPierceRatio, boolean drainsShield) {
        double pierced = amount * pierceRatio(shield, maxShield, minPierceRatio);
        double shieldAfter = shield;
        if (drainsShield) {
            double blocked = amount - pierced;
            shieldAfter = Math.max(0.0D, shield - Math.min(shield, blocked));
        }
        return new Outcome((float) pierced, shieldAfter, shield > 0.0D && shieldAfter <= 0.0D);
    }

    /** 按分类结果统一结算：FULL = 全额抵挡，PIERCE = 百分比穿盾，NONE = 不吃护盾。 */
    public static Outcome apply(DamageClassifier.Behavior behavior, double shield, double maxShield, float amount,
                                double minPierceRatio, boolean fullyAbsorb, boolean drainsShield) {
        return switch (behavior) {
            case FULL -> ranged(shield, maxShield, amount, fullyAbsorb);
            case PIERCE -> melee(shield, maxShield, amount, minPierceRatio, drainsShield);
            case NONE -> new Outcome(amount, shield, false);
        };
    }

    /** 连携回复速率：队友越多越快（1 人 / 2 人 / 3 人及以上）；soloRate 默认 0。 */
    public static double linkedshieldRate(int nearbyTeammates, LinkedShieldSettings cfg) {
        if (nearbyTeammates <= 0) {
            return cfg.linkedshield.soloRate;
        }
        if (nearbyTeammates >= 3) {
            return cfg.linkedshield.rateThreeOrMore;
        }
        return nearbyTeammates == 2 ? cfg.linkedshield.rateTwoMembers : cfg.linkedshield.rateOneMember;
    }

    /**
     * 连携回盾的启动判定：
     * <ol>
     *   <li>连携已经成立（linkStartTick 有效）并且持续时间 ≥ {@code linkedshield.startDelaySeconds}；</li>
     *   <li>若 {@code linkedshield.requireOutOfCombat} 为 true，还要求脱战（战斗标签 + 连携延迟）；</li>
     *   <li>破盾后仍有 {@code shield.shieldBreakRegenPenaltySeconds} 的额外延迟。</li>
     * </ol>
     */
    public static boolean linkedshieldReady(long linkStartTick, long lastDamageTick, long lastBreakTick,
                                         long now, LinkedShieldSettings cfg) {
        if (linkStartTick == Long.MIN_VALUE) {
            return false;
        }
        long linked = now - linkStartTick;
        if (linked < 0L || linked < cfg.linkedshield.startDelaySeconds * 20.0D) {
            return false;
        }
        if (cfg.linkedshield.requireOutOfCombat && !outOfCombat(lastDamageTick, lastBreakTick, now, cfg)) {
            return false;
        }
        if (cfg.shield.shieldBreakRegenPenaltySeconds > 0.0D && lastBreakTick != Long.MIN_VALUE) {
            long sinceBreak = now - lastBreakTick;
            if (sinceBreak >= 0L && sinceBreak < cfg.shield.shieldBreakRegenPenaltySeconds * 20.0D) {
                return false;
            }
        }
        return true;
    }

    /**
     * 脱战判定：距上次受伤超过 max(战斗标签, 连携延迟)，且距上次破盾超过破盾惩罚。
     */
    public static boolean outOfCombat(long lastDamageTick, long lastBreakTick, long now, LinkedShieldSettings cfg) {
        double delayTicks = Math.max(cfg.combat.combatTagSeconds, cfg.linkedshield.outOfCombatDelaySeconds) * 20.0D;
        if (lastDamageTick != Long.MIN_VALUE) {
            long elapsed = now - lastDamageTick;
            if (elapsed >= 0L && elapsed < delayTicks) {
                return false;
            }
        }
        if (cfg.shield.shieldBreakRegenPenaltySeconds > 0.0D && lastBreakTick != Long.MIN_VALUE) {
            long sinceBreak = now - lastBreakTick;
            if (sinceBreak >= 0L && sinceBreak < cfg.shield.shieldBreakRegenPenaltySeconds * 20.0D) {
                return false;
            }
        }
        return true;
    }
}

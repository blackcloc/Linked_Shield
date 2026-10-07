package com.linkedshield.api;

import com.linkedshield.config.ConfigManager;
import com.linkedshield.shield.LinkedShieldAttachments;
import com.linkedshield.shield.ShieldData;
import com.linkedshield.shield.ShieldService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * 连携护盾的静态 API。给其他模组读写护盾值用（服务端调用）。
 *
 * <pre>{@code
 * // 给玩家加 20 点护盾（不超过上限）
 * LinkedShieldAPI.addShield(player, 20.0D);
 * // 把上限直接设成 150（会随存档保存）
 * LinkedShieldAPI.setBaseMaxShield(player, 150.0D);
 * // 查询
 * double shield = LinkedShieldAPI.getShield(player);
 * double max = LinkedShieldAPI.getEffectiveMaxShield(player);
 * }</pre>
 *
 * <p>“护盾上限”分两层：</p>
 * <ul>
 *   <li><b>基础上限</b>：存在玩家数据里（{@link #setBaseMaxShield}），会随存档保存；</li>
 *   <li><b>加成</b>：{@link LinkedShieldEvent.MaxShield} 事件 + 物品上的
 *       {@code linkedshield:shield_bonus} 组件，由装备/饰品/药水等动态提供。</li>
 * </ul>
 */
public final class LinkedShieldAPI {

    private LinkedShieldAPI() {
    }

    /** 是否有护盾数据（只对 LivingEntity 且被本模组挂过数据的有效）。 */
    public static boolean hasShield(@Nullable LivingEntity entity) {
        return entity instanceof ServerPlayer;
    }

    /** 当前护盾值；没有护盾时返回 0。 */
    public static double getShield(@Nullable LivingEntity entity) {
        return data(entity) == null ? 0.0D : data(entity).getShield();
    }

    /** 存档里的基础护盾上限（不含事件/物品加成）。 */
    public static double getBaseMaxShield(@Nullable LivingEntity entity) {
        return data(entity) == null ? 0.0D : data(entity).getMaxShield();
    }

    /** 动态加成（事件 + 物品组件），由 {@link #refreshBonuses} 刷新。 */
    public static double getBonusMaxShield(@Nullable LivingEntity entity) {
        return data(entity) == null ? 0.0D : data(entity).getBonusMaxShield();
    }

    /** 实际生效的护盾上限 = 基础上限 + 加成。 */
    public static double getEffectiveMaxShield(@Nullable LivingEntity entity) {
        return data(entity) == null ? 0.0D : ShieldService.effectiveMaxShield(data(entity));
    }

    /** 当前护盾占比（0~1）。 */
    public static double getShieldRatio(@Nullable LivingEntity entity) {
        double max = getEffectiveMaxShield(entity);
        return max <= 0.0D ? 0.0D : Math.clamp(getShield(entity) / max, 0.0D, 1.0D);
    }

    /** 连携是否正在生效（脱战 + 身边有队友 + 满足启动延迟）。 */
    public static boolean isLinkedShieldActive(@Nullable LivingEntity entity) {
        return entity instanceof ServerPlayer player
                && ShieldService.activeLinkedShield(player, ConfigManager.get(),
                        player.level().getServer().getTickCount()) > 0;
    }

    /** 设置当前护盾值（0 ~ 有效上限之间）。 */
    public static void setShield(@Nullable LivingEntity entity, double value) {
        if (entity instanceof ServerPlayer player) {
            ShieldService.setShield(player, value, LinkedShieldEvent.ChangeReason.API);
        }
    }

    /** 加/减护盾值。 */
    public static void addShield(@Nullable LivingEntity entity, double delta) {
        setShield(entity, getShield(entity) + delta);
    }

    /** 直接设置基础上限（会随存档保存）；当前护盾会被夹到新上限内。 */
    public static void setBaseMaxShield(@Nullable LivingEntity entity, double value) {
        if (entity instanceof ServerPlayer player) {
            ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
            double old = data.getShield();
            data.setMaxShield(Math.max(0.0D, value));
            ShieldService.setShield(player, Math.min(old, ShieldService.effectiveMaxShield(data)),
                    LinkedShieldEvent.ChangeReason.API);
            ShieldService.refreshBonuses(player);
        }
    }

    /**
     * 立即重算护盾上限加成。饰品/装备变化后调用它可以立刻生效
     * （平时服务端每 10 tick 也会自动算一次）。
     */
    public static void refreshBonuses(@Nullable LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ShieldService.refreshBonuses(player);
        }
    }

    private static ShieldData data(@Nullable LivingEntity entity) {
        return entity instanceof ServerPlayer player ? player.getData(LinkedShieldAttachments.SHIELD) : null;
    }
}

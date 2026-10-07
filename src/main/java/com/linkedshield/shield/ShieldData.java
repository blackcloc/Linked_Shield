package com.linkedshield.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 单个玩家的护盾状态。挂在玩家实体上的数据附件（Attachment），会随存档持久化。
 *
 * <p>穿盾公式：{@code 穿盾比例 = clamp(1 - 当前护盾 / 最大护盾, minPierceRatio, 1)}</p>
 */
public class ShieldData {

    public static final MapCodec<ShieldData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("shield", 100.0D).forGetter(ShieldData::getShield),
            Codec.DOUBLE.optionalFieldOf("max_shield", 100.0D).forGetter(ShieldData::getMaxShield),
            Codec.LONG.optionalFieldOf("last_damage_tick", Long.MIN_VALUE).forGetter(ShieldData::getLastDamageTick),
            Codec.LONG.optionalFieldOf("last_break_tick", Long.MIN_VALUE).forGetter(ShieldData::getLastBreakTick)
    ).apply(instance, ShieldData::new));

    private double shield;
    private double maxShield;
    /** 最后一次受到伤害的游戏刻（用于脱战判定）。 */
    private long lastDamageTick;
    /** 最后一次破盾的游戏刻。 */
    private long lastBreakTick;
    /** 连携开始成立的游戏刻（不持久化，只用于「连携持续 N 秒后启动」判定）。 */
    private long linkedshieldLinkStart = Long.MIN_VALUE;
    /** 外部来源给的护盾上限加成（事件 + 物品组件，不持久化，每 10 tick 重算）。 */
    private double bonusMaxShield;

    public ShieldData() {
        this(100.0D, 100.0D, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    public ShieldData(double shield, double maxShield, long lastDamageTick, long lastBreakTick) {
        this.shield = shield;
        this.maxShield = maxShield;
        this.lastDamageTick = lastDamageTick;
        this.lastBreakTick = lastBreakTick;
    }

    public static ShieldData of(double shield, double maxShield) {
        return new ShieldData(shield, maxShield, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    public double getShield() {
        return shield;
    }

    public double getMaxShield() {
        return maxShield;
    }

    public long getLastDamageTick() {
        return lastDamageTick;
    }

    public long getLastBreakTick() {
        return lastBreakTick;
    }

    public void setShield(double value) {
        this.shield = Math.max(0.0D, value);
    }

    public void setMaxShield(double value) {
        this.maxShield = Math.max(0.0D, value);
        if (this.shield > this.maxShield) {
            this.shield = this.maxShield;
        }
    }

    public void setLastDamageTick(long tick) {
        this.lastDamageTick = tick;
    }

    public void setLastBreakTick(long tick) {
        this.lastBreakTick = tick;
    }

    /** 当前连携从哪一刻开始（{@link Long#MIN_VALUE} = 当前没有连携）。 */
    public long getLinkedShieldLinkStart() {
        return linkedshieldLinkStart;
    }

    public void setLinkedShieldLinkStart(long tick) {
        this.linkedshieldLinkStart = tick;
    }

    /** 外部（事件 / 物品组件 / 其它模组）给的护盾上限加成。 */
    public double getBonusMaxShield() {
        return bonusMaxShield;
    }

    public void setBonusMaxShield(double bonus) {
        this.bonusMaxShield = bonus;
    }

    /** 护盾百分比 0~1。 */
    public double ratio() {
        if (maxShield <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, shield / maxShield));
    }

    /** 是否已破盾。 */
    public boolean isBroken() {
        return shield <= 0.0D;
    }
}

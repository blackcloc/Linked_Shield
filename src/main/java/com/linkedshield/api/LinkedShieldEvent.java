package com.linkedshield.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 连携护盾对外提供的事件。其他模组只要在自己的 mod 构造器里
 * {@code NeoForge.EVENT_BUS.addListener(...)} 即可接入，<b>不需要依赖编译期接口</b>。
 *
 * <h2>示例 1：饰品/装备按等级加护盾上限</h2>
 * <pre>{@code
 * NeoForge.EVENT_BUS.addListener((LinkedShieldEvent.MaxShield event) -> {
 *     ItemStack chest = event.getPlayer().getItemBySlot(EquipmentSlot.CHEST);
 *     if (chest.is(MyItems.SHIELD_VEST.get())) {
 *         event.addBonus(30.0D);   // 护盾上限 +30
 *     }
 * });
 * }</pre>
 *
 * <h2>示例 2：监听护盾变化（做音效/粒子/UI）</h2>
 * <pre>{@code
 * NeoForge.EVENT_BUS.addListener((LinkedShieldEvent.Changed event) -> {
 *     if (event.getNewValue() <= 0.0D) {
 *         // 破盾了
 *     }
 * });
 * }</pre>
 */
public abstract class LinkedShieldEvent extends Event {

    private final ServerPlayer player;

    protected LinkedShieldEvent(ServerPlayer player) {
        this.player = player;
    }

    /** 事件对应的玩家。 */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 计算“护盾上限”时触发。最终上限 = 基础上限（存档里的值）+ 本事件累加的加成 + 物品组件加成。
     *
     * <p>取消（{@link ICancellableEvent#setCanceled}）表示<b>忽略本次的全部加成</b>，只用基础上限。</p>
     */
    public static class MaxShield extends LinkedShieldEvent implements ICancellableEvent {

        private final double baseValue;
        private double bonus;

        public MaxShield(ServerPlayer player, double baseValue, double bonus) {
            super(player);
            this.baseValue = baseValue;
            this.bonus = bonus;
        }

        /** 存档里的基础护盾上限。 */
        public double getBaseValue() {
            return baseValue;
        }

        /** 当前累加到的加成值（含物品组件给的）。 */
        public double getBonus() {
            return bonus;
        }

        public void setBonus(double bonus) {
            this.bonus = Math.max(-baseValue, bonus);
        }

        /** 追加护盾上限（模组最常用的入口）。 */
        public void addBonus(double amount) {
            setBonus(this.bonus + amount);
        }

        /** 基础 + 加成后的上限。 */
        public double getTotal() {
            return Math.max(0.0D, baseValue + bonus);
        }
    }

    /** 护盾值发生变化（回盾 / 扣盾 / 破盾 / 指令或 API 改动）。 */
    public static class Changed extends LinkedShieldEvent {

        private final double oldValue;
        private final double newValue;
        private final ChangeReason reason;

        public Changed(ServerPlayer player, double oldValue, double newValue, ChangeReason reason) {
            super(player);
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
        }

        public double getOldValue() {
            return oldValue;
        }

        public double getNewValue() {
            return newValue;
        }

        public ChangeReason getReason() {
            return reason;
        }

        /** 变化量（负数 = 被扣）。 */
        public double getDelta() {
            return newValue - oldValue;
        }
    }

    /** 护盾刚好被打空。 */
    public static class Broken extends LinkedShieldEvent {

        private final DamageSource source;
        private final double absorbed;

        public Broken(ServerPlayer player, DamageSource source, double absorbed) {
            super(player);
            this.source = source;
            this.absorbed = absorbed;
        }

        /** 打空护盾的那次伤害（可能为 null，例如指令清空）。 */
        public DamageSource getSource() {
            return source;
        }

        /** 这次伤害被护盾吃掉的量。 */
        public double getAbsorbed() {
            return absorbed;
        }
    }

    /** 护盾变化原因。 */
    public enum ChangeReason {
        /** 连携回盾。 */
        REGEN,
        /** 挡下伤害。 */
        DAMAGE,
        /** 指令 / 配置改动。 */
        COMMAND,
        /** 其他模组通过 API 改动。 */
        API
    }
}

package com.linkedshield.shield;

import com.linkedshield.api.LinkedShieldDataComponents;
import com.linkedshield.api.LinkedShieldEvent;
import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.party.Party;
import com.linkedshield.party.PartyManager;
import com.linkedshield.party.PartyStore;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 护盾运行时逻辑：伤害分类结算（{@link DamageClassifier} + {@link ShieldMath}）+ 小队连携回盾
 * + 护盾上限加成（物品组件 / 其他模组事件）。
 *
 * <p>结算时机在护甲/附魔减伤之后的 {@link LivingDamageEvent.Pre}。</p>
 */
public final class ShieldService {

    private static final Logger LOG = LogUtils.getLogger();
    /** 每多少 tick 重算一次“外部护盾上限加成”。 */
    private static final int BONUS_REFRESH_INTERVAL = 10;

    private ShieldService() {
    }

    /* ------------------------------------------------------------------ 伤害结算 */

    /**
     * 战斗标记：只要挨了伤害就算进入战斗（驱动连携延迟），与护盾是否挡下无关。
     * 这一步仍在护甲结算之前，只用来打时间戳。
     *
     * <p>进来先做小队友军伤害免疫：{@code party.friendlyFire = false}（默认）时，
     * 同队玩家之间造成不了任何伤害 —— 事件直接取消，连战斗标记都不会打。</p>
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // ServerPlayer 保证只在服务端结算
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        if (isFriendlyFire(player, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        data.setLastDamageTick(player.level().getServer().getTickCount());
    }

    /* ------------------------------------------------------------------ 友军伤害免疫 */

    /**
     * 是否属于“同小队友军伤害”：配置 {@code party.friendlyFire = false} 且
     * 受害者与伤害归属玩家互为队友时返回 true。
     */
    public static boolean isFriendlyFire(ServerPlayer victim, DamageSource source) {
        MinecraftServer server = victim.level().getServer();
        return server != null && blocksFriendlyFire(
                ConfigManager.get().party.friendlyFire,
                PartyManager.store(server),
                victim.getUUID(),
                attackerId(source));
    }

    /**
     * 友军免疫的纯判定（不碰世界，自检可无头调用）：
     * 只有「配置关闭友伤 + 有明确的攻击者 + 攻击者不是自己 + 两人确实同队」才返回 true。
     */
    public static boolean blocksFriendlyFire(boolean friendlyFireAllowed, PartyStore store,
                                             UUID victim, UUID attacker) {
        if (friendlyFireAllowed) {
            return false; // 配置允许友军伤害
        }
        if (store == null || victim == null || attacker == null || victim.equals(attacker)) {
            return false; // 无归属玩家 / 自己打自己，交给原版处理
        }
        return store.areTeammates(victim, attacker);
    }

    /**
     * 从伤害来源里解析“这一击算谁的”：先看 {@link DamageSource#getEntity()}，
     * 再看 {@link DamageSource#getDirectEntity()}；投射物与被驯服/有主实体再顺着 owner 往上找。
     * 解析不到玩家时返回 {@code null}。
     */
    private static UUID attackerId(DamageSource source) {
        UUID direct = playerId(source.getEntity());
        return direct != null ? direct : playerId(source.getDirectEntity());
    }

    private static UUID playerId(Entity entity) {
        // owner 链理论上有限，这里加个上限防止模组实体互相持有导致死循环
        for (int hop = 0; entity != null && hop < 4; hop++) {
            if (entity instanceof Player player) {
                return player.getUUID();
            }
            if (entity instanceof Projectile projectile) {
                entity = projectile.getOwner();
            } else if (entity instanceof OwnableEntity ownable) {
                entity = ownable.getOwner();
            } else {
                entity = null;
            }
        }
        return null;
    }

    /**
     * 护盾真正结算的位置：{@link LivingDamageEvent.Pre} 在
     * 护甲 / 附魔 / 状态效果减伤之后、吸收之前触发，所以护盾扣的是
     * <b>实际会作用到血量上的伤害</b>，不会替护甲买单。
     */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float amount = event.getNewDamage();
        if (amount <= 0.0F) {
            return;
        }
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        double maxShield = effectiveMaxShield(data);
        if (maxShield <= 0.0D) {
            return;
        }

        LinkedShieldSettings cfg = ConfigManager.get();
        DamageClassifier.Category category = DamageClassifier.categorize(event.getSource(), cfg);
        DamageClassifier.Behavior behavior = DamageClassifier.behaviorFor(category, cfg);
        if (behavior == DamageClassifier.Behavior.NONE) {
            return;
        }

        ShieldMath.Outcome outcome = ShieldMath.apply(behavior,
                data.getShield(), maxShield, amount,
                cfg.shield.minPierceRatio, cfg.shield.rangedDamageFullyAbsorbed, cfg.shield.meleeDrainsShield);

        double absorbed = Math.max(0.0D, data.getShield() - outcome.shield());
        setShield(player, outcome.shield(), LinkedShieldEvent.ChangeReason.DAMAGE, null, absorbed);
        event.setNewDamage(outcome.damage());
        if (outcome.broke()) {
            data.setLastBreakTick(player.level().getServer().getTickCount());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("[LinkedShield] {} 受伤 {}：类别 {} / {}，实际伤害 {} → {}",
                    player.getName().getString(), DamageClassifier.idOf(event.getSource()),
                    category.label(), behavior, amount, outcome.damage());
        }
    }

    /* ------------------------------------------------------------------ 护盾值写入 */

    /** 设置护盾值并广播事件（护盾变化 / 破盾）。 */
    public static void setShield(ServerPlayer player, double value, LinkedShieldEvent.ChangeReason reason) {
        setShield(player, value, reason, null, 0.0D);
    }

    private static void setShield(ServerPlayer player, double value,
                                  LinkedShieldEvent.ChangeReason reason,
                                  net.minecraft.world.damagesource.DamageSource source, double absorbed) {
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        double old = data.getShield();
        double next = Math.clamp(value, 0.0D, Math.max(0.0D, effectiveMaxShield(data)));
        if (Math.abs(next - old) < 1.0E-4D && !(old > 0.0D && next <= 0.0D)) {
            return;
        }
        data.setShield(next);
        if (next <= 0.0D && old > 0.0D) {
            NeoForge.EVENT_BUS.post(new LinkedShieldEvent.Broken(player, source, absorbed));
            if (reason == LinkedShieldEvent.ChangeReason.DAMAGE) {
                player.displayClientMessage(Component.translatable("linkedshield.shield.broken"), true);
            }
        }
        NeoForge.EVENT_BUS.post(new LinkedShieldEvent.Changed(player, old, next, reason));
    }

    /* ------------------------------------------------------------------ 护盾上限加成 */

    /** 实际生效的护盾上限 = 基础上限 + 外部加成。 */
    public static double effectiveMaxShield(ShieldData data) {
        return Math.max(0.0D, data.getMaxShield() + data.getBonusMaxShield());
    }

    public static double effectiveMaxShield(ServerPlayer player) {
        return effectiveMaxShield(player.getData(LinkedShieldAttachments.SHIELD));
    }

    /** 当前护盾占比（0~1），用有效上限计算。 */
    public static double ratio(ShieldData data) {
        double max = effectiveMaxShield(data);
        return max <= 0.0D ? 0.0D : Math.clamp(data.getShield() / max, 0.0D, 1.0D);
    }

    /**
     * 重算“外部护盾上限加成”：
     * ① 触发 {@link LinkedShieldEvent.MaxShield}，其他模组（饰品、装备、药水…）在这里加值；
     * ② 扫描背包/装备里带 {@code linkedshield:shield_bonus} 组件的物品。
     */
    public static void refreshBonuses(ServerPlayer player) {
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        double base = data.getMaxShield();
        double itemBonus = itemBonus(player);
        LinkedShieldEvent.MaxShield event = new LinkedShieldEvent.MaxShield(player, base, itemBonus);
        NeoForge.EVENT_BUS.post(event);
        double bonus = event.isCanceled() ? 0.0D : event.getBonus();
        if (Math.abs(bonus - data.getBonusMaxShield()) > 1.0E-4D) {
            data.setBonusMaxShield(bonus);
            // 上限变小时把当前护盾夹回去
            if (data.getShield() > effectiveMaxShield(data)) {
                setShield(player, effectiveMaxShield(data), LinkedShieldEvent.ChangeReason.API);
            }
        }
    }

    /** 背包（含快捷栏）+ 护甲 + 副手物品上 {@code linkedshield:shield_bonus} 组件的总和。 */
    public static double itemBonus(ServerPlayer player) {
        double bonus = 0.0D;
        // 注意：Inventory#getContainerSize() 在 1.21.9+ 已包含装备槽，
        // 所以主背包要按“非装备物品”遍历，避免装备/副手被算两次。
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            bonus += stackBonus(stack);
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            bonus += stackBonus(player.getItemBySlot(slot));
        }
        return bonus;
    }

    private static double stackBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        Double value = stack.get(LinkedShieldDataComponents.SHIELD_BONUS.get());
        return value == null ? 0.0D : value;
    }

    /* ------------------------------------------------------------------ 连携回盾 */

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        LinkedShieldSettings cfg = ConfigManager.get();
        long now = server.getTickCount();
        boolean refresh = now % BONUS_REFRESH_INTERVAL == 0L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (refresh) {
                refreshBonuses(player);
            }
            if (cfg.linkedshield.enabled) {
                tickPlayer(server, player, cfg, now);
            }
        }
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayer player, LinkedShieldSettings cfg, long now) {
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        double maxShield = effectiveMaxShield(data);
        if (maxShield <= 0.0D) {
            return;
        }
        if (cfg.linkedshield.stopWhenFull && data.getShield() >= maxShield) {
            return;
        }
        int nearby = activeLinkedShield(player, cfg, now);
        double rate = ShieldMath.linkedshieldRate(nearby, cfg);
        if (rate <= 0.0D) {
            return;
        }
        setShield(player, Math.min(maxShield, data.getShield() + rate / 20.0D),
                LinkedShieldEvent.ChangeReason.REGEN);
    }

    /**
     * 当前连携状态：0 = 没有连携（或还没到启动时间），&gt;0 = 身边生效的队友数量。
     * 条件：连携开启、半径内有队友（同维度，若配置要求）、
     * 连携人数 &gt; 1 并持续 {@code linkedshield.startDelaySeconds} 秒（默认 2 秒）、
     * 破盾后的额外延迟；{@code requireOutOfCombat} 为 true 时还要求脱战。
     * HUD 上的“盾牌图标”和回盾速率都用这个值。
     */
    public static int activeLinkedShield(ServerPlayer player, LinkedShieldSettings cfg, long now) {
        if (!cfg.linkedshield.enabled) {
            return 0;
        }
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        if (effectiveMaxShield(data) <= 0.0D) {
            return 0;
        }
        int nearby = countNearbyTeammates(player.level().getServer(), player, cfg);
        if (nearby <= 0) {
            // 连携断了，重新计时
            data.setLinkedShieldLinkStart(Long.MIN_VALUE);
            return 0;
        }
        if (data.getLinkedShieldLinkStart() == Long.MIN_VALUE) {
            data.setLinkedShieldLinkStart(now);
        }
        if (!ShieldMath.linkedshieldReady(data.getLinkedShieldLinkStart(),
                data.getLastDamageTick(), data.getLastBreakTick(), now, cfg)) {
            return 0;
        }
        return nearby;
    }

    /** 供指令展示：连携是否已经满足启动条件。 */
    public static boolean linkedshieldReady(ServerPlayer player) {
        return activeLinkedShield(player, ConfigManager.get(), player.level().getServer().getTickCount()) > 0;
    }

    /** 统计半径内、同小队的在线队友数量。 */
    public static int countNearbyTeammates(MinecraftServer server, ServerPlayer player, LinkedShieldSettings cfg) {
        PartyStore store = PartyManager.store(server);
        Party party = store.partyOf(player.getUUID());
        if (party == null || party.size() <= 1) {
            return 0;
        }
        double radiusSqr = cfg.linkedshield.radius * cfg.linkedshield.radius;
        int count = 0;
        for (UUID id : party.getMembers()) {
            if (id.equals(player.getUUID())) {
                continue;
            }
            ServerPlayer teammate = server.getPlayerList().getPlayer(id);
            if (teammate == null || teammate.isRemoved() || !teammate.isAlive()) {
                continue;
            }
            if (cfg.linkedshield.requireSameDimension
                    && !teammate.level().dimension().equals(player.level().dimension())) {
                continue;
            }
            if (player.distanceToSqr(teammate) <= radiusSqr) {
                count++;
            }
        }
        return count;
    }

    /** 当前玩家的附近队友数量（供指令展示）。 */
    public static int nearbyTeammates(ServerPlayer player) {
        return countNearbyTeammates(player.level().getServer(), player, ConfigManager.get());
    }

    /** 当前是否处于脱战状态（供指令展示）。 */
    public static boolean outOfCombat(ServerPlayer player) {
        ShieldData data = player.getData(LinkedShieldAttachments.SHIELD);
        long now = player.level().getServer().getTickCount();
        return ShieldMath.outOfCombat(data.getLastDamageTick(), data.getLastBreakTick(), now, ConfigManager.get());
    }
}

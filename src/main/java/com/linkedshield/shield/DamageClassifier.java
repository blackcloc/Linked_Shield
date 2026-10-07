package com.linkedshield.shield;

import com.linkedshield.config.LinkedShieldSettings;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 把原版（以及模组）的伤害类型归类，决定护盾怎么处理这次伤害。
 *
 * <p>判定依据是<b>伤害类型 + 原版标签</b>：</p>
 * <ul>
 *   <li><b>远程</b>（默认 {@code FULL} 全额抵挡）：{@code #minecraft:is_projectile} 里的投射物（箭、三叉戟、火球、
 *       凋灵头颅、雪球、风弹）、{@code lightning_bolt}（闪电）、{@code thorns}（反伤）、配置补的 {@code spit}（羊驼口水）</li>
 *   <li><b>持续伤害</b>（{@code FULL}）：火焰系、岩浆、{@code magic}（毒）、{@code wither}（凋零效果）、{@code freeze}</li>
 *   <li><b>法术/声波</b>（{@code FULL}）：{@code sonic_boom}（监守者声波）、{@code indirect_magic}（守卫者激光）、{@code dragon_breath}</li>
 *   <li><b>近战</b>（默认 {@code PIERCE} 百分比穿盾）：玩家/生物攻击、重锤、蜜蜂、长枪，
 *       以及仙人掌、甜浆果丛、挤压、摔落、末影珍珠摔落、飞行撞击、石笋、坠落物（铁砧/方块/钟乳石）</li>
 *   <li><b>爆炸</b>（{@code PIERCE}，与近战同一套公式）：TNT、床、水晶、爬行者、烟花火箭</li>
 *   <li><b>环境</b>（{@code NONE} 不吃护盾）：溺水、饥饿、卡墙、掉出世界、边界外、dry_out、generic 等</li>
 * </ul>
 *
 * <p>每一类都能在 settings.json 的 {@code damageRules} 里单独设置
 * {@code FULL} / {@code PIERCE} / {@code NONE}，也可以用 {@code extraXxxTypes} 把模组伤害类型加进某一类。</p>
 */
public final class DamageClassifier {

    /** 伤害类别。 */
    public enum Category {
        PROJECTILE("远程"),
        DOT("持续伤害"),
        MAGIC("法术/声波"),
        MELEE("近战"),
        EXPLOSION("爆炸"),
        ENVIRONMENT("环境"),
        OTHER("其它/未识别");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 护盾对某类伤害的处理方式。 */
    public enum Behavior {
        /** 全额抵挡：护盾足够则完全免伤，并扣掉等量护盾；不够时溢出部分打到血量。 */
        FULL,
        /** 百分比穿盾：穿盾比例 = clamp(1 - 当前护盾/最大护盾, minPierceRatio, 1)。 */
        PIERCE,
        /** 不吃护盾：完全按原版结算。 */
        NONE;

        public static Behavior parse(String value, Behavior fallback) {
            if (value == null) {
                return fallback;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    /**
     * 近战：生物/玩家直接攻击 + 接触与撞击类（仙人掌、甜浆果丛、挤压、摔落、末影珍珠、
     * 飞行撞击、石笋）+ 坠落物（铁砧、下落方块、钟乳石）。
     */
    private static final Set<String> MELEE_TYPES = Set.of(
            "minecraft:player_attack",
            "minecraft:mob_attack",
            "minecraft:mob_attack_no_aggro",
            "minecraft:mace_smash",
            "minecraft:sting",
            "minecraft:spear",
            "minecraft:cactus",
            "minecraft:sweet_berry_bush",
            "minecraft:cramming",
            "minecraft:fall",
            "minecraft:ender_pearl",
            "minecraft:fly_into_wall",
            "minecraft:stalagmite",
            "minecraft:falling_anvil",
            "minecraft:falling_block",
            "minecraft:falling_stalactite");

    /** 法术 / 声波（原版里都绕过护甲）。 */
    private static final Set<String> MAGIC_TYPES = Set.of(
            "minecraft:indirect_magic",
            "minecraft:sonic_boom",
            "minecraft:dragon_breath");

    /** 持续伤害（火焰系 + 毒 + 凋零 + 冰冻）。 */
    private static final Set<String> DOT_TYPES = Set.of(
            "minecraft:in_fire",
            "minecraft:on_fire",
            "minecraft:campfire",
            "minecraft:hot_floor",
            "minecraft:lava",
            "minecraft:magic",
            "minecraft:wither",
            "minecraft:freeze");

    /** 原版 is_projectile 标签没覆盖、但按“远程”处理的类型。 */
    private static final Set<String> RANGED_EXTRA_TYPES = Set.of(
            "minecraft:lightning_bolt",
            "minecraft:thorns");

    /** 环境伤害（既不是近战也不是远程的持续/自然伤害）。 */
    private static final Set<String> ENVIRONMENT_TYPES = Set.of(
            "minecraft:drown",
            "minecraft:starve",
            "minecraft:in_wall",
            "minecraft:out_of_world",
            "minecraft:outside_border",
            "minecraft:generic",
            "minecraft:generic_kill",
            "minecraft:dry_out");

    private DamageClassifier() {
    }

    /** 伤害类型的注册名，例如 {@code minecraft:arrow}。取不到时返回空串。 */
    public static String idOf(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse("");
    }

    /** 归类一次伤害。 */
    public static Category categorize(DamageSource source, LinkedShieldSettings cfg) {
        LinkedShieldSettings.DamageRules rules = cfg.damageRules;

        if (!rules.useDamageTypeRules) {
            // 兼容模式：退回“看直接实体”的旧判定
            return fallbackByEntity(source);
        }

        String id = idOf(source);

        // 1) 配置里的额外归类优先
        if (matchesAny(rules.extraProjectileTypes, id)) {
            return Category.PROJECTILE;
        }
        if (matchesAny(rules.extraMeleeTypes, id)) {
            return Category.MELEE;
        }
        if (matchesAny(rules.extraDotTypes, id)) {
            return Category.DOT;
        }

        // 2) 原版标签（爆炸优先于投射物：烟花火箭同时算投射物实体，但它是爆炸伤害）
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return Category.EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return Category.PROJECTILE;
        }

        // 3) 原版标签覆盖不到的显式集合
        if (RANGED_EXTRA_TYPES.contains(id)) {
            return Category.PROJECTILE;
        }
        if (MELEE_TYPES.contains(id)) {
            return Category.MELEE;
        }
        if (DOT_TYPES.contains(id)) {
            return Category.DOT;
        }
        if (MAGIC_TYPES.contains(id)) {
            return Category.MAGIC;
        }
        if (ENVIRONMENT_TYPES.contains(id)) {
            return Category.ENVIRONMENT;
        }

        // 4) 未识别（多半是模组伤害）：可选按直接实体回退，避免模组内容完全不吃护盾
        if (rules.fallbackToEntityType) {
            Category byEntity = fallbackByEntity(source);
            if (byEntity != Category.OTHER) {
                return byEntity;
            }
        }
        return Category.OTHER;
    }

    private static Category fallbackByEntity(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return Category.PROJECTILE;
        }
        if (direct instanceof LivingEntity) {
            return Category.MELEE;
        }
        return Category.OTHER;
    }

    /** 取某一类在配置里的处理方式。 */
    public static Behavior behaviorFor(Category category, LinkedShieldSettings cfg) {
        LinkedShieldSettings.DamageRules rules = cfg.damageRules;
        return switch (category) {
            case PROJECTILE -> Behavior.parse(rules.projectileBehavior, Behavior.FULL);
            case DOT -> Behavior.parse(rules.dotBehavior, Behavior.FULL);
            case MAGIC -> Behavior.parse(rules.magicBehavior, Behavior.FULL);
            case MELEE -> Behavior.parse(rules.meleeBehavior, Behavior.PIERCE);
            case EXPLOSION -> Behavior.parse(rules.explosionBehavior, Behavior.PIERCE);
            case ENVIRONMENT -> Behavior.parse(rules.environmentBehavior, Behavior.NONE);
            case OTHER -> Behavior.parse(rules.otherBehavior, Behavior.NONE);
        };
    }

    /** 逗号/分号分隔的 id 列表里是否包含该伤害类型。 */
    private static boolean matchesAny(String list, String id) {
        if (list == null || list.isBlank() || id.isEmpty()) {
            return false;
        }
        return splitList(list).contains(id);
    }

    /** 供指令/日志展示用：解析出的 id 集合。 */
    public static Set<String> splitList(String list) {
        Set<String> result = new LinkedHashSet<>();
        if (list == null) {
            return result;
        }
        Arrays.stream(list.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }
}

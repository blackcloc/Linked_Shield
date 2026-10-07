package com.linkedshield.config;

/**
 * 全部可配置项。字段名即 config/linkedshield/settings.json 中的键名，
 * HTML 配置编辑器（config/linkedshield/config_editor.html）也直接读写同一份 JSON。
 */
public class LinkedShieldSettings {

    /** 结构版本，用于将来迁移。 */
    public int version = 1;

    public Party party = new Party();
    public Shield shield = new Shield();
    public DamageRules damageRules = new DamageRules();
    public LinkedShield linkedshield = new LinkedShield();
    public Combat combat = new Combat();
    public Hud hud = new Hud();
    public Misc misc = new Misc();

    /** 小队规则 */
    public static class Party {
        /** 一个小队最多多少人（含队长）。 */
        public int maxPartySize = 6;
        /** 组队邀请的有效秒数。 */
        public int inviteTimeoutSeconds = 60;
        /** 是否允许跨维度组队（连携回盾仍受 requireSameDimension 限制）。 */
        public boolean allowCrossDimension = true;
        /** 队友之间是否关闭友军伤害。 */
        public boolean friendlyFire = false;
        /** 组队界面里“附近玩家”的判定半径（方块/米）。 */
        public double nearbyRadius = 10.0;
    }

    /** 护盾数值与减伤规则 */
    public static class Shield {
        /** 新玩家 / 重生后的默认护盾上限。 */
        public double defaultMaxShield = 100.0;
        /** 新玩家 / 重生后的默认当前护盾值。 */
        public double defaultCurrentShield = 100.0;
        /** 满护盾时仍然会穿透的最小伤害比例（示例：0.10 = 最低 10% 穿盾）。 */
        public double minPierceRatio = 0.10;
        /** 远程伤害是否被护盾全额吸收（true 时护盾足够则完全免伤）。 */
        public boolean rangedDamageFullyAbsorbed = true;
        /** 近战被护盾挡下的部分是否消耗护盾值。 */
        public boolean meleeDrainsShield = true;
        /** 护盾归零后，连携回复额外延迟的秒数。 */
        public double shieldBreakRegenPenaltySeconds = 3.0;
    }

    /** 伤害分类 -> 护盾处理方式 */
    public static class DamageRules {
        /**
         * true：按“伤害类型 + 原版标签”分类（推荐）。
         * false：退回旧版“看直接实体是投射物还是活体”的判定。
         */
        public boolean useDamageTypeRules = true;
        /** 投射物（箭、三叉戟、火球、凋灵头颅、雪球、风弹…）：FULL / PIERCE / NONE。 */
        public String projectileBehavior = "FULL";
        /** 近战攻击（玩家、生物、重锤、蜜蜂、长枪）：默认按护盾百分比穿盾。 */
        public String meleeBehavior = "PIERCE";
        /** 爆炸（TNT、爬行者、末影水晶、烟花火箭、重生锚）—— 与近战同一套百分比穿盾公式。 */
        public String explosionBehavior = "PIERCE";
        /** 持续伤害（火焰、岩浆、毒/魔法、凋零、冰冻）—— 默认按“远程”全额抵挡。 */
        public String dotBehavior = "FULL";
        /** 法术与声波（守卫者激光、监守者声波、龙息）—— 默认也按“远程”全额抵挡。 */
        public String magicBehavior = "FULL";
        /** 环境伤害（溺水、饥饿、卡墙、掉出世界、边界外等）。 */
        public String environmentBehavior = "NONE";
        /** 其它/未识别的伤害类型。 */
        public String otherBehavior = "NONE";
        /** 额外算作投射物的伤害类型（逗号分隔）。原版 minecraft:spit（羊驼口水）不在此标签里，故默认补上。 */
        public String extraProjectileTypes = "minecraft:spit";
        /** 额外算作近战攻击的伤害类型（逗号分隔）。 */
        public String extraMeleeTypes = "";
        /** 额外算作持续伤害的伤害类型（逗号分隔，例如某个模组的 modid:my_dot）。 */
        public String extraDotTypes = "";
        /** 未识别的伤害类型（多半来自其它模组）是否回退成“看直接实体”的判定。 */
        public boolean fallbackToEntityType = true;
    }

    /** 小队连携（LinkedShield）自动回盾 */
    public static class LinkedShield {
        public boolean enabled = true;
        /** 附近有 1 名队友时的回复速率（点/秒）。 */
        public double rateOneMember = 2.5;
        /** 附近有 2 名队友时的回复速率（点/秒）。 */
        public double rateTwoMembers = 3.75;
        /** 附近有 3 名及以上队友时的回复速率（点/秒）。 */
        public double rateThreeOrMore = 5.0;
        /** 单人（附近没有队友）时的回复速率；默认 0 = 只有队友在身边才回盾。 */
        public double soloRate = 0.0;
        /** 判定“附近”的半径（方块）。 */
        public double radius = 10.0;
        /**
         * 连携人数 &gt; 1（即身边至少 1 名队友）需要持续多少秒才开始回盾。
         * 默认 2 秒：连携刚成立的瞬间不会立刻回盾。
         */
        public double startDelaySeconds = 2.0;
        /**
         * 是否还要求“脱战”才能回盾。
         * 默认 false：启动条件只看“连携 &gt; 1 持续 startDelaySeconds 秒”。
         */
        public boolean requireOutOfCombat = false;
        /** 脱离战斗多少秒后才开始连携回复。 */
        public double outOfCombatDelaySeconds = 2.0;
        /** 是否要求队友处于同一维度才触发连携。 */
        public boolean requireSameDimension = true;
        /** 是否只在护盾未满时回复。 */
        public boolean stopWhenFull = true;
    }

    /** 战斗状态判定 */
    public static class Combat {
        /** 受到/造成伤害后维持“战斗中”的秒数。 */
        /** 战斗状态持续秒数：最后一次受伤后这么久算脱战（默认 2 秒）。 */
        public double combatTagSeconds = 2.0;
    }

    /** 客户端 HUD 布局 */
    public static class Hud {
        /** HUD 总开关。 */
        public boolean enabled = true;
        public PartyList partyList = new PartyList();
        public SelfShield selfShield = new SelfShield();
        public LinkedShieldIcon linkedshieldIcon = new LinkedShieldIcon();
    }

    /** 连携生效时，血条旁边的小图标（蓝色底 + 握手 + 右下角连携人数） */
    public static class LinkedShieldIcon {
        public boolean visible = true;
        public int offsetX = 0;
        public int offsetY = 0;
        /** 图标缩放（1.0 = 16x16 像素）。 */
        public double scale = 1.0;
        /** 是否在图标右下角显示连携人数。 */
        public boolean showCount = true;
    }

    /** 屏幕最右侧的 FF14 风格队友面板 */
    public static class PartyList {
        public boolean visible = true;
        /** 锚点：LEFT（默认，屏幕最左侧）或 RIGHT。 */
        public String anchor = "LEFT";
        /** 整体缩放（1.0 = 原始大小，越小越紧凑）。 */
        public double scale = 0.8;
        /** 距离屏幕边缘的水平像素偏移。 */
        public int offsetX = 6;
        /** 距离屏幕顶部的垂直像素偏移。 */
        public int offsetY = 34;
        /** 单个队友行的宽度（缩放前）。 */
        public int rowWidth = 96;
        /** 单个队友行的高度（缩放前）。 */
        public int rowHeight = 18;
        /** 行间距。 */
        public int rowGap = 1;
        /** 最多显示多少行。 */
        public int maxRows = 8;
        /** 名字显示位置：ABOVE_BAR（血条上方）、INSIDE_BAR（血条内）、LEFT（血条左侧）。 */
        public String namePosition = "ABOVE_BAR";
        public int nameOffsetX = 0;
        public int nameOffsetY = 0;
        /** 是否在名字下方显示护盾条。 */
        public boolean showShieldBar = true;
        /** 是否显示 1/2/3/4 序号（FF14 风格）。 */
        public boolean showMemberIndex = true;
        /** 面板背景不透明度 0~1。 */
        public double backgroundOpacity = 0.55;
        /** 只有自己也在小队中时才显示面板。 */
        public boolean hideWhenSolo = true;
        /** 排序：LEADER_FIRST / NAME / DISTANCE。 */
        public String sortMode = "LEADER_FIRST";
    }

    /** 自身血条上方的护盾条 */
    public static class SelfShield {
        public boolean visible = true;
        /** ABOVE_HEALTH / BELOW_HEALTH / ABOVE_HOTBAR / CUSTOM。 */
        public String anchor = "ABOVE_HEALTH";
        public int offsetX = 0;
        public int offsetY = -7;
        public int width = 64;
        public int height = 4;
        /** 是否显示护盾条。 */
        public boolean showBar = true;
        /** 是否显示数值文本。 */
        public boolean showText = true;
        /** 文本位置：RIGHT / LEFT / CENTER。 */
        public String textPosition = "RIGHT";
        /** 条的颜色（十六进制）。 */
        public String barColor = "#4FC3F7";
        /** 护盾量的显示方式：CURRENT（默认，只显示实时数值）/ CURRENT_MAX / RATIO。 */
        public String numberFormat = "CURRENT";
    }

    /** 其它 */
    public static class Misc {
        /** 是否生成 HTML 配置编辑器。 */
        public boolean generateHtmlEditor = true;
        /** 聊天栏显示的配置目录（相对游戏目录）。 */
        public String configSubDirectory = "config/linkedshield";
    }
}

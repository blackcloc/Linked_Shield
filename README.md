# 连携护盾（Linked Shield）v1.0.4
[简体中文](README.md) | [English](README.en.md)

> 作者：**blackcloc** · modid `linkedshield` · **Minecraft 1.21.11 + NeoForge 21.11.x**

FF14 风格小队组队 + 战锤40k暗潮护盾 / 连携回盾模组，界面参考 FF14 的简洁队友列表。

- 屏幕**最左侧**显示队友名字 + 血条（FF14 风格：序号、队长金色侧条、血量渐变；不显示距离）
- 队友有**状态效果**时，血条下方显示一排**缩小的效果图标**（原版效果图标与配色、剩余不到 10 秒时和原版一样闪烁，跟随面板缩放；每名队友最多 8 条）
- 玩家**血条正上方**显示自己的护盾条；连携生效时血条左侧显示**蓝色盾牌图标**（右下角是连携人数，含自己）
- **投射物**与**持续伤害**（火焰/岩浆/毒/凋零/冰冻）、**法术声波**（如监守者声波、守卫者激光）由护盾全额抵挡；**近战**与**爆炸**按护盾百分比穿盾
- 结算发生在护甲/附魔减伤之后，护盾不会替护甲买单
- **小队连携**：连携人数 > 1 并持续 **2 秒**后开始自动回盾，队友越多越快（半径 10 格，默认不再要求脱战；脱战判定 = 受伤后 2 秒）
- **友军伤害免疫**：`party.friendlyFire` 默认 false —— 同一小队的玩家之间打不出伤害（伤害在护甲结算前就被取消，连“战斗中”标记都不会打上）；设成 true 才允许友伤
- 按 **U** 打开组队界面：队伍名 + 队内玩家（自己标为“（你）”）+ 10 格内附近玩家一键邀请
- 收到邀请时右上角出现小点，界面里可直接接受/拒绝
- 首次运行在 `config/linkedshield/` 生成 `settings.json` 和一个**可点击打开的 HTML 配置编辑器**

---

## 1. 环境与构建

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| NeoForge | 21.11.45（`gradle.properties` 里 `neo_version`） |
| Java | 21（必须，Mojang 在 1.21.11 就用 21） |
| Gradle | 9.6（工程自带 wrapper） |
| 构建插件 | ModDevGradle 2.0.148 |
| 映射 | Parchment 1.21.11 / 2025.12.20 |

IDEA 里直接打开本目录（Gradle 工程），等同步后即可：

```bash
./gradlew build          # 编译并打包 build/libs/linkedshield-1.0.4.jar
./gradlew runClient      # 启动带模组的客户端
./gradlew runServer      # 启动带模组的服务端（--nogui）
./gradlew runSelfTest    # 无人值守自检：跑完护盾/小队/配置/伤害分类检查后自动关服
./gradlew runHudDemo     # 单人预览 HUD 布局（假队友数据 + 假邀请）
./gradlew runHudDemo -PquickPlayWorld=world              # 预览并直接进入名为 world 的存档
./gradlew runHudDemo -PquickPlayWorld=world -PguiDemo    # 进世界后自动打开一次组队界面（截图用）
./gradlew runHudDemo -PquickPlayWorld=world -PdamageTest # 顺便跑伤害管线实测（掉血/扣盾实测值进聊天栏）
```

> `runSelfTest` 用 `-Dlinkedshield.selftest=true` 启动服务端，进服 40 tick 后执行 **170 项断言**
> （配置生成、默认值、语言文件规范、可点击链接、小队增删改名、同队友判定、**友军伤害免疫判定**、存档 Codec 往返、UI/动作包往返（含效果列表）、穿盾公式、
> 脱战 2 秒/连携启动条件、50 个伤害类型的分类与七类处理方式、拓展接口/物品组件），
> 日志里输出 `[LinkedShield][SELFTEST] PASSED 全部 170 项检查`；任何一项失败都会以退出码 1 结束，gradle 任务直接失败。
>
> `-PdamageTest` 用 `-Dlinkedshield.damagetest=true`，玩家进服后**真的打 17 组伤害 + 1 组接口测试**，
> 把「掉血 / 扣盾」实测值打到日志和聊天栏，并断言 38 项。

### 实际跑出来的效果

| 游戏内 HUD（盾牌图标 + 连携人数） | 组队界面（按 U） | 配置编辑器：数值在上 | 编辑器：下方 1:1 预览 + 原版参照 |
| --- | --- | --- | --- |
| ![HUD](docs/hud-ingame.png) | ![组队界面](docs/party-gui.png) | ![编辑器](docs/config-editor.png) | ![预览](docs/config-editor-preview.png) |

左图：左上角是队友面板（更小、无距离），血条左侧是**蓝色盾牌图标**，右下角数字 4 = 连携人数（3 名队友 + 自己），
右上角是收到邀请时的小点，血条上方是护盾条（**只显示实时数值 68**，不显示上限/百分比）。
第二张：组队界面 —— 左边「我的小队」（队伍名 + 自己“（你）” + 队内玩家 + 踢出/改名/离开/解散），
右边「附近玩家（10 格内）」（邀请 / 已有队伍），顶部是邀请横幅的接受与拒绝；界面里不显示血条与护盾值。
后两张：首次启动生成的 `config_editor.html` —— 上面是所有数值，**HUD 预览放在数值下面**，
按原版 1:1 坐标画（生命/饥饿/经验/快捷栏做参照物）；右上角有 🇨🇳 / 🇺🇸 / 🇬🇧 / 🇷🇺 国旗按钮，
点了整套文字立刻换语言（`docs/config-editor-ru.png` 是同一个页面切成俄语的样子）。

---

## 2. 配置文件（HTML 编辑器）

首次启动会在**游戏目录**下生成：

```
config/linkedshield/settings.json        ← 唯一配置来源（JSON）
config/linkedshield/config_editor.html   ← 可视化配置编辑器
```

打开方式（任选其一）：

1. 游戏内执行 `/linkedshield config`，点聊天栏里给出的文件名（单人/局域网主机有效）
2. 直接去 `config/linkedshield/` 双击 `config_editor.html`

> 1.0.2 起**进游戏不再往聊天栏发任何提示**（早期版本会打印一条可点击的配置页链接）。
> 配置页随时可以用上面的方式打开；想每次进服都被提醒的话自己加个数据包/命令方块即可。

编辑器里能改（含实时 HUD 预览）：

| 配置项 | 说明 | 默认 |
| --- | --- | --- |
| `party.maxPartySize` | 小队最大人数 | 6 |
| `shield.defaultMaxShield` / `defaultCurrentShield` | 默认护盾上限 / 当前值 | 100 / 100 |
| `shield.minPierceRatio` | 满护盾时的强制穿盾比例 | 0.10 |
| `shield.rangedDamageFullyAbsorbed` | 远程是否由护盾全额抵挡 | true |
| `shield.meleeDrainsShield` | 近战/百分比穿盾时，被挡下的部分是否消耗护盾 | true |
| `shield.shieldBreakRegenPenaltySeconds` | 破盾后回盾额外延迟 | 3 |
| `damageRules.projectileBehavior` | 远程（投射物/闪电/反伤）处理方式 `FULL`/`PIERCE`/`NONE` | FULL |
| `damageRules.dotBehavior` | 持续伤害（火焰/岩浆/毒/凋零/冰冻） | FULL（按远程） |
| `damageRules.meleeBehavior` | 近战（攻击/接触/撞击/坠落物）处理方式 | PIERCE |
| `damageRules.explosionBehavior` | 爆炸处理方式（与近战同一套公式） | PIERCE |
| `damageRules.magicBehavior` | 法术/声波（守卫者激光、监守者声波、龙息）处理方式 | FULL（按远程） |
| `damageRules.environmentBehavior` | 环境伤害（溺水/饥饿/卡墙/掉出世界等）处理方式 | NONE |
| `damageRules.otherBehavior` | 未识别伤害处理方式 | NONE |
| `damageRules.extraProjectileTypes` / `extraMeleeTypes` / `extraDotTypes` | 额外归类的伤害类型（逗号分隔） | `minecraft:spit` / 空 / 空 |
| `damageRules.fallbackToEntityType` | 未识别类型是否回退到“直接实体”判定 | true |
| `party.nearbyRadius` | 组队界面“附近玩家”的判定半径（格） | 10 |
| `party.friendlyFire` | 是否**允许**友军伤害（false = 同队玩家之间免疫伤害，连战斗标记都不打） | false |
| `hud.partyList.scale` | 队友面板整体缩放（名字字体一起缩） | 0.8 |
| `hud.partyList.rowWidth` / `rowHeight` | 队友行尺寸（缩放前） | 96 / 18 |
| `linkedshield.rateOneMember` / `rateTwoMembers` / `rateThreeOrMore` | 连携回复速率（点/秒） | 2.5 / 3.75 / 5.0 |
| `linkedshield.soloRate` | 单人（无队友）回复速率，0 = 只有队友在身边才回盾 | 0 |
| `linkedshield.radius` | 连携判定半径 | 10 格 |
| `linkedshield.startDelaySeconds` | 连携人数 &gt; 1 后需要持续多少秒才开始回盾 | 2 |
| `linkedshield.requireOutOfCombat` | 是否仍要求脱战才能回盾 | false |
| `linkedshield.outOfCombatDelaySeconds` | 脱战多久后开始回盾 | 2 秒 |
| `linkedshield.requireSameDimension` | 是否要求同维度 | true |
| `combat.combatTagSeconds` | 脱战判定：**最后一次受伤后多少秒算脱战** | 2 秒 |
| `hud.partyList.*` | 队友面板：靠边（默认 **LEFT**）、偏移、行宽高、**名字位置**、护盾条、序号、背景透明度、排序 | 左侧 / 名字在血条上方 |
| `hud.selfShield.*` | 自身护盾条：**显示位置**（血条上方/下方/快捷栏上方/自定义）、偏移、宽高、文本位置、颜色、数字格式（默认 **CURRENT = 只显示实时数值**） | 血条上方 / 68 |
| `hud.linkedshieldIcon.*` | 连携图标：显示开关、缩放、偏移、是否显示人数 | 显示 / 1.0 / 0 / 显示 |

改完保存后：

- 单机：`/linkedshield reload` 热重载（无需重启）
- 多人：房主/服务端改自己的 `config/linkedshield/settings.json` 后 `/linkedshield reload`

> HTML 保存行为（1.0.3 起）：`保存 settings.json` 会**直接写回这个 html 所在的文件夹**（`config/linkedshield/`）。
> 浏览器安全策略要求首次点一次「允许访问文件夹」，授权后浏览器会记住它，以后点保存就静默写回、不再弹窗；
> 想换地方就用 `另存为…`（走系统文件对话框）。没有 File System Access API 的浏览器（如 Firefox）会退化为下载。

**HUD 实时预览**（1.0.3 起放在所有数值下面，**1:1 原版比例，没有任何缩放**）：

预览区就是一个 **GUI 单位坐标系**（1 格 = 1 GUI 单位，画布 640×360 GUI 单位，等于 1920×1080 窗口在 GUI 缩放 3 下的尺寸），
所以数值 1px 在预览里就是 1px，和游戏里完全对得上。可以：

- **按住队友面板拖动** → 改 `hud.partyList.offsetX / offsetY`（面板靠右侧时偏移方向自动反向）
- **按住护盾条拖动** → 改 `hud.selfShield.offsetX / offsetY`
- **在队友面板上滚轮** → 改 `hud.partyList.scale`（游戏内同样生效的面板缩放，不是预览缩放）
- **在护盾条上滚轮** → 改 `hud.selfShield.width`；**Shift + 滚轮** → 改 `hud.selfShield.height`
- 拖动/滚轮的结果会实时写回表单里的数字，点「保存 settings.json」写回本文件夹，再 `/linkedshield reload` 生效

**原版参照物（校准位置用）**：预览里按原版布局画出了**生命条、饥饿条、经验条、快捷栏（9 格 + 选中框）**，
灰色标记 `vanilla HUD`。因为护盾条的锚点就是按原版坐标算的（`x = 屏宽/2 - 91`、生命条在 `屏高 - 39`、
快捷栏 182×22 贴底），所以对着它拖动/滚轮就能把护盾条精确贴到「血条正上方 / 血条下方 / 快捷栏上方」。
右下角勾选框可以随时隐藏这些参照物。

**多语言（点击国旗切换）**：编辑器界面文字来自模组自己的语言文件，右上角有四个国旗按钮：

| 国旗 | 语言 | 语言文件 |
| --- | --- | --- |
| 🇨🇳 | 简体中文 | `lang/zh_cn.json`（完整） |
| 🇺🇸 | English (US) | `lang/en_us.json`（完整，**权威文件**） |
| 🇬🇧 | English (UK) | `lang/en_gb.json`（只写英式差异：armour / colour / recognised…，其余回退 en_us） |
| 🇷🇺 | Русский | `lang/ru_ru.json`（完整） |

游戏内的指令、GUI、按键名同样覆盖这四种语言；浏览器打开配置页时会按系统语言自动选一种，
也可以随时点国旗手动切换（缺键按 `en_us → zh_cn` 回退）。

---

## 3. 指令

组队（所有玩家可用）：

```
/party invite <玩家>     邀请
/party accept            接受邀请
/party deny              拒绝邀请
/party leave             离开小队
/party kick <玩家>       队长踢人
/party disband           队长解散
/party rename <名字>     队长改队伍名（最多 24 字，留空=用默认名）
/party list              列出成员（名字/血量/护盾）
/party info              当前护盾（只显示实时数值）、附近队友数、连携速率、连携是否已启动
```

护盾与配置（需要 OP，权限等级 2）：

```
/linkedshield shield get [玩家]
/linkedshield shield set <数值> [玩家]
/linkedshield shield max <数值> [玩家]
/linkedshield bonus <数值>            给主手物品挂/改 linkedshield:shield_bonus（拓展接口调试，0 = 移除）
/linkedshield reload                      重新读取 settings.json
/linkedshield config                      打印可点击的配置页面链接
/linkedshield damage                      列出八类伤害各自的处理方式
/linkedshield damage <伤害类型>           查某个伤害类型归到哪一类（如 minecraft:sonic_boom）
```

---

## 4. 护盾机制

### 4.0 结算时机（重要）

护盾在 **`LivingDamageEvent.Pre`** 结算，也就是**护甲 / 附魔 / 状态效果减伤之后、吸收之前**：

```
原始伤害 → 难度缩放 → 护甲减伤 → 附魔/抗性减伤 → 【护盾结算】 → 吸收(黄心) → 扣血
```

所以护盾扣的是**真正会打到血量上的那份伤害**，不会替护甲买单：
同一发 20 点近战伤害，无甲时护盾扣 18、掉血 2；穿钻石全套时护盾只扣 7.2、掉血 0.8（实测见第 7 节）。

### 4.1 伤害分类（不再看“谁打的我”）

判定依据是**伤害类型 + 原版标签**（`DamageSource#is(TagKey)`）。每类可在 `damageRules` 里单独设置：

| 方式 | 含义 |
| --- | --- |
| `FULL` | 护盾全额抵挡：护盾足够则完全免伤，并扣掉等量护盾；不够时溢出打到血量 |
| `PIERCE` | 按护盾百分比穿盾：`穿盾比例 = clamp(1 - 当前护盾/最大护盾, minPierceRatio, 1)` |
| `NONE` | 不吃护盾，完全按原版结算 |

### 4.2 分类表（1.21.11 全部 50 个原版伤害类型）

| 类别 | 默认处理 | 包含的伤害类型 |
| --- | --- | --- |
| **远程** | `FULL` | `arrow`、`trident`、`mob_projectile`、`unattributed_fireball`、`fireball`、`wither_skull`、`thrown`、`wind_charge`（`#minecraft:is_projectile`）+ `spit`（原版漏在标签外，用 `extraProjectileTypes` 补上）+ `lightning_bolt`（闪电）+ `thorns`（反伤） |
| **持续伤害** | `FULL`（按远程） | `in_fire`、`on_fire`、`campfire`、`hot_floor`、`lava`、`magic`（毒药/瞬间伤害）、`wither`（凋零效果）、`freeze`（冰冻） |
| **法术/声波** | `FULL`（按远程） | `sonic_boom`（监守者声波）、`indirect_magic`（守卫者激光）、`dragon_breath`（龙息） |
| **近战** | `PIERCE` | 攻击类：`player_attack`、`mob_attack`、`mob_attack_no_aggro`、`mace_smash`、`sting`、`spear`<br>接触/撞击类：`cactus`、`sweet_berry_bush`、`cramming`、`fall`、`ender_pearl`、`fly_into_wall`、`stalagmite`<br>坠落物：`falling_anvil`、`falling_block`、`falling_stalactite` |
| **爆炸** | `PIERCE`（**与近战同一套公式**） | `explosion`（TNT/水晶/爬行者）、`player_explosion`（床/重生锚）、`bad_respawn_point`、`fireworks`（烟花火箭） |
| **环境** | `NONE` | `drown`、`starve`、`in_wall`、`out_of_world`、`outside_border`、`generic`、`generic_kill`、`dry_out` |
| **其它/未识别** | `NONE` | 模组自定义类型；`fallbackToEntityType=true` 时按“直接实体”回退成远程/近战，避免模组内容完全不吃护盾 |

> 1.0.1 起把仙人掌、甜浆果丛、挤压、摔落、末影珍珠摔落、飞行撞击、石笋、坠落物统统并入**近战**（百分比穿盾），
> 闪电与反伤并入**远程**（全额抵挡）；原来的「反伤」「坠落物」两个独立类别已取消，类别共 7 种。
> `spear` 在 1.21.11 原版代码里没有任何调用点（只有数据文件），归到近战是为了 `/damage`、数据包或模组用到它时行为一致。
> 查游戏内实际归类：`/linkedshield damage <伤害类型>`（例如 `/linkedshield damage minecraft:sonic_boom`）。

### 4.3 近战/百分比穿盾实例（`minPierceRatio = 0.10`）

| 当前护盾 | 穿盾比例 | 100 点伤害实际掉血 |
| --- | --- | --- |
| 100%（满） | 10%（强制下限） | 10 |
| 90% | 10% | 10 |
| 50% | 50% | 50 |
| 10% | 90% | 90 |
| 0%（破盾） | 100% | 100 |

### 4.4 连携回盾（LinkedShield）

- 判定：同小队、在 `linkedshield.radius` 内（默认 10 格）、在线且存活；`requireSameDimension=true` 时还要求同维度
- 速率：附近 1 名队友 2.5/s，2 名 3.75/s，3 名及以上 5/s（可配）
- **启动条件（1.0.1 起）**：连携人数 **> 1**（身边至少 1 名队友）并**持续 `startDelaySeconds` 秒（默认 2 秒）**才开始回盾；
  中途有人离开半径会重新计时。另外距上次**破盾**要超过 `shieldBreakRegenPenaltySeconds`（默认 3 秒）
- `linkedshield.requireOutOfCombat`（默认 **false**）打开后会额外要求脱战
  （距上次受伤超过 `max(combatTagSeconds, outOfCombatDelaySeconds)`，两项默认都是 **2 秒**）
- 连携图标（血条左侧的蓝色盾牌）与回盾同时生效，右下角数字 = 连携人数（含自己）

### 4.5 给其它模组的对接接口（护盾上限拓展）

护盾上限分两层：**基础上限**（存在玩家数据里、随存档保存）+ **动态加成**（饰品/装备/药水等提供）。
动态加成有三条对接路径，任选其一即可：

**① 物品组件 `linkedshield:shield_bonus`** —— 最简单，物品在背包/装备栏里就生效（可叠加）：

```java
// 自己模组的物品
new Item.Properties().component(LinkedShieldDataComponents.SHIELD_BONUS.get(), 25.0D)
```

```mcfunction
/give @s minecraft:diamond_chestplate[linkedshield:shield_bonus=25]
/linkedshield bonus 25        # 给主手物品挂上/修改（调试用，设 0 即移除）
```

**② NeoForge 事件**（不用依赖本模组的编译期接口，监听即可，适合按饰品等级/药水效果动态算）：

```java
NeoForge.EVENT_BUS.addListener((LinkedShieldEvent.MaxShield event) -> {
    // 例：戴着“护盾背心”+30，每级“护盾强化”药水再 +10
    if (event.getPlayer().getItemBySlot(EquipmentSlot.CHEST).is(MyItems.SHIELD_VEST.get())) {
        event.addBonus(30.0D);
    }
    event.addBonus(10.0D * event.getPlayer().getEffect(MyEffects.SHIELD_UP).getAmplifier());
});
// 还有两个通知事件：Changed（护盾变化，带原因 REGEN/DAMAGE/COMMAND/API）、Broken（被打空）
```

**③ 静态 API `LinkedShieldAPI`**（服务端，读写护盾）：

```java
double shield = LinkedShieldAPI.getShield(player);
double max    = LinkedShieldAPI.getEffectiveMaxShield(player);   // 基础 + 加成
LinkedShieldAPI.addShield(player, 20.0D);                        // 加 20 点（自动夹到上限）
LinkedShieldAPI.setBaseMaxShield(player, 150.0D);                // 改基础上限（存档保存）
LinkedShieldAPI.setShield(player, 0.0D);                         // 清空
LinkedShieldAPI.refreshBonuses(player);                          // 换装备后立刻重算
boolean linked = LinkedShieldAPI.isLinkedShieldActive(player);      // 是否正在连携回盾
```

- 服务端**每 10 tick** 自动重算一次加成；换装备/饰品后想立刻生效就调 `refreshBonuses`
- 加成变小时当前护盾会被自动夹回新上限
- 护盾 HUD、`/party list`、`/party info` 显示的都是**含加成的有效上限**
- 实测：`runHudDemo -PdamageTest` 的最后一步会给副手盾牌挂上 `shield_bonus=40` 并断言有效上限从 100 → 140、取下后回到 100

---

## 5. 组队界面与按键

默认按键 **U**（1.0.1 起从 P 改为 U；可在「选项 → 按键控制 → LinkedShield 小队」里改）：

- 按 U 打开/关闭组队界面（`PartyScreen`）
- 界面左边是 **我的小队**：队伍名、**第一行永远是自己并标注“（你）”**、后面是队内玩家（★队长）、
  队长每行有「踢出」，底部「离开小队」「解散小队」，还能在输入框里**改队伍名**
- 界面右边是 **附近玩家**（默认 10 格内，`party.nearbyRadius` 可调）：
  显示名字与距离，未组队的玩家有「邀请」按钮，已在队伍里的显示「已有队伍」
- 有人邀请你时：
  - 屏幕**右上角出现一个小点**（呼吸闪烁，`PartyNotifyLayer`）
  - 打开界面后顶部出现「XX 邀请你加入小队」+「接受邀请 / 拒绝」按钮
- 队伍名默认是「队长名 的小队」，队长改名后所有成员界面/聊天栏同步
- **界面里不显示血条与护盾数值**（只显示名字与状态）；界面打开时本模组 HUD 会临时隐藏，
  界面按钮不会被 HUD 盖住

- 连携生效时（脱战 + 半径内有队友持续 2 秒），血条左侧出现 **蓝色盾牌图标**，
  右下角数字 = 实际连携人数（**含自己**，例如 3 名队友时为 4）
- 队友身上有效果时，血条下方一排 9x9 的小图标：原版 `mob_effect/*` 图标（**保持原版配色**——贴图本身已经上色，
  所以只做原版那层白色 tint；剩余 &lt; 200 tick 时按原版公式闪烁，解析不到的效果退回同色纯色块），
  图标画在面板缩放矩阵里，所以是“缩小显示”；每 4 tick 随 `party_state` 同步（每名队友最多 8 条，增益排在前、剩余时间长的在前）
- **友军伤害免疫**：`party.friendlyFire = false`（默认）时，同队玩家之间的伤害在 `LivingIncomingDamageEvent` 里被取消，
  血量、护盾、战斗标记都不受影响；解析伤害归属时会顺着投射物 owner（箭、投掷物）和 `OwnableEntity`/`Tameable` 的 owner 找玩家

界面数据全部来自服务端的 `party_state` 包（每 4 tick 同步一次），按钮通过 `party_action` 包回传服务端；
指令 `/party ...` 和界面按钮走的是同一套 `PartyService` 逻辑。

---

## 6. 工程结构

```
src/main/java/com/linkedshield/
├── LinkedShieldMod.java              模组入口：注册附件、物品组件、网络包、事件
├── api/                           ← 给其它模组的对接接口
│   ├── LinkedShieldAPI.java          静态 API：读/写护盾、有效上限、刷新加成
│   ├── LinkedShieldEvent.java  MaxShield / Changed / Broken 三个事件
│   └── LinkedShieldDataComponents.java 物品组件 linkedshield:shield_bonus├── config/
│   ├── LinkedShieldSettings.java     所有配置项（字段名 = JSON 键名）
│   └── ConfigManager.java         settings.json 读写 + HTML 生成
├── party/
│   ├── Party.java                 小队（队长 + 队伍名 + 成员，Codec）
│   ├── PartyStore.java            全局小队表 + 邀请（Level 数据附件，随存档保存）
│   ├── PartyService.java          组队操作（指令与组队界面共用）
│   └── PartyManager.java          查询/广播辅助
├── shield/
│   ├── ShieldData.java            玩家护盾数据（Codec，死亡保留）
│   ├── LinkedShieldAttachments.java  数据附件注册
│   ├── DamageClassifier.java      伤害类型/标签 → 八类 → FULL/PIERCE/NONE
│   ├── ShieldMath.java            全额吸收 / 百分比穿盾 / 连携公式（纯函数）
│   └── ShieldService.java         伤害结算（LivingDamageEvent.Pre）+ 友军伤害免疫 + 每 tick 连携回复
├── network/│   ├── PartyStatePayload.java     服务端 → 客户端：队友（含状态效果列表）/附近玩家/队伍名/邀请
│   ├── PartyActionPayload.java    客户端 → 服务端：组队界面按钮动作
│   ├── LinkedShieldNetwork.java      网络包注册
│   └── PartySync.java             每 4 tick 同步一次
├── command/LinkedShieldCommands.java /party 与 /linkedshield
├── client/
│   ├── LinkedShieldClient.java       客户端注册（HUD 图层、按键 P、包处理）
│   ├── ClientPartyState.java      客户端缓存 + 排序 + 已邀请记录
│   ├── PartyScreen.java           组队界面（我的小队 + 附近玩家）
│   ├── PartyNotifyLayer.java      右上角邀请小点
│   ├── PartyHudLayer.java         右侧 FF14 风格队友面板（缩放、无距离、血条下方效果图标条）
│   └── SelfShieldLayer.java       血条上方的护盾条
└── dev/LinkedShieldSelfTest.java     自检（服务端 170 项断言）
    dev/LinkedShieldDamageProbe.java  伤害管线实测（真机打伤害，23 项断言）

src/main/resources/
├── assets/linkedshield/lang/{zh_cn,en_us,en_gb,ru_ru}.json   四种语言的指令/GUI/编辑器文案
├── assets/linkedshield/textures/gui/linkedshield_icon.png       连携图标（蓝色盾牌 + 人数）
└── linkedshield/config_editor.html   HTML 配置编辑器模板（内嵌当前配置 + 四语言文案后写到 config/）
```

---

## 7. 已知限制

- HUD 只显示**在线**队友；离线成员在 `/party list` 里显示为 `???`
- 护盾按第 4.2 节的分类表处理伤害；未识别类型默认不吃护盾（可用 `fallbackToEntityType` 回退）
- 队友面板不再显示距离（按需求移除）；排序仍可用 `sortMode = DISTANCE`
- 组队界面只显示名字（按需求去掉血条/护盾值）；打开界面时本模组 HUD 会隐藏
- 组队界面的“附近玩家”只看**同维度**、半径内的在线玩家，队友不会出现在该列表里
- 连携图标的人数**含自己**（3 名队友 = 4）；无队友时不显示图标
- 队伍名最长 24 个字符；小队数据存在主世界存档里（`data` 附件），换存档 = 换小队
- 客户端 HUD 布局取自**客户端自己的** `settings.json`；多人服务器上“小队人数上限”等玩法数值以服务端为准

---


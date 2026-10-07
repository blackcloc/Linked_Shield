package com.linkedshield.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.linkedshield.LinkedShieldMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 连携护盾提供的数据组件。把 {@code linkedshield:shield_bonus} 挂到物品上,
 * 玩家背包/装备里带着它就会加护盾上限 —— 饰品模组、数据包甚至 {@code /give} 都能直接用。
 *
 * <h2>示例 1：自己的模组里给物品挂组件</h2>
 * <pre>{@code
 * new Item.Properties().component(LinkedShieldDataComponents.SHIELD_BONUS.get(), 25.0D)
 * }</pre>
 *
 * <h2>示例 2：指令直接给手上的物品加（管理员/调试）</h2>
 * <pre>{@code
 * /give @s minecraft:diamond_chestplate[linkedshield:shield_bonus=25]
 * /linkedshield bonus 25      // 给主手物品挂上/修改组件
 * }</pre>
 */
public final class LinkedShieldDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LinkedShieldMod.MODID);

    /** {@code linkedshield:shield_bonus}（双精度）：背包/装备中的物品护盾上限加成。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> SHIELD_BONUS =
            COMPONENTS.register("shield_bonus", () -> DataComponentType.<Double>builder()
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    .build());

    /** 组件注册名（用于指令补全 / 提示）。 */
    public static Identifier shieldBonusId() {
        return Identifier.fromNamespaceAndPath(LinkedShieldMod.MODID, "shield_bonus");
    }

    private LinkedShieldDataComponents() {
    }
}

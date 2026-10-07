package com.linkedshield.command;

import com.linkedshield.api.LinkedShieldDataComponents;
import com.linkedshield.api.LinkedShieldEvent;
import com.linkedshield.config.ConfigManager;
import com.linkedshield.config.LinkedShieldSettings;
import com.linkedshield.network.PartySync;
import com.linkedshield.party.Party;
import com.linkedshield.party.PartyManager;
import com.linkedshield.party.PartyService;
import com.linkedshield.shield.LinkedShieldAttachments;
import com.linkedshield.shield.DamageClassifier;
import com.linkedshield.shield.ShieldData;
import com.linkedshield.shield.ShieldMath;
import com.linkedshield.shield.ShieldService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/** /party 与 /linkedshield 两条指令。组队操作本身在 {@link PartyService}（组队界面共用同一套）。 */
public final class LinkedShieldCommands {

    private LinkedShieldCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerParty(dispatcher);
        registerLinkedShield(dispatcher);
    }

    /* ------------------------------------------------------------------ /party */

    private static void registerParty(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LinkedShieldCommands::invite)))
                .then(Commands.literal("accept").executes(LinkedShieldCommands::accept))
                .then(Commands.literal("deny").executes(LinkedShieldCommands::deny))
                .then(Commands.literal("leave").executes(LinkedShieldCommands::leave))
                .then(Commands.literal("disband").executes(LinkedShieldCommands::disband))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LinkedShieldCommands::kick)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(LinkedShieldCommands::rename)))
                .then(Commands.literal("list").executes(LinkedShieldCommands::list))
                .then(Commands.literal("info").executes(LinkedShieldCommands::info)));
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")) ? 1 : 0;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.accept(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int deny(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.deny(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.disband(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.kick(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")) ? 1 : 0;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return PartyService.rename(ctx.getSource().getPlayerOrException(),
                StringArgumentType.getString(ctx, "name")) ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        Party party = PartyManager.store(server).partyOf(player.getUUID());
        if (party == null) {
            ctx.getSource().sendFailure(Component.translatable("linkedshield.command.party.not_in_party"));
            return 0;
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(party.getLeader());
        String leaderName = leader == null ? "???" : leader.getName().getString();
        String partyName = party.displayName(leaderName);
        int max = ConfigManager.get().party.maxPartySize;

        MutableComponent header = Component.translatable("linkedshield.command.party.list.header",
                partyName, String.valueOf(party.size()), String.valueOf(max)).withStyle(ChatFormatting.AQUA);
        ctx.getSource().sendSuccess(() -> header, false);
        for (UUID id : party.orderedMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member == null) {
                ctx.getSource().sendSuccess(() -> Component.literal(" - ???").withStyle(ChatFormatting.DARK_GRAY), false);
                continue;
            }
            ShieldData shield = member.getData(LinkedShieldAttachments.SHIELD);
            boolean isLeader = party.isLeader(id);
            MutableComponent row = Component.translatable("linkedshield.command.party.list.entry",
                    (isLeader ? "★ " : "  ") + member.getName().getString(),
                    String.format("%.0f/%.0f", member.getHealth(), member.getMaxHealth()),
                    String.format("%.0f", shield.getShield()));
            row.withStyle(isLeader ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            ctx.getSource().sendSuccess(() -> row, false);
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        Party party = PartyManager.store(server).partyOf(player.getUUID());
        if (party == null) {
            ctx.getSource().sendFailure(Component.translatable("linkedshield.command.party.not_in_party"));
            return 0;
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(party.getLeader());
        ShieldData shield = player.getData(LinkedShieldAttachments.SHIELD);
        int nearby = ShieldService.nearbyTeammates(player);
        LinkedShieldSettings cfg = ConfigManager.get();
        double rate = ShieldMath.linkedshieldRate(nearby, cfg);
        boolean ready = ShieldService.linkedshieldReady(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.party.info.leader",
                leader == null ? "???" : leader.getName().getString()).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.party.info.shield",
                String.format("%.1f", shield.getShield())), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.party.info.linkedshield",
                String.valueOf(nearby), String.format("%.2f", rate),
                Component.translatable(ready ? "linkedshield.state.linkedshield_on" : "linkedshield.state.linkedshield_wait")), false);
        return 1;
    }

    /* -------------------------------------------------------------- /linkedshield */

    private static void registerLinkedShield(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("linkedshield")
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(LinkedShieldCommands::reload))
                .then(Commands.literal("config").executes(LinkedShieldCommands::config))
                .then(Commands.literal("damage")
                        .executes(LinkedShieldCommands::damageSummary)
                        .then(Commands.argument("type", StringArgumentType.string())
                                .executes(LinkedShieldCommands::damageInfo)))
                .then(Commands.literal("shield")
                        .then(Commands.literal("get")
                                .executes(ctx -> shieldGet(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(ctx -> shieldGet(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D))
                                        .executes(ctx -> shieldSet(ctx, ctx.getSource().getPlayerOrException(),
                                                DoubleArgumentType.getDouble(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> shieldSet(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        DoubleArgumentType.getDouble(ctx, "value"))))))
                        .then(Commands.literal("max")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0D))
                                        .executes(ctx -> shieldSetMax(ctx, ctx.getSource().getPlayerOrException(),
                                                DoubleArgumentType.getDouble(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> shieldSetMax(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                        DoubleArgumentType.getDouble(ctx, "value")))))))
                .then(Commands.literal("bonus")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1000.0D))
                                .executes(ctx -> itemBonus(ctx, DoubleArgumentType.getDouble(ctx, "value"))))));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.reload();
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.reload"), true);
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> ctx) {
        MutableComponent link = ConfigManager.clickableHtmlLink();
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (!(net.neoforged.fml.loading.FMLEnvironment.getDist().isClient() && caller != null
                && caller.connection.getConnection().isMemoryConnection())) {
            link.withStyle(style -> style.withClickEvent(null));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.config", link), false);
        return 1;
    }

    /** /linkedshield damage：列出各类别各自怎么处理。 */
    private static int damageSummary(CommandContext<CommandSourceStack> ctx) {
        LinkedShieldSettings cfg = ConfigManager.get();
        for (DamageClassifier.Category category : DamageClassifier.Category.values()) {
            DamageClassifier.Behavior behavior = DamageClassifier.behaviorFor(category, cfg);
            MutableComponent row = Component.translatable("linkedshield.command.damage.summary",
                    category.label(), behavior.name());
            row.withStyle(behavior == DamageClassifier.Behavior.NONE ? ChatFormatting.GRAY : ChatFormatting.AQUA);
            ctx.getSource().sendSuccess(() -> row, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.damage.hint"), false);
        return 1;
    }

    /** /linkedshield damage <伤害类型>：查询某个伤害类型会被归到哪一类、怎么结算。 */
    private static int damageInfo(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "type");
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.translatable("linkedshield.command.damage.bad_id", raw));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        Registry<DamageType> registry = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var holder = registry.get(id);
        if (holder.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("linkedshield.command.damage.unknown", id.toString()));
            return 0;
        }
        DamageSource source = new DamageSource(holder.get());
        LinkedShieldSettings cfg = ConfigManager.get();
        DamageClassifier.Category category = DamageClassifier.categorize(source, cfg);
        DamageClassifier.Behavior behavior = DamageClassifier.behaviorFor(category, cfg);
        double[] shield = {0.0D, 0.0D};
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller != null) {
            ShieldData data = caller.getData(LinkedShieldAttachments.SHIELD);
            shield[0] = data.getShield();
            shield[1] = ShieldService.effectiveMaxShield(data);
        }
        final double current = shield[0];
        final double max = shield[1];
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.damage.info",
                id.toString(), category.label(), behavior.name(),
                String.format("%.1f", current), String.format("%.1f", max)), false);
        return 1;
    }

    private static int shieldGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ShieldData data = target.getData(LinkedShieldAttachments.SHIELD);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.shield.get",
                target.getName().getString(),
                String.format("%.1f", data.getShield()),
                String.format("%.1f", ShieldService.effectiveMaxShield(data)),
                String.format("%.1f", data.getBonusMaxShield())), false);
        return 1;
    }

    private static int shieldSet(CommandContext<CommandSourceStack> ctx, ServerPlayer target, double value) {
        ShieldData data = target.getData(LinkedShieldAttachments.SHIELD);
        if (value > data.getMaxShield()) {
            // 自动抬高基础上限，避免设置被夹掉
            data.setMaxShield(value);
        }
        ShieldService.setShield(target, value, LinkedShieldEvent.ChangeReason.COMMAND);
        PartySync.sendStateNow(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.shield.set",
                target.getName().getString(), String.format("%.1f", data.getShield())), true);
        return 1;
    }

    private static int shieldSetMax(CommandContext<CommandSourceStack> ctx, ServerPlayer target, double value) {
        ShieldData data = target.getData(LinkedShieldAttachments.SHIELD);
        data.setMaxShield(value);
        ShieldService.setShield(target, Math.min(data.getShield(), ShieldService.effectiveMaxShield(data)),
                LinkedShieldEvent.ChangeReason.COMMAND);
        PartySync.sendStateNow(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.shield.max",
                target.getName().getString(), String.format("%.1f", data.getMaxShield())), true);
        return 1;
    }

    /**
     * {@code /linkedshield bonus <值>}：给主手物品挂上（或修改）{@code linkedshield:shield_bonus} 组件，
     * 用来演示 / 调试「物品加护盾上限」这条对接路径。
     */
    private static int itemBonus(CommandContext<CommandSourceStack> ctx, double value) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("linkedshield.command.bonus.empty"));
            return 0;
        }
        stack.set(LinkedShieldDataComponents.SHIELD_BONUS.get(), value);
        ShieldService.refreshBonuses(player);
        PartySync.sendStateNow(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("linkedshield.command.bonus.set",
                stack.getHoverName(), String.format("%.1f", value),
                String.format("%.1f", ShieldService.effectiveMaxShield(player))), true);
        return 1;
    }
}

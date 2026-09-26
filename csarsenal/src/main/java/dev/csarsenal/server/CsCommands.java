package dev.csarsenal.server;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.csarsenal.weapon.WeaponItem;
import dev.csarsenal.weapon.WeaponState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /cs team t|ct|none, /cs armor &lt;0-100&gt; [helmet], /cs money &lt;amount&gt;, /cs refill */
public final class CsCommands {
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("cs")
                .then(Commands.literal("team").then(Commands.argument("team", StringArgumentType.word())
                        .suggests((c, b) -> b.suggest("t").suggest("ct").suggest("none").buildFuture())
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String t = StringArgumentType.getString(c, "team").toLowerCase();
                            int team = t.equals("t") ? CsPlayerData.TEAM_T : t.equals("ct") ? CsPlayerData.TEAM_CT : CsPlayerData.TEAM_NONE;
                            CsPlayerData.set(p, CsPlayerData.get(p).withTeam(team));
                            if (team != 0) {
                                c.getSource().sendSuccess(() -> Component.translatable("csarsenal.team.set",
                                        Component.translatable(team == 1 ? "csarsenal.team.t" : "csarsenal.team.ct")), false);
                            }
                            return 1;
                        })))
                .then(Commands.literal("armor").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
                                .executes(c -> armor(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), true))
                                .then(Commands.argument("helmet", BoolArgumentType.bool())
                                        .executes(c -> armor(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), BoolArgumentType.getBool(c, "helmet"))))))
                .then(Commands.literal("money").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000000))
                                .executes(c -> money(java.util.List.of(c.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(c, "amount")))
                                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(c -> money(net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("refill").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack s = p.getInventory().getItem(i);
                        if (s.getItem() instanceof WeaponItem wi) {
                            WeaponState old = WeaponItem.state(s);
                            WeaponItem.setState(s, WeaponState.full(wi.def()).withSilencer(old.silencer()).withAlt(old.altMode()));
                        }
                    }
                    return 1;
                })));
    }

    private static int money(java.util.Collection<ServerPlayer> players, int amount) {
        for (ServerPlayer p : players) CsPlayerData.set(p, CsPlayerData.get(p).withMoney(amount));
        return players.size();
    }

    private static int armor(CommandSourceStack src, int amount, boolean helmet) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        CsPlayerData.set(p, CsPlayerData.get(p).withArmor(amount, helmet));
        return 1;
    }

    private CsCommands() {
    }
}

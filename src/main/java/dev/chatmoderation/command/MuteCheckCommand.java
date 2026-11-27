package dev.chatmoderation.command;

import dev.chatmoderation.ChatModerationPlugin;
import dev.chatmoderation.Messages;
import dev.chatmoderation.MuteEntry;
import dev.chatmoderation.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /mutecheck <player> */
public final class MuteCheckCommand implements TabExecutor {

    private final ChatModerationPlugin plugin;

    public MuteCheckCommand(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("chatmoderation.check")) {
            msg.send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "usage-mutecheck");
            return true;
        }

        Optional<MuteEntry> entry = plugin.muteManager().findByName(args[0]);
        if (entry.isEmpty()) {
            OfflinePlayer target = plugin.resolvePlayer(args[0]);
            if (target == null) {
                msg.send(sender, "player-not-found", "player", args[0]);
                return true;
            }
            entry = plugin.muteManager().getActive(target.getUniqueId());
        }
        if (entry.isEmpty()) {
            msg.send(sender, "check-not-muted", "player", args[0]);
            return true;
        }

        MuteEntry e = entry.get();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String expires = e.isPermanent() ? msg.raw("permanent-label") : fmt.format(new Date(e.expiresAt()));
        String remaining = e.isPermanent() ? msg.raw("permanent-label")
                : DurationUtil.format(e.remaining(System.currentTimeMillis()));

        msg.sendRaw(sender, "check-header", "player", e.name());
        msg.sendRaw(sender, "check-reason", "reason", e.reason());
        msg.sendRaw(sender, "check-staff", "staff", e.staff());
        msg.sendRaw(sender, "check-issued", "issued", fmt.format(new Date(e.createdAt())));
        msg.sendRaw(sender, "check-expires", "expires", expires);
        msg.sendRaw(sender, "check-remaining", "remaining", remaining);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("chatmoderation.check") || args.length != 1) return out;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}

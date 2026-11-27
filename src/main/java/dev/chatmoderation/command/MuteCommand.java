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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/** /mute <player> <duration> <reason> */
public final class MuteCommand implements TabExecutor {

    private static final List<String> DURATIONS = List.of("perm", "10m", "1h", "1d", "7d");

    private final ChatModerationPlugin plugin;

    public MuteCommand(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("chatmoderation.mute")) {
            msg.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "usage-mute");
            return true;
        }

        OfflinePlayer target = plugin.resolvePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        String targetName = target.getName() != null ? target.getName() : args[0];

        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) {
            msg.send(sender, "cannot-mute-self");
            return true;
        }
        Player online = target.getPlayer();
        if (online != null && plugin.settings().protectBypass && online.hasPermission("chatmoderation.bypass")) {
            msg.send(sender, "cannot-mute-bypass", "player", targetName);
            return true;
        }

        OptionalLong parsed = DurationUtil.parse(args[1]);
        if (parsed.isEmpty()) {
            msg.send(sender, "invalid-duration", "input", args[1]);
            return true;
        }
        if (plugin.muteManager().getActive(target.getUniqueId()).isPresent()) {
            msg.send(sender, "already-muted", "player", targetName);
            return true;
        }

        String reason = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : plugin.settings().defaultReason;
        long now = System.currentTimeMillis();
        long duration = parsed.getAsLong();
        long expires = duration == DurationUtil.PERMANENT ? DurationUtil.PERMANENT : now + duration;

        MuteEntry entry = new MuteEntry(target.getUniqueId(), targetName, reason, sender.getName(), now, expires);
        plugin.muteManager().issue(entry, sender);

        String shown = duration == DurationUtil.PERMANENT ? msg.raw("permanent-label") : DurationUtil.format(duration);
        msg.send(sender, "mute-success", "player", targetName, "duration", shown, "reason", reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("chatmoderation.mute")) return out;
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (args.length == 2) {
            for (String d : DURATIONS) if (d.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(d);
        }
        return out;
    }
}

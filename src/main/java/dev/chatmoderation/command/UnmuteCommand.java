package dev.chatmoderation.command;

import dev.chatmoderation.ChatModerationPlugin;
import dev.chatmoderation.Messages;
import dev.chatmoderation.MuteEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /unmute <player> */
public final class UnmuteCommand implements TabExecutor {

    private final ChatModerationPlugin plugin;

    public UnmuteCommand(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("chatmoderation.unmute")) {
            msg.send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "usage-unmute");
            return true;
        }

        // Look up by stored name first so players that are no longer resolvable can still be unmuted.
        Optional<MuteEntry> entry = plugin.muteManager().findByName(args[0]);
        if (entry.isEmpty()) {
            OfflinePlayer target = plugin.resolvePlayer(args[0]);
            if (target != null) entry = plugin.muteManager().getActive(target.getUniqueId());
        }
        if (entry.isEmpty()) {
            msg.send(sender, "not-muted", "player", args[0]);
            return true;
        }

        MuteEntry mute = entry.get();
        plugin.muteManager().remove(mute.uuid());
        msg.send(sender, "unmute-success", "player", mute.name());

        Player online = Bukkit.getPlayer(mute.uuid());
        if (online != null) msg.send(online, "unmute-target-notify");
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("chatmoderation.unmute") && staff != sender && staff != online) {
                msg.send(staff, "unmute-staff-notify", "staff", sender.getName(), "player", mute.name());
            }
        }
        plugin.getLogger().info(sender.getName() + " unmuted " + mute.name());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("chatmoderation.unmute") || args.length != 1) return out;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))
                    && plugin.muteManager().getActive(p.getUniqueId()).isPresent()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}

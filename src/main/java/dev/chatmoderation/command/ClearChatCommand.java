package dev.chatmoderation.command;

import dev.chatmoderation.ChatModerationPlugin;
import dev.chatmoderation.Messages;
import dev.chatmoderation.Settings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /clearchat */
public final class ClearChatCommand implements TabExecutor {

    private final ChatModerationPlugin plugin;

    public ClearChatCommand(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!sender.hasPermission("chatmoderation.clear")) {
            msg.send(sender, "no-permission");
            return true;
        }
        Settings s = plugin.settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (s.bypassKeepsChat && p.hasPermission("chatmoderation.bypass")) continue;
            for (int i = 0; i < s.clearLines; i++) p.sendMessage(" ");
        }
        String announcement = msg.get("chat-cleared", "player", sender.getName());
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(announcement);
        Bukkit.getConsoleSender().sendMessage(announcement);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return List.of();
    }
}

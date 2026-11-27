package dev.chatmoderation.command;

import dev.chatmoderation.ChatModerationPlugin;
import dev.chatmoderation.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /chat toggle | /chat reload */
public final class ChatCommand implements TabExecutor {

    private final ChatModerationPlugin plugin;

    public ChatCommand(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 1) {
            msg.send(sender, "usage-chat");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                if (!sender.hasPermission("chatmoderation.toggle")) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                boolean enabled = !plugin.isChatEnabled();
                plugin.setChatEnabled(enabled);
                String text = msg.get(enabled ? "chat-enabled-broadcast" : "chat-disabled-broadcast",
                        "player", sender.getName());
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(text);
                Bukkit.getConsoleSender().sendMessage(text);
            }
            case "reload" -> {
                if (!sender.hasPermission("chatmoderation.admin")) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                try {
                    plugin.reloadAll();
                    plugin.messages().send(sender, "reload-success");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Reload failed: " + ex);
                    msg.send(sender, "reload-failed", "error", String.valueOf(ex.getMessage()));
                }
            }
            default -> msg.send(sender, "usage-chat");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String typed = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission("chatmoderation.toggle") && "toggle".startsWith(typed)) out.add("toggle");
        if (sender.hasPermission("chatmoderation.admin") && "reload".startsWith(typed)) out.add("reload");
        return out;
    }
}

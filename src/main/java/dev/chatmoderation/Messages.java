package dev.chatmoderation;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Reads configurable messages, applies colors and {placeholders}. */
public final class Messages {

    private final FileConfiguration config;
    private final String prefix;

    public Messages(FileConfiguration config) {
        this.config = config;
        this.prefix = color(config.getString("messages.prefix", ""));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Message without prefix. Placeholders are given as key, value, key, value... */
    public String raw(String key, Object... pairs) {
        String msg = config.getString("messages." + key, "&c[missing message: " + key + "]");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            msg = msg.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return color(msg);
    }

    public String get(String key, Object... pairs) {
        return prefix + raw(key, pairs);
    }

    private boolean isDisabled(String key) {
        return config.getString("messages." + key, "x").isEmpty();
    }

    /** Sends a prefixed message. A message set to "" in the config is not sent. */
    public void send(CommandSender to, String key, Object... pairs) {
        if (isDisabled(key)) return;
        to.sendMessage(get(key, pairs));
    }

    /** Sends a message without prefix. */
    public void sendRaw(CommandSender to, String key, Object... pairs) {
        if (isDisabled(key)) return;
        to.sendMessage(raw(key, pairs));
    }
}

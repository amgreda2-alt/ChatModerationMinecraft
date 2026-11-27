package dev.chatmoderation;

import dev.chatmoderation.util.DurationUtil;
import dev.chatmoderation.util.PlayerChatData;
import dev.chatmoderation.util.WordFilter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable snapshot of config.yml, rebuilt on /chat reload. */
public final class Settings {

    public final PlayerChatData.Limits limits;

    public final boolean autoMuteEnabled;
    public final int autoMuteViolations;
    public final long autoMuteWindowMs;
    public final long autoMuteDurationMs;
    public final String autoMuteReason;

    public final boolean wordsEnabled;
    public final boolean wordsCensor;
    public final String censorReplacement;
    public final WordFilter wordFilter;

    public final boolean protectBypass;
    public final String defaultReason;
    public final Set<String> mutedBlockedCommands = new HashSet<>();

    public final int clearLines;
    public final boolean bypassKeepsChat;
    public final long expiryCheckTicks;

    public Settings(FileConfiguration c) {
        int floodMax = Math.max(1, c.getInt("spam.flood.max-repeated-chars", 5));
        this.limits = new PlayerChatData.Limits(
                c.getBoolean("cooldown.enabled", true),
                (long) (c.getDouble("cooldown.seconds", 2.0) * 1000),
                c.getBoolean("spam.rate.enabled", true),
                Math.max(1, c.getInt("spam.rate.max-messages", 4)),
                c.getLong("spam.rate.window-seconds", 6) * 1000,
                c.getBoolean("spam.repeat.enabled", true),
                c.getLong("spam.repeat.window-seconds", 30) * 1000,
                c.getInt("spam.repeat.history-size", 3),
                c.getBoolean("spam.caps.enabled", true),
                c.getInt("spam.caps.min-length", 8),
                c.getInt("spam.caps.max-percent", 70),
                c.getBoolean("spam.flood.enabled", true),
                Pattern.compile("([\\p{L}\\p{N}])\\1{" + floodMax + ",}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));

        this.autoMuteEnabled = c.getBoolean("spam.auto-mute.enabled", true);
        this.autoMuteViolations = Math.max(1, c.getInt("spam.auto-mute.violations", 5));
        this.autoMuteWindowMs = c.getLong("spam.auto-mute.window-seconds", 60) * 1000;
        this.autoMuteDurationMs = DurationUtil.parse(c.getString("spam.auto-mute.duration", "5m")).orElse(300_000L);
        this.autoMuteReason = c.getString("spam.auto-mute.reason", "Automatic mute: chat spam");

        this.wordsEnabled = c.getBoolean("blocked-words.enabled", true);
        this.wordsCensor = "CENSOR".equalsIgnoreCase(c.getString("blocked-words.action", "BLOCK"));
        this.censorReplacement = c.getString("blocked-words.censor-replacement", "***");
        this.wordFilter = new WordFilter(c.getStringList("blocked-words.words"),
                c.getBoolean("blocked-words.match-inside-words", false));

        this.protectBypass = c.getBoolean("mute.protect-bypass", true);
        this.defaultReason = c.getString("mute.default-reason", "No reason specified");
        for (String cmd : c.getStringList("mute.blocked-commands")) {
            mutedBlockedCommands.add(cmd.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }

        this.clearLines = Math.max(1, Math.min(500, c.getInt("clear-chat.lines", 100)));
        this.bypassKeepsChat = c.getBoolean("clear-chat.bypass-keeps-chat", true);
        this.expiryCheckTicks = Math.max(1, c.getLong("expiry-check-interval-ticks", 20));
    }
}

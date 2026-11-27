package dev.chatmoderation;

import dev.chatmoderation.util.PlayerChatData;
import dev.chatmoderation.util.PlayerChatData.Verdict;
import dev.chatmoderation.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies mutes, global chat state, spam detection and the word filter. */
public final class ChatListener implements Listener {

    private static final String BYPASS = "chatmoderation.bypass";

    private final ChatModerationPlugin plugin;
    private final Map<UUID, PlayerChatData> data = new ConcurrentHashMap<>();

    public ChatListener(ChatModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Settings s = plugin.settings();
        Messages msg = plugin.messages();

        // 1. Mutes always apply (staff with bypass can only be muted if protect-bypass is off).
        Optional<MuteEntry> mute = plugin.muteManager().getActive(player.getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            sendMuted(player, mute.get(), msg);
            return;
        }

        // 2. Staff bypass skips every restriction below.
        if (player.hasPermission(BYPASS)) return;

        // 3. Global chat toggle.
        if (!plugin.isChatEnabled()) {
            event.setCancelled(true);
            msg.send(player, "chat-disabled");
            return;
        }

        // 4. Cooldown / spam / repeat / flood / caps.
        long now = System.currentTimeMillis();
        String message = event.getMessage();
        PlayerChatData d = data.computeIfAbsent(player.getUniqueId(), k -> new PlayerChatData());
        Verdict verdict = d.check(message, now, s.limits);
        if (verdict.type() != PlayerChatData.Type.OK) {
            event.setCancelled(true);
            switch (verdict.type()) {
                case COOLDOWN -> msg.send(player, "cooldown", "time", DurationUtil.format(verdict.remainingMs()));
                case RATE -> msg.send(player, "spam-rate");
                case REPEAT -> msg.send(player, "spam-repeat");
                case FLOOD -> msg.send(player, "spam-flood");
                case CAPS -> msg.send(player, "spam-caps");
                default -> { }
            }
            if (verdict.isSpam()) handleViolation(player, d, now, s);
            return;
        }

        // 5. Blocked words.
        if (s.wordsEnabled && s.wordFilter.containsBlocked(message)) {
            if (s.wordsCensor) {
                String censored = s.wordFilter.censor(message, s.censorReplacement);
                if (!censored.equals(message)) {
                    event.setMessage(censored);
                } else {
                    event.setCancelled(true);
                    msg.send(player, "blocked-word");
                    return;
                }
            } else {
                event.setCancelled(true);
                msg.send(player, "blocked-word");
                return;
            }
        }

        d.record(message, now, s.limits);
    }

    private void handleViolation(Player player, PlayerChatData d, long now, Settings s) {
        int count = d.addViolation(now, s.autoMuteWindowMs);
        if (s.autoMuteEnabled && count >= s.autoMuteViolations) {
            d.clearViolations();
            // Mutes are issued on the main thread (chat events are async).
            Bukkit.getScheduler().runTask(plugin, () -> {
                MuteManager mm = plugin.muteManager();
                if (mm.getActive(player.getUniqueId()).isPresent()) return;
                long created = System.currentTimeMillis();
                mm.issue(new MuteEntry(player.getUniqueId(), player.getName(), s.autoMuteReason,
                        "AutoMod", created, created + s.autoMuteDurationMs), null);
            });
        }
    }

    private void sendMuted(Player player, MuteEntry entry, Messages msg) {
        if (entry.isPermanent()) {
            msg.send(player, "muted-permanent", "reason", entry.reason());
        } else {
            msg.send(player, "muted-temporary", "reason", entry.reason(),
                    "remaining", DurationUtil.format(entry.remaining(System.currentTimeMillis())));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Settings s = plugin.settings();
        if (s.mutedBlockedCommands.isEmpty()) return;
        String text = event.getMessage();
        int space = text.indexOf(' ');
        String label = (space == -1 ? text : text.substring(0, space)).substring(1).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon != -1) label = label.substring(colon + 1);
        if (!s.mutedBlockedCommands.contains(label)) return;

        Player player = event.getPlayer();
        if (plugin.muteManager().getActive(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            plugin.messages().send(player, "muted-command");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        data.remove(event.getPlayer().getUniqueId());
    }
}

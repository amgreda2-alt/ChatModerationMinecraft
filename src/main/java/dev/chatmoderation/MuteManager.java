package dev.chatmoderation;

import dev.chatmoderation.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Holds active mutes in memory and persists them to plugins/ChatModeration/mutes.yml. */
public final class MuteManager {

    private final ChatModerationPlugin plugin;
    private final File file;
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();

    public MuteManager(ChatModerationPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
    }

    public void load() {
        mutes.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("mutes");
        if (section == null) return;
        long now = System.currentTimeMillis();
        boolean dropped = false;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String base = "mutes." + key + ".";
                MuteEntry entry = new MuteEntry(uuid,
                        yaml.getString(base + "name", key),
                        yaml.getString(base + "reason", ""),
                        yaml.getString(base + "staff", "Console"),
                        yaml.getLong(base + "created", now),
                        yaml.getLong(base + "expires", DurationUtil.PERMANENT));
                if (entry.isExpired(now)) {
                    dropped = true;
                } else {
                    mutes.put(uuid, entry);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid mute entry '" + key + "' in mutes.yml");
            }
        }
        plugin.getLogger().info("Loaded " + mutes.size() + " active mute(s).");
        if (dropped) save();
    }

    public void save() {
        synchronized (saveLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (MuteEntry e : mutes.values()) {
                String base = "mutes." + e.uuid() + ".";
                yaml.set(base + "name", e.name());
                yaml.set(base + "reason", e.reason());
                yaml.set(base + "staff", e.staff());
                yaml.set(base + "created", e.createdAt());
                yaml.set(base + "expires", e.expiresAt());
            }
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                File tmp = new File(dir, "mutes.yml.tmp");
                Files.write(tmp.toPath(), yaml.saveToString().getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp.toPath(), file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save mutes.yml", ex);
            }
        }
    }

    /** @return the active mute for this player, removing it first if it has expired. */
    public Optional<MuteEntry> getActive(UUID uuid) {
        MuteEntry e = mutes.get(uuid);
        if (e == null) return Optional.empty();
        if (e.isExpired(System.currentTimeMillis())) {
            if (mutes.remove(uuid, e)) save();
            return Optional.empty();
        }
        return Optional.of(e);
    }

    public Optional<MuteEntry> findByName(String name) {
        long now = System.currentTimeMillis();
        for (MuteEntry e : mutes.values()) {
            if (e.name().equalsIgnoreCase(name) && !e.isExpired(now)) return Optional.of(e);
        }
        return Optional.empty();
    }

    public void add(MuteEntry entry) {
        mutes.put(entry.uuid(), entry);
        save();
    }

    public Optional<MuteEntry> remove(UUID uuid) {
        MuteEntry e = mutes.remove(uuid);
        if (e != null) save();
        return Optional.ofNullable(e);
    }

    /** Removes and returns all expired mutes. */
    public List<MuteEntry> purgeExpired() {
        long now = System.currentTimeMillis();
        List<MuteEntry> removed = new ArrayList<>();
        for (MuteEntry e : mutes.values()) {
            if (e.isExpired(now) && mutes.remove(e.uuid(), e)) removed.add(e);
        }
        if (!removed.isEmpty()) save();
        return removed;
    }

    public int size() {
        return mutes.size();
    }

    /**
     * Stores a mute and notifies the target, online staff and the console.
     * Must be called on the main thread.
     */
    public void issue(MuteEntry entry, CommandSender issuer) {
        add(entry);
        Messages msg = plugin.messages();
        String duration = entry.isPermanent()
                ? msg.raw("permanent-label")
                : DurationUtil.format(entry.expiresAt() - entry.createdAt());

        Player target = Bukkit.getPlayer(entry.uuid());
        if (target != null) {
            msg.send(target, "mute-target-notify", "duration", duration, "reason", entry.reason());
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("chatmoderation.mute") && staff != issuer && staff != target) {
                msg.send(staff, "mute-staff-notify", "staff", entry.staff(),
                        "player", entry.name(), "duration", duration, "reason", entry.reason());
            }
        }
        plugin.getLogger().info(entry.staff() + " muted " + entry.name() + " (" + duration + "): " + entry.reason());
    }
}

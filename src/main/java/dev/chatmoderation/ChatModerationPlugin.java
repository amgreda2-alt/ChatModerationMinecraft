package dev.chatmoderation;

import dev.chatmoderation.command.ChatCommand;
import dev.chatmoderation.command.ClearChatCommand;
import dev.chatmoderation.command.MuteCheckCommand;
import dev.chatmoderation.command.MuteCommand;
import dev.chatmoderation.command.UnmuteCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class ChatModerationPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile boolean chatEnabled = true;
    private MuteManager muteManager;
    private BukkitTask expiryTask;
    private File stateFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        messages = new Messages(getConfig());

        muteManager = new MuteManager(this);
        muteManager.load();

        stateFile = new File(getDataFolder(), "state.yml");
        loadState();

        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        register("mute", new MuteCommand(this));
        register("unmute", new UnmuteCommand(this));
        register("mutecheck", new MuteCheckCommand(this));
        register("clearchat", new ClearChatCommand(this));
        register("chat", new ChatCommand(this));

        startExpiryTask();
    }

    @Override
    public void onDisable() {
        if (expiryTask != null) expiryTask.cancel();
        if (muteManager != null) muteManager.save();
        saveState();
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    /** Reloads config.yml. Active mutes and the chat on/off state are kept. */
    public void reloadAll() {
        reloadConfig();
        settings = new Settings(getConfig());
        messages = new Messages(getConfig());
        startExpiryTask();
    }

    private void startExpiryTask() {
        if (expiryTask != null) expiryTask.cancel();
        long interval = settings.expiryCheckTicks;
        expiryTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (MuteEntry expired : muteManager.purgeExpired()) {
                Player online = Bukkit.getPlayer(expired.uuid());
                if (online != null) messages.send(online, "mute-expired");
                getLogger().info("Mute of " + expired.name() + " expired.");
            }
        }, interval, interval);
    }

    /** Finds an online player, or a player that has joined the server before. */
    public OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    // ---- global chat state (persisted in state.yml) ----

    private void loadState() {
        if (stateFile.exists()) {
            chatEnabled = YamlConfiguration.loadConfiguration(stateFile).getBoolean("chat-enabled", true);
        }
    }

    private void saveState() {
        if (stateFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("chat-enabled", chatEnabled);
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            yaml.save(stateFile);
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Could not save state.yml", ex);
        }
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean enabled) {
        this.chatEnabled = enabled;
        saveState();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MuteManager muteManager() {
        return muteManager;
    }
}

package dev.nxms.codes;

import dev.nxms.codes.commands.CodeCommand;
import dev.nxms.codes.hooks.LuckPermsHook;
import dev.nxms.codes.managers.CodeManager;
import dev.nxms.codes.managers.MessageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Codes extends JavaPlugin {

    private static Codes instance;

    private CodeManager codeManager;
    private MessageManager messageManager;

    private LuckPermsHook luckPermsHook;
    private boolean luckPermsEnabled;

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

        // Wygeneruj messages_pl.yml zgodnie z językiem
        ensureMessagesFile();

        this.messageManager = new MessageManager(this);
        this.codeManager = new CodeManager(this);

        initLuckPerms();
        registerCommands();
        getLogger().info("Registering commands.");

        if (getConfig().getBoolean("auto-save.enabled", true)) {
            int interval = getConfig().getInt("auto-save.interval", 1) * 60 * 20;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (codeManager != null) codeManager.saveCodes();
            }, interval, interval);
        }

        getLogger().info("Codes plugin has been enabled.");
    }

    @Override
    public void onDisable() {
        if (codeManager != null) codeManager.saveCodes();
        getLogger().info("Codes plugin has been disabled.");
    }

    private void registerCommands() {
        PluginCommand cmd = getCommand("code");
        if (cmd == null) {
            getLogger().severe("Couldn't register /code command (check plugin.yml)!");
            return;
        }
        CodeCommand executor = new CodeCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    private void initLuckPerms() {
        this.luckPermsEnabled = false;
        this.luckPermsHook = null;

        if (!getConfig().getBoolean("luckperms-integration", true)) {
            getLogger().warning("LuckPerms integration is disabled in config!");
            return;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("Couldn't find LuckPerms plugin!");
            return;
        }

        try {
            this.luckPermsHook = new LuckPermsHook(getLogger());
            this.luckPermsEnabled = luckPermsHook.isAvailable();
        } catch (Throwable t) {
            getLogger().warning("Couldn't launch LuckPermsHook: " + t.getMessage());
            this.luckPermsHook = null;
            this.luckPermsEnabled = false;
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        ensureMessagesFile(); // jeśli overwrite-messages=true, to podmieni messages_pl.yml
        if (messageManager != null) messageManager.reload();
        if (codeManager != null) codeManager.loadCodes();

        getLogger().info("Codes plugin has been reloaded.");
    }

    private void ensureMessagesFile() {
        String lang = getConfig().getString("language", "pl").toLowerCase();
        boolean overwrite = getConfig().getBoolean("overwrite-messages", false);

        if (overwrite) getLogger().warning("Messages file has been overwritten! If this is an accidental action check config.yml file!");

        String resource = switch (lang) {
            case "en" -> "messages_en.yml";
            case "pl" -> "messages_pl.yml";
            default -> "messages_pl.yml";
        };

        File out = new File(getDataFolder(), "messages_pl.yml");
        if (out.exists() && !overwrite) return;

        try (InputStream in = getResource(resource)) {
            if (in == null) {
                getLogger().severe("Brak zasobu: " + resource + " w JAR!");
                return;
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().severe("Couldn't save messages_pl.yml: " + e.getMessage());
        }
    }

    public static Codes getInstance() {
        return instance;
    }

    public CodeManager getCodeManager() {
        return codeManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
}
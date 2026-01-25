package dev.nxms.codes;

import dev.nxms.codes.commands.CodeCommand;
import dev.nxms.codes.hooks.LuckPermsHook;
import dev.nxms.codes.managers.CodeManager;
import dev.nxms.codes.managers.MessageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Codes extends JavaPlugin {

    private static Codes instance;
    private CodeManager codeManager;
    private MessageManager messageManager;
    private LuckPermsHook luckPermsHook;
    private boolean luckPermsEnabled;

    @Override
    public void onEnable() {
        instance = this;

        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            saveDefaultConfig();

            if (!new java.io.File(getDataFolder(), "messages.yml").exists()) {
                saveResource("messages.yml", false);
            }

            getLogger().info("Inicjalizacja MessageManager...");
            this.messageManager = new MessageManager(this);

            getLogger().info("Inicjalizacja CodeManager...");
            this.codeManager = new CodeManager(this);

            initLuckPerms();

            registerCommands();

            if (getConfig().getBoolean("auto-save.enabled", true)) {
                int interval = getConfig().getInt("auto-save.interval", 5) * 60 * 20;
                getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                    if (codeManager != null) {
                        codeManager.saveCodes();
                    }
                }, interval, interval);
            }

            getLogger().info("Plugin Codes v1.0.0 został włączony!");

        } catch (Exception e) {
            getLogger().severe("Błąd podczas włączania pluginu!");
            e.printStackTrace();
        }
    }

    private void initLuckPerms() {
        this.luckPermsEnabled = false;
        this.luckPermsHook = null;

        if (!getConfig().getBoolean("luckperms-integration", true)) {
            getLogger().info("Integracja z LuckPerms wyłączona w configu.");
            return;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().info("LuckPerms nie znaleziony - używam standardowych permisji.");
            return;
        }

        try {
            this.luckPermsHook = new LuckPermsHook(getLogger());
            if (luckPermsHook.isAvailable()) {
                this.luckPermsEnabled = true;
                getLogger().info("Integracja z LuckPerms włączona!");
            }
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Nie można załadować LuckPerms hook: " + e.getMessage());
            this.luckPermsHook = null;
        }
    }

    @Override
    public void onDisable() {
        if (codeManager != null) {
            codeManager.saveCodes();
        }
        getLogger().info("Plugin Codes został wyłączony!");
    }

    private void registerCommands() {
        PluginCommand kodCommand = getCommand("kod");
        if (kodCommand != null) {
            CodeCommand executor = new CodeCommand(this);
            kodCommand.setExecutor(executor);
            kodCommand.setTabCompleter(executor);
            getLogger().info("Komenda /kod zarejestrowana!");
        } else {
            getLogger().severe("Nie można zarejestrować komendy /kod!");
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

    public void reloadPlugin() {
        reloadConfig();
        if (messageManager != null) {
            messageManager.reload();
        }
        if (codeManager != null) {
            codeManager.loadCodes();
        }
    }
}
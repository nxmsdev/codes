package dev.nxms.codes.managers;

import dev.nxms.codes.Codes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages plugin messages and translations.
 * Supports multiple languages, color formatting, and config placeholder references.
 */
public class MessageManager {

    private final Codes plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private FileConfiguration messagesConfig;
    private String language;

    // Pattern to match {placeholder} format for config references
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");

    public MessageManager(Codes plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Loads/reloads messages from the appropriate language file.
     */
    public void reload() {
        // Ensure default message files exist
        saveDefaultMessages("messages_en.yml");
        saveDefaultMessages("messages_pl.yml");

        // Load configured language
        language = plugin.getConfig().getString("language", "en").toLowerCase();
        String fileName = "messages_" + language + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);

        // Fallback to English if selected language doesn't exist
        if (!file.exists()) {
            plugin.getLogger().warning("Messages file " + fileName + " not found! Using messages_en.yml.");
            fileName = "messages_en.yml";
            file = new File(plugin.getDataFolder(), fileName);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(file);

        // Load defaults from JAR
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            messagesConfig.setDefaults(defaultConfig);
        }

        plugin.getLogger().info("Messages file has been loaded (" + fileName + ").");
    }

    /**
     * Saves a default message file if it doesn't exist.
     */
    private void saveDefaultMessages(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("Couldn't find " + fileName + "! Creating default messages file.");
            plugin.saveResource(fileName, false);
        }
    }

    /**
     * Gets a raw message from config without any processing.
     */
    public String getRaw(String key) {
        return messagesConfig.getString(key, "");
    }

    /**
     * Replaces all {key} placeholders with values from messages file.
     * This allows referencing other message keys within messages (e.g., {prefix}).
     */
    private String replaceConfigPlaceholders(String message) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String value = getRaw(placeholder);

            // Only replace if key exists in config and is not empty
            if (!value.isEmpty()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Gets a formatted message with config placeholders replaced (without prefix).
     */
    public String get(String key) {
        String message = getRaw(key);
        if (message.isEmpty()) {
            plugin.getLogger().warning("Couldn't find message: " + key);
            return "&c[Missing: " + key + "]";
        }
        return replaceConfigPlaceholders(message);
    }

    /**
     * Gets a formatted message with custom placeholders replaced.
     */
    public String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        return applyPlaceholders(message, placeholders);
    }

    /**
     * Gets the prefix from messages file.
     */
    public String getPrefix() {
        return get("prefix");
    }

    /**
     * Converts legacy text with color codes to Adventure Component.
     */
    public Component colorize(String text) {
        return legacy.deserialize(text);
    }

    // ==================== SEND METHODS (WITH PREFIX) ====================

    /**
     * Sends a message with prefix to the sender.
     */
    public void send(CommandSender sender, String key) {
        sender.sendMessage(colorize(getPrefix() + get(key)));
    }

    /**
     * Sends a message with prefix and placeholder replacements.
     */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(colorize(getPrefix() + get(key, placeholders)));
    }

    // ==================== SEND METHODS (WITHOUT PREFIX) ====================

    /**
     * Sends a message without prefix.
     */
    public void sendRaw(CommandSender sender, String key) {
        sender.sendMessage(colorize(get(key)));
    }

    /**
     * Sends a message without prefix with placeholder replacements.
     */
    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(colorize(get(key, placeholders)));
    }

    // ==================== SEND TEXT METHODS ====================

    /**
     * Sends raw text (not from config) to sender.
     */
    public void sendText(CommandSender sender, String text) {
        sender.sendMessage(colorize(text));
    }

    /**
     * Sends raw text with placeholder replacements.
     */
    public void sendText(CommandSender sender, String text, Map<String, String> placeholders) {
        sender.sendMessage(colorize(applyPlaceholders(text, placeholders)));
    }

    // ==================== BROADCAST METHODS ====================

    /**
     * Broadcasts a message with prefix to all players and console.
     */
    public void broadcast(String key) {
        broadcastComponent(colorize(getPrefix() + get(key)));
    }

    /**
     * Broadcasts a message with prefix and placeholders.
     */
    public void broadcast(String key, Map<String, String> placeholders) {
        broadcastComponent(colorize(getPrefix() + get(key, placeholders)));
    }

    /**
     * Broadcasts a message without prefix.
     */
    public void broadcastRaw(String key) {
        broadcastComponent(colorize(get(key)));
    }

    /**
     * Broadcasts a message without prefix with placeholders.
     */
    public void broadcastRaw(String key, Map<String, String> placeholders) {
        broadcastComponent(colorize(get(key, placeholders)));
    }

    /**
     * Broadcasts raw text (not from config).
     */
    public void broadcastText(String text) {
        broadcastComponent(colorize(text));
    }

    /**
     * Broadcasts raw text with placeholders.
     */
    public void broadcastText(String text, Map<String, String> placeholders) {
        broadcastComponent(colorize(applyPlaceholders(text, placeholders)));
    }

    /**
     * Sends a component to all online players and console.
     */
    private void broadcastComponent(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Gets the current language code.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Applies custom placeholders to a message.
     */
    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    /**
     * Helper method to create placeholder maps easily.
     * Usage: placeholders("player", "Steve", "code", "FREE2024")
     */
    public static Map<String, String> placeholders(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
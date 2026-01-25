package dev.nxms.codes.managers;

import dev.nxms.codes.Codes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final Codes plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;

    public MessageManager(Codes plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messagesConfig.getString("prefix", "&8[&6Codes&8] ");
    }

    public String getRaw(String key) {
        return messagesConfig.getString(key, "&cMissing message: " + key);
    }

    public String get(String key) {
        return prefix + getRaw(key);
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public Component getComponent(String key) {
        return colorize(get(key));
    }

    public Component getComponent(String key, Map<String, String> placeholders) {
        return colorize(get(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(getComponent(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(getComponent(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key) {
        sender.sendMessage(colorize(getRaw(key)));
    }

    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(colorize(message));
    }

    public Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
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
        prefix = messagesConfig.getString("prefix", "&8[&6Kody&8] ");
    }

    public String getRaw(String key) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Brak wiadomości w messages.yml: " + key);
            return "&c[Brak: " + key + "]";
        }
        return message;
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

    /** Broadcast z prefixem */
    public void broadcast(String key) {
        Component c = colorize(prefix + getRaw(key));
        broadcastComponent(c);
    }

    /** Broadcast z prefixem + placeholderami */
    public void broadcast(String key, Map<String, String> placeholders) {
        String message = prefix + getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        broadcastComponent(colorize(message));
    }

    /** Broadcast bez prefixu */
    public void broadcastRaw(String key) {
        broadcastComponent(colorize(getRaw(key)));
    }

    /** Broadcast bez prefixu + placeholdery */
    public void broadcastRaw(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        broadcastComponent(colorize(message));
    }

    private void broadcastComponent(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
        }
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
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
        messagesFile = new File(plugin.getDataFolder(), "messages_pl.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messagesConfig.getString("prefix", "&8[&6Kody&8] ");
    }

    public String getRaw(String key) {
        String msg = messagesConfig.getString(key);
        if (msg == null) {
            plugin.getLogger().warning("Brak wiadomości w messages_pl.yml: " + key);
            return "&c[Missing: " + key + "]";
        }
        return msg;
    }

    public String get(String key) {
        return prefix + getRaw(key);
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = get(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            message = message.replace("{" + e.getKey() + "}", e.getValue());
        }
        return message;
    }

    public Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(colorize(get(key)));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(colorize(get(key, placeholders)));
    }

    public void sendRaw(CommandSender sender, String key) {
        sender.sendMessage(colorize(getRaw(key)));
    }

    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            message = message.replace("{" + e.getKey() + "}", e.getValue());
        }
        sender.sendMessage(colorize(message));
    }

    /** Broadcast z prefixem */
    public void broadcast(String key) {
        broadcastComponent(colorize(prefix + getRaw(key)));
    }

    /** Broadcast z prefixem + placeholdery */
    public void broadcast(String key, Map<String, String> placeholders) {
        String message = prefix + getRaw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            message = message.replace("{" + e.getKey() + "}", e.getValue());
        }
        broadcastComponent(colorize(message));
    }

    private void broadcastComponent(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(component);
        }
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
package dev.nxms.codes.commands;

import dev.nxms.codes.Codes;
import dev.nxms.codes.managers.CodeManager;
import dev.nxms.codes.managers.MessageManager;
import dev.nxms.codes.models.Code;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeCommand implements CommandExecutor, TabCompleter {

    private final Codes plugin;
    private final CodeManager codeManager;
    private final MessageManager msg;

    public CodeCommand(Codes plugin) {
        this.plugin = plugin;
        this.codeManager = plugin.getCodeManager();
        this.msg = plugin.getMessageManager();
    }

    // ----------------------------
    // Command
    // ----------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (codeManager == null || msg == null) {
            sender.sendMessage("§cPlugin nie został poprawnie załadowany! Sprawdź logi serwera.");
            return true;
        }

        try {
            if (args.length == 0) {
                // domyślnie help gracza
                sendHelpPlayer(sender);
                return true;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);

            // HELP (PL/EN)
            if (sub.equals("pomoc") || sub.equals("help")) {
                return handleHelp(sender, args);
            }

            // RELOAD (PL/EN)
            if (sub.equals("przeladuj") || sub.equals("reload")) {
                return handleReload(sender);
            }

            // ADMIN: create/delete/list/info (PL/EN)
            if (sub.equals("stworz") || sub.equals("create")) return handleCreate(sender, args, false);
            if (sub.equals("nadpisz") || sub.equals("overwrite")) return handleCreate(sender, args, true);
            if (sub.equals("usun") || sub.equals("delete")) return handleDelete(sender, args);
            if (sub.equals("lista") || sub.equals("list")) return handleList(sender, args);
            if (sub.equals("info")) return handleInfo(sender, args);

            // Otherwise treat as redeem: /code <name> OR /kod <name>
            return handleRedeem(sender, args[0]);

        } catch (Exception e) {
            sender.sendMessage("§cWystąpił błąd! Sprawdź logi serwera.");
            plugin.getLogger().severe("Błąd w komendzie /code:");
            e.printStackTrace();
            return true;
        }
    }

    // ----------------------------
    // HELP
    // ----------------------------

    private boolean handleHelp(CommandSender sender, String[] args) {
        // /code help admin OR /kod pomoc admin
        if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("codes.admin")) {
                msg.send(sender, "no-permission");
                return true;
            }
            sendHelpAdmin(sender);
            return true;
        }

        sendHelpPlayer(sender);
        return true;
    }

    private void sendHelpPlayer(CommandSender sender) {
        if (!sender.hasPermission("codes.player")) {
            msg.send(sender, "no-permission");
            return;
        }
        msg.sendRaw(sender, "help-player-header");
        msg.sendRaw(sender, "help-player-use");
        msg.sendRaw(sender, "help-player-help");
    }

    private void sendHelpAdmin(CommandSender sender) {
        msg.sendRaw(sender, "help-admin-header");
        sender.sendMessage("");
        msg.sendRaw(sender, "help-admin-create");
        msg.sendRaw(sender, "help-admin-overwrite");
        sender.sendMessage("");
        msg.sendRaw(sender, "help-admin-rewards-header");
        msg.sendRaw(sender, "help-admin-reward-item");
        msg.sendRaw(sender, "help-admin-reward-item-material");
        msg.sendRaw(sender, "help-admin-reward-item-amount");
        msg.sendRaw(sender, "help-admin-reward-permission");
        msg.sendRaw(sender, "help-admin-reward-rank");
        sender.sendMessage("");
        msg.sendRaw(sender, "help-admin-unlimited-hint");
        msg.sendRaw(sender, "help-admin-delay-hint");
        msg.sendRaw(sender, "help-admin-broadcast-hint");
        sender.sendMessage("");
        msg.sendRaw(sender, "help-admin-delete");
        msg.sendRaw(sender, "help-admin-list-active");
        msg.sendRaw(sender, "help-admin-list-used");
        msg.sendRaw(sender, "help-admin-list-clear");
        msg.sendRaw(sender, "help-admin-info");
        msg.sendRaw(sender, "help-admin-reload");
    }

    // ----------------------------
    // CREATE
    // Syntax:
    // /code create  <name> <globalUses> <playerUses> <delay> <announce> <reward>
    // /kod  stworz  <nazwa> ...
    // delay = seconds, announce = yes/no or tak/nie/true/false
    // reward: item/przedmiot | item:MAT:amt / przedmiot:MAT:amt | permission:... / permisja:... | rank:... / ranga:...
    // ----------------------------

    private boolean handleCreate(CommandSender sender, String[] args, boolean overwrite) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 7) {
            msg.send(sender, "invalid-usage-create");
            msg.send(sender, "invalid-usage-create-hint");
            return true;
        }

        String codeName = args[1];

        int maxGlobalUses;
        int maxPlayerUses;
        int delaySeconds;

        try {
            maxGlobalUses = Integer.parseInt(args[2]);
            maxPlayerUses = Integer.parseInt(args[3]);
            delaySeconds = parseDelayToSeconds(args[4]);
            if (delaySeconds < 0) {
                msg.send(sender, "invalid-delay-format", MessageManager.placeholders(
                        "value", args[4]
                ));
                return true;
            }
        } catch (NumberFormatException e) {
            msg.send(sender, "invalid-number");
            return true;
        }

        if (maxGlobalUses < 0 || maxPlayerUses < 0 || delaySeconds < 0) {
            msg.send(sender, "invalid-number-negative");
            return true;
        }

        Boolean announce = parseBoolean(args[5]);
        if (announce == null) {
            msg.send(sender, "invalid-broadcast");
            return true;
        }

        String rewardArg = args[6];
        boolean success;

        // ITEM in hand: item/przedmiot
        if (rewardArg.equalsIgnoreCase("item") || rewardArg.equalsIgnoreCase("przedmiot")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "must-be-player");
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType() == Material.AIR) {
                msg.send(sender, "must-hold-item");
                return true;
            }
            success = overwrite
                    ? codeManager.overwriteCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, held.clone())
                    : codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, held.clone());
        }
        // ITEM by name: item:... / przedmiot:...
        else if (rewardArg.toLowerCase(Locale.ROOT).startsWith("item:") || rewardArg.toLowerCase(Locale.ROOT).startsWith("przedmiot:")) {
            String data = rewardArg.substring(rewardArg.indexOf(':') + 1);
            String[] parts = data.split(":");

            if (parts.length < 1 || parts[0].isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }

            String materialName = parts[0].toUpperCase(Locale.ROOT);
            int amount = 1;

            if (parts.length >= 2) {
                try { amount = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }

            Material mat = Material.matchMaterial(materialName);
            if (mat == null) {
                msg.send(sender, "unknown-item", MessageManager.placeholders("item", materialName));
                msg.send(sender, "unknown-item-hint");
                return true;
            }

            if (amount < 1) amount = 1;
            if (amount > 64) amount = 64;

            ItemStack reward = new ItemStack(mat, amount);
            success = overwrite
                    ? codeManager.overwriteCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, reward)
                    : codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, reward);
        }
        // PERMISSION: permission:... / permisja:...
        else if (rewardArg.toLowerCase(Locale.ROOT).startsWith("permission:") || rewardArg.toLowerCase(Locale.ROOT).startsWith("permisja:")) {
            String perm = rewardArg.substring(rewardArg.indexOf(':') + 1);
            if (perm.isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }
            success = overwrite
                    ? codeManager.overwriteCodeWithPermission(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, perm)
                    : codeManager.createCodeWithPermission(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, perm);
        }
        // RANK: rank:... / ranga:...
        else if (rewardArg.toLowerCase(Locale.ROOT).startsWith("rank:") || rewardArg.toLowerCase(Locale.ROOT).startsWith("ranga:")) {
            String rank = rewardArg.substring(rewardArg.indexOf(':') + 1);
            if (rank.isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }

            if (!codeManager.isLuckPermsAvailable()) {
                msg.send(sender, "rank-requires-luckperms");
                return true;
            }
            if (!codeManager.rankExists(rank)) {
                msg.send(sender, "rank-not-found", MessageManager.placeholders("reward", rank));
                return true;
            }

            success = overwrite
                    ? codeManager.overwriteCodeWithRank(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, rank)
                    : codeManager.createCodeWithRank(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, rank);
        }
        else {
            msg.send(sender, "invalid-reward-type");
            return true;
        }

        if (!success) {
            msg.send(sender, "code-already-exists", MessageManager.placeholders("code", codeName));
            return true;
        }

        msg.send(sender, "code-created", MessageManager.placeholders("code", codeName));

        if (maxGlobalUses == Code.UNLIMITED) msg.send(sender, "code-created-unlimited-global");
        if (maxPlayerUses == Code.UNLIMITED) msg.send(sender, "code-created-unlimited-player");
        if (delaySeconds > 0) {
            msg.send(sender, "code-created-delay", MessageManager.placeholders(
                    "delay", formatDuration(delaySeconds)
            ));
        }

        if (announce) msg.send(sender, "code-created-broadcast-on");
        else msg.send(sender, "code-created-broadcast-off");

        return true;
    }

    private static Boolean parseBoolean(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        if (v.equals("tak") || v.equals("true") || v.equals("yes")) return true;
        if (v.equals("nie") || v.equals("false") || v.equals("no")) return false;
        return null;
    }

    private String getLang() {
        return plugin.getConfig().getString("language", "pl").toLowerCase(Locale.ROOT);
    }

    private String formatDurationLong(int seconds) {
        if (seconds <= 0) {
            // możesz też zwrócić msg.getRaw("time-none") jeśli masz taki klucz
            return getLang().equals("en") ? "0 seconds" : "0 sekund";
        }

        int s = seconds;

        int days = s / 86400; s %= 86400;
        int hours = s / 3600; s %= 3600;
        int minutes = s / 60; s %= 60;

        List<String> parts = new ArrayList<>();
        boolean en = getLang().equals("en");

        if (days > 0) parts.add(en ? (days + " " + enPlural(days, "day", "days")) : (days + " " + plPlural(days, "dzień", "dni", "dni")));
        if (hours > 0) parts.add(en ? (hours + " " + enPlural(hours, "hour", "hours")) : (hours + " " + plPlural(hours, "godzina", "godziny", "godzin")));
        if (minutes > 0) parts.add(en ? (minutes + " " + enPlural(minutes, "minute", "minutes")) : (minutes + " " + plPlural(minutes, "minuta", "minuty", "minut")));
        if (s > 0) parts.add(en ? (s + " " + enPlural(s, "second", "seconds")) : (s + " " + plPlural(s, "sekunda", "sekundy", "sekund")));

        return String.join(en ? " " : " ", parts);
    }

    private String enPlural(int value, String singular, String plural) {
        return value == 1 ? singular : plural;
    }

    /**
     * Polish plural rules:
     * 1 -> one
     * 2-4 (except 12-14) -> few
     * others -> many
     */
    private String plPlural(int value, String one, String few, String many) {
        int mod10 = value % 10;
        int mod100 = value % 100;

        if (value == 1) return one;
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return few;
        return many;
    }

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");

    private int parseDelayToSeconds(String input) {
        if (input == null) return -1;
        String s = input.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

        // sama liczba = sekundy
        if (s.matches("\\d+")) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        Matcher m = DURATION_PART.matcher(s);
        long total = 0;
        int lastEnd = 0;

        while (m.find()) {
            // wymuś brak “dziur” w stringu (np. 1m-10s -> błąd)
            if (m.start() != lastEnd) return -1;

            long value;
            try {
                value = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }

            String unit = m.group(2);
            switch (unit) {
                case "d" -> total += value * 86400L;
                case "h" -> total += value * 3600L;
                case "m" -> total += value * 60L;
                case "s" -> total += value;
                default -> { return -1; }
            }

            if (total > Integer.MAX_VALUE) return -1;
            lastEnd = m.end();
        }

        // jeśli nie dopasowało nic albo zostały śmieci na końcu
        if (lastEnd != s.length() || lastEnd == 0) return -1;

        return (int) total;
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return msg.getRaw("time-none"); // PL/EN zależnie od messages

        int s = seconds;
        int d = s / 86400; s %= 86400;
        int h = s / 3600;  s %= 3600;
        int m = s / 60;    s %= 60;

        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append("d");
        if (h > 0) out.append(h).append("h");
        if (m > 0) out.append(m).append("m");
        if (s > 0) out.append(s).append("s");

        return out.length() == 0 ? "0s" : out.toString();
    }

    // ----------------------------
    // DELETE
    // /code delete <name>
    // /kod usun <nazwa>
    // ----------------------------

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            msg.send(sender, "invalid-usage-delete");
            return true;
        }

        String codeName = args[1];
        boolean existedBefore = codeManager.existsAny(codeName);
        if (codeManager.deleteCode(codeName)) {
            msg.send(sender, "code-deleted", MessageManager.placeholders("code", codeName));
        } else {
            msg.send(sender, "code-not-found", MessageManager.placeholders("code", codeName));
        }
        return true;
    }

    // ----------------------------
    // LIST
    // /code list active
    // /code list used
    // /code list used clear
    // and PL equivalents:
    // /kod lista aktywne
    // /kod lista zuzyte
    // /kod lista zuzyte wyczysc
    // ----------------------------

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            msg.sendRaw(sender, "list-usage-header");
            msg.sendRaw(sender, "list-usage-active");
            msg.sendRaw(sender, "list-usage-used");
            msg.sendRaw(sender, "list-usage-clear");
            return true;
        }

        String type = args[1].toLowerCase(Locale.ROOT);

        if (type.equals("aktywne") || type.equals("active")) {
            return handleListActive(sender);
        }

        if (type.equals("zuzyte") || type.equals("used")) {
            return handleListUsed(sender, args);
        }

        msg.sendRaw(sender, "list-unknown-type", MessageManager.placeholders("type", type));
        msg.sendRaw(sender, "list-available-types");
        return true;
    }

    private boolean handleListActive(CommandSender sender) {
        msg.sendRaw(sender, "list-active-header");

        if (codeManager.getAllCodes().isEmpty()) {
            msg.sendRaw(sender, "list-active-empty");
            return true;
        }

        UUID viewer = (sender instanceof Player p) ? p.getUniqueId() : null;

        for (Code code : codeManager.getAllCodes()) {
            String gUsed = String.valueOf(code.getGlobalUses());
            String gMax = code.isGlobalUnlimited() ? "∞" : String.valueOf(code.getMaxGlobalUses());

            String pUsed = "-";
            String pMax = code.isPlayerUnlimited() ? "∞" : String.valueOf(code.getMaxPlayerUses());
            if (viewer != null) {
                pUsed = String.valueOf(code.getPlayerUseCount(viewer));
            }

            msg.sendRaw(sender, "list-active-entry", MessageManager.placeholders(
                    "code", code.getName(),
                    "global_used", gUsed,
                    "global_max", gMax,
                    "player_used", pUsed,
                    "player_max", pMax
            ));
        }

        return true;
    }

    private boolean handleListUsed(CommandSender sender, String[] args) {
        // /list used clear OR /lista zuzyte wyczysc
        if (args.length >= 3) {
            String third = args[2].toLowerCase(Locale.ROOT);
            if (third.equals("wyczysc") || third.equals("clear")) {
                if (codeManager.clearUsedCodes()) {
                    msg.send(sender, "list-used-cleared");
                } else {
                    msg.sendRaw(sender, "list-used-clear-empty");
                }
                return true;
            }
        }

        msg.sendRaw(sender, "list-used-header");

        var used = codeManager.getUsedCodes();
        if (used.isEmpty()) {
            msg.sendRaw(sender, "list-used-empty");
            return true;
        }

        UUID viewer = (sender instanceof Player p) ? p.getUniqueId() : null;

        for (CodeManager.UsedCodeInfo info : used) {
            String gUsed = String.valueOf(info.totalUses());
            String gMax = (info.maxGlobalUses() == 0) ? "∞" : String.valueOf(info.maxGlobalUses());

            String pUsed = "-";
            String pMax = (info.maxPlayerUses() == 0) ? "∞" : String.valueOf(info.maxPlayerUses());
            if (viewer != null && info.playerUses() != null) {
                pUsed = String.valueOf(info.playerUses().getOrDefault(viewer, 0));
            }

            msg.sendRaw(sender, "list-used-entry", MessageManager.placeholders(
                    "code", info.name(),
                    "reward", info.rewardDisplay(),
                    "global_used", gUsed,
                    "global_max", gMax,
                    "player_used", pUsed,
                    "player_max", pMax
            ));
        }

        msg.sendRaw(sender, "list-used-clear-hint");
        return true;
    }

    // ----------------------------
    // INFO
    // /code info <name>
    // ----------------------------

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            msg.sendRaw(sender, "info-usage");
            return true;
        }

        Code code = codeManager.getCode(args[1]);
        if (code == null) {
            msg.send(sender, "code-not-found", MessageManager.placeholders("code", args[1]));
            return true;
        }

        msg.sendRaw(sender, "info-header", MessageManager.placeholders("code", code.getName()));

        if (code.isGlobalUnlimited()) {
            msg.sendRaw(sender, "info-global-uses-unlimited", MessageManager.placeholders(
                    "current", String.valueOf(code.getGlobalUses())
            ));
        } else {
            msg.sendRaw(sender, "info-global-uses", MessageManager.placeholders(
                    "current", String.valueOf(code.getGlobalUses()),
                    "max", String.valueOf(code.getMaxGlobalUses())
            ));
        }

        if (code.isPlayerUnlimited()) {
            msg.sendRaw(sender, "info-player-uses-unlimited");
        } else {
            msg.sendRaw(sender, "info-player-uses", MessageManager.placeholders(
                    "max", String.valueOf(code.getMaxPlayerUses())
            ));
        }

        msg.sendRaw(sender, "info-delay", MessageManager.placeholders(
                "delay", formatDuration(code.getCooldown())
        ));
        msg.sendRaw(sender, "info-broadcast", MessageManager.placeholders("broadcast", code.getBroadcastDisplay()));

        msg.sendRaw(sender, "info-reward-type", MessageManager.placeholders("type", code.getRewardType().name()));
        msg.sendRaw(sender, "info-reward", MessageManager.placeholders("reward", code.getRewardDisplay()));

        return true;
    }

    // ----------------------------
    // RELOAD
    // ----------------------------

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }
        plugin.reloadPlugin();
        msg.send(sender, "reload-success");
        return true;
    }

    // ----------------------------
    // REDEEM
    // /code <name>
    // ----------------------------

    private boolean handleRedeem(CommandSender sender, String codeName) {
        if (!sender.hasPermission("codes.player")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            msg.send(sender, "must-be-player");
            return true;
        }

        Code code = codeManager.getCode(codeName);
        if (code == null) {
            msg.send(sender, "code-not-found", MessageManager.placeholders("code", codeName));
            return true;
        }

        int remaining = code.getRemainingCooldown(player.getUniqueId());
        if (remaining > 0) {
            String timeText = formatDurationLong(remaining);

            msg.send(player, "code-cooldown", MessageManager.placeholders(
                    "time", timeText,
                    "seconds", String.valueOf(remaining)
            ));
            return true;
        }

        if (!code.canBeUsed()) {
            msg.send(sender, "code-expired");
            return true;
        }

        if (!code.canPlayerUse(player.getUniqueId())) {
            msg.send(sender, "code-already-used");
            return true;
        }

        CodeManager.RedeemResult result = codeManager.redeemCode(player, codeName);


        switch (result) {
            case COOLDOWN -> {
                int remaining2 = code.getRemainingCooldown(player.getUniqueId());
                String timeText2 = formatDurationLong(remaining2);

                msg.send(player, "code-cooldown", MessageManager.placeholders(
                        "time", timeText2,
                        "seconds", String.valueOf(remaining2)
                ));
            }
            case SUCCESS_ITEM -> {
                msg.send(player, "code-redeemed");
                msg.send(player, "reward-received-item");

                if (code.isBroadcast()) {
                    String rewardDisplay = getItemDisplayName(code.getItemReward());
                    msg.broadcast("broadcast-reward-item", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", rewardDisplay
                    ));
                }
            }
            case SUCCESS_PERMISSION -> {
                msg.send(player, "code-redeemed");
                msg.send(player, "reward-received-permission",
                        MessageManager.placeholders("reward", code.getPermissionReward()));

                if (code.isBroadcast()) {
                    msg.broadcast("broadcast-reward-permission", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", code.getPermissionReward()
                    ));
                }
            }
            case SUCCESS_RANK -> {
                msg.send(player, "code-redeemed");
                msg.send(player, "reward-received-rank",
                        MessageManager.placeholders("reward", code.getRankReward()));

                if (code.isBroadcast()) {
                    msg.broadcast("broadcast-reward-rank", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", code.getRankReward()
                    ));
                }
            }
            case RANK_ERROR -> msg.send(player, "rank-requires-luckperms");
            default -> msg.send(player, "code-not-found", MessageManager.placeholders("code", codeName));
        }

        return true;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "Item";

        int amount = item.getAmount();

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component dn = item.getItemMeta().displayName();
            if (dn != null) {
                String name = PlainTextComponentSerializer.plainText().serialize(dn);
                return name + " x" + amount;
            }
        }

        // fallback: DIAMOND_SWORD -> Diamond Sword
        String materialName = item.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");
        String[] words = materialName.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) formatted.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return formatted.toString().trim() + " x" + amount;
    }

    // ----------------------------
    // TAB COMPLETE
    // - gracz: tylko help/pomoc + <code_name>
    // - admin: komendy (PL+EN), ale podpowiedzi zależą od language w configu (preferowany język)
    // ----------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (codeManager == null) return Collections.emptyList();

        boolean isAdmin = sender.hasPermission("codes.admin");
        boolean isPlayer = sender.hasPermission("codes.player");

        List<String> out = new ArrayList<>();

        boolean pl = label.equalsIgnoreCase("kod"); // /kod = PL, /code = EN

        // 1 argument
        if (args.length == 1) {
            if (isAdmin) {
                // Prefer language, but accept both
                if (pl) {
                    out.addAll(List.of("stworz", "nadpisz", "usun", "lista", "info", "przeladuj", "pomoc"));
                } else {
                    out.addAll(List.of("create", "overwrite", "delete", "list", "info", "reload", "help"));
                }
            } else if (isPlayer) {
                // player cannot tab real code names
                if (pl) out.addAll(List.of("pomoc", "<nazwa_kodu>"));
                if (!pl) out.addAll(List.of("help", "<code_name>"));
            }
            return filter(out, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // help/pomoc admin
        if (args.length == 2 && (sub.equals("pomoc") || sub.equals("help")) && isAdmin) {
            out.add("admin");
            return filter(out, args[1]);
        }

        // delete/usun + info => suggest existing code names for admins
        if (args.length == 2 && isAdmin && (sub.equals("usun") || sub.equals("delete") || sub.equals("info"))) {
            out.addAll(codeManager.getCodeNames());
            return filter(out, args[1]);
        }

        // list/lista second arg
        if (args.length == 2 && isAdmin && (sub.equals("lista") || sub.equals("list"))) {
            if (pl) out.addAll(List.of("aktywne", "zuzyte"));
            if (!pl) out.addAll(List.of("active", "used"));
            out.addAll(List.of("aktywne", "zuzyte", "active", "used"));
            return filter(out, args[1]);
        }

        // list used clear / lista zuzyte wyczysc
        if (args.length == 3 && isAdmin && (sub.equals("lista") || sub.equals("list"))) {
            String type = args[1].toLowerCase(Locale.ROOT);
            if (type.equals("zuzyte") || type.equals("used")) {
                out.addAll(List.of(pl ? "wyczysc" : "clear"));
                return filter(out, args[2]);
            }
        }

        if (isAdmin && ((sub.equals("stworz") || sub.equals("create") || sub.equals("nadpisz") || sub.equals("overwrite")))) {
            if (args.length == 2) return filter(List.of(pl ? "<nazwa_kodu>" : "<code_name>"), args[1]);
            if (args.length == 3) return filter(List.of(pl ? "<użycia_ogólne>" : "<global_uses>"), args[2]);
            if (args.length == 4) return filter(List.of(pl ? "<użycia_gracza>" : "<player_uses>"), args[3]);
            if (args.length == 5) {
                return filter(List.of(
                        pl ? "<opóźnienie>" : "<delay>",
                        "10s", "30s", "1m", "5m", "1h", "1d", "2m30s", "1d2h2m30s"
                ), args[4]);
            }
            if (args.length == 6) return filter(List.of(pl ? "<ogłoszenie>" : "<broadcast>", pl ? "tak": "yes", pl ? "nie" : "no"), args[5]);

            if (args.length == 7) {
                String cur = args[6].toLowerCase(Locale.ROOT);

                // reward keywords (both languages)
                out.addAll(List.of(
                        pl ? "<nagroda>" : "<reward>",
                        pl ? "przedmiot" : "item",
                        pl ? "przedmiot:" : "item:",
                        pl ? "permisja:": "permission:",
                        pl ? "ranga:" : "rank:"
                ));

                // if starts with item/przedmiot:
                if (cur.startsWith("przedmiot:") || cur.startsWith("item:")) {
                    String after = cur.substring(cur.indexOf(':') + 1).toUpperCase(Locale.ROOT);

                    List<String> popular = Arrays.asList(
                            "DIAMOND", "DIAMOND_BLOCK", "DIAMOND_SWORD",
                            "IRON_INGOT", "IRON_BLOCK",
                            "GOLD_INGOT", "GOLD_BLOCK",
                            "EMERALD", "EMERALD_BLOCK",
                            "NETHERITE_INGOT", "NETHERITE_BLOCK",
                            "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE",
                            "EXPERIENCE_BOTTLE", "NETHER_STAR",
                            "ELYTRA", "TOTEM_OF_UNDYING"
                    );

                    String prefix = cur.startsWith("item:") ? "item:" : "przedmiot:";
                    for (String it : popular) {
                        if (it.startsWith(after)) {
                            out.add(prefix + it);
                            out.add(prefix + it + ":64");
                        }
                    }

                    if (after.length() >= 2) {
                        Arrays.stream(Material.values())
                                .filter(Material::isItem)
                                .filter(m -> !m.name().startsWith("LEGACY_"))
                                .filter(m -> m.name().startsWith(after))
                                .limit(15)
                                .forEach(m -> {
                                    out.add(prefix + m.name());
                                    out.add(prefix + m.name() + ":64");
                                });
                    }
                }

                return filter(out, args[6]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String token) {
        String low = token.toLowerCase(Locale.ROOT);
        return list.stream()
                .distinct()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low))
                .sorted()
                .collect(Collectors.toList());
    }
}
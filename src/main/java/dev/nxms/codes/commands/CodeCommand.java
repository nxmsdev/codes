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
            if (sub.equals("stworz") || sub.equals("create")) return handleCreate(sender, args);
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

    private boolean handleCreate(CommandSender sender, String[] args) {
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
            delaySeconds = Integer.parseInt(args[4]);
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
            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, held.clone());
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
            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, reward);
        }
        // PERMISSION: permission:... / permisja:...
        else if (rewardArg.toLowerCase(Locale.ROOT).startsWith("permission:") || rewardArg.toLowerCase(Locale.ROOT).startsWith("permisja:")) {
            String perm = rewardArg.substring(rewardArg.indexOf(':') + 1);
            if (perm.isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }
            success = codeManager.createCodeWithPermission(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, perm);
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

            success = codeManager.createCodeWithRank(codeName, maxGlobalUses, maxPlayerUses, delaySeconds, announce, rank);
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
        if (delaySeconds > 0) msg.send(sender, "code-created-delay", MessageManager.placeholders("seconds", String.valueOf(delaySeconds)));

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

        msg.sendRaw(sender, "info-delay", MessageManager.placeholders("delay", code.getCooldownDisplay()));
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

        if (code.isOnCooldown(player.getUniqueId())) {
            int remaining = code.getRemainingCooldown(player.getUniqueId());
            msg.send(sender, "code-cooldown", MessageManager.placeholders("seconds", String.valueOf(remaining)));
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
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {

        if (codeManager == null) return Collections.emptyList();

        boolean isAdmin = sender.hasPermission("codes.admin");
        boolean isPlayer = sender.hasPermission("codes.player");

        String lang = plugin.getConfig().getString("language", "pl").toLowerCase(Locale.ROOT);
        boolean pl = lang.equals("pl");
        boolean en = lang.equals("en");

        List<String> out = new ArrayList<>();

        // 1 argument
        if (args.length == 1) {
            if (isAdmin) {
                // Prefer language, but accept both
                if (pl) {
                    out.addAll(List.of("stworz", "usun", "lista", "info", "przeladuj", "pomoc"));
                } else {
                    out.addAll(List.of("create", "delete", "list", "info", "reload", "help"));
                }

                // also show the other language variants (optional but useful)
                out.addAll(List.of("stworz", "usun", "lista", "przeladuj", "pomoc"));
                out.addAll(List.of("create", "delete", "list", "reload", "help"));
            } else if (isPlayer) {
                // player cannot tab real code names
                if (pl) out.add("pomoc");
                if (en) out.add("help");
                out.addAll(List.of("pomoc", "help", "<code_name>"));
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
            if (en) out.addAll(List.of("active", "used"));
            out.addAll(List.of("aktywne", "zuzyte", "active", "used"));
            return filter(out, args[1]);
        }

        // list used clear / lista zuzyte wyczysc
        if (args.length == 3 && isAdmin && (sub.equals("lista") || sub.equals("list"))) {
            String type = args[1].toLowerCase(Locale.ROOT);
            if (type.equals("zuzyte") || type.equals("used")) {
                out.addAll(List.of("wyczysc", "clear"));
                return filter(out, args[2]);
            }
        }

        // create/stworz args: name/global/player/delay/announce/reward
        if (isAdmin && (sub.equals("stworz") || sub.equals("create"))) {
            if (args.length == 2) return filter(List.of(pl ? "<nazwa_kodu>" : "<code_name>", "<code_name>"), args[1]);
            if (args.length == 3) return filter(List.of(pl ? "<użycia_ogólne>" : "<global_uses>", "<global_uses>"), args[2]);
            if (args.length == 4) return filter(List.of(pl ? "<użycia_gracza>" : "<player_uses>", "<player_uses>"), args[3]);
            if (args.length == 5) return filter(List.of(pl ? "<opóźnienie_sekundy>" : "<delay_seconds>", "<delay_seconds>"), args[4]);
            if (args.length == 6) return filter(List.of(pl ? "tak" : "yes", pl ? "nie" : "no", "true", "false", "tak", "nie", "yes", "no"), args[5]);

            if (args.length == 7) {
                String cur = args[6].toLowerCase(Locale.ROOT);

                // reward keywords (both languages)
                out.addAll(List.of(
                        "przedmiot", "przedmiot:",
                        "item", "item:",
                        "permisja:", "permission:",
                        "ranga:", "rank:"
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
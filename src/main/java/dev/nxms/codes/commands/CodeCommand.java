package dev.nxms.codes.commands;

import dev.nxms.codes.Codes;
import dev.nxms.codes.managers.CodeManager;
import dev.nxms.codes.managers.MessageManager;
import dev.nxms.codes.models.Code;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (codeManager == null || msg == null) {
            sender.sendMessage("§cPlugin nie został poprawnie załadowany! Sprawdź logi serwera.");
            return true;
        }

        try {
            if (args.length == 0) {
                sendHelpPlayer(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "stworz":
                    return handleCreate(sender, args);
                case "usun":
                    return handleDelete(sender, args);
                case "lista":
                    return handleList(sender, args);
                case "przeladuj":
                    return handleReload(sender);
                case "info":
                    return handleInfo(sender, args);
                case "pomoc":
                    return handleHelp(sender, args);
                default:
                    return handleRedeem(sender, args[0]);
            }
        } catch (Exception e) {
            sender.sendMessage("§cWystąpił błąd! Sprawdź logi serwera.");
            plugin.getLogger().severe("Błąd w komendzie /kod:");
            e.printStackTrace();
            return true;
        }
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
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

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 5) {
            msg.send(sender, "invalid-usage-create");
            msg.send(sender, "invalid-usage-create-hint");
            return true;
        }

        String codeName = args[1];
        int maxGlobalUses;
        int maxPlayerUses;

        try {
            maxGlobalUses = Integer.parseInt(args[2]);
            maxPlayerUses = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msg.send(sender, "invalid-number");
            return true;
        }

        if (maxGlobalUses < 0 || maxPlayerUses < 0) {
            msg.send(sender, "invalid-number-negative");
            return true;
        }

        String rewardArg = args[4];
        boolean success;

        if (rewardArg.equalsIgnoreCase("item") || rewardArg.equalsIgnoreCase("przedmiot")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "must-be-player");
                return true;
            }

            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem == null || heldItem.getType() == Material.AIR) {
                msg.send(sender, "must-hold-item");
                return true;
            }

            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, heldItem.clone());
        }
        else if (rewardArg.toLowerCase().startsWith("item:") || rewardArg.toLowerCase().startsWith("przedmiot:")) {
            String rewardData = rewardArg.substring(rewardArg.indexOf(':') + 1);
            String[] parts = rewardData.split(":");

            if (parts.length < 1 || parts[0].isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }

            String materialName = parts[0].toUpperCase();
            int amount = 1;

            if (parts.length >= 2) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    amount = 1;
                }
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                sender.sendMessage("§cNieznany przedmiot: §e" + materialName);
                sender.sendMessage("§7Użyj nazwy jak: DIAMOND, IRON_INGOT, GOLDEN_APPLE");
                return true;
            }

            if (amount < 1) amount = 1;
            if (amount > 64) amount = 64;

            ItemStack itemReward = new ItemStack(material, amount);
            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, itemReward);
        }
        else if (rewardArg.toLowerCase().startsWith("permisja:")) {
            String permission = rewardArg.substring(9);
            if (permission.isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }
            success = codeManager.createCodeWithPermission(codeName, maxGlobalUses, maxPlayerUses, permission);
        }
        else if (rewardArg.toLowerCase().startsWith("ranga:")) {
            String rank = rewardArg.substring(6);
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

            success = codeManager.createCodeWithRank(codeName, maxGlobalUses, maxPlayerUses, rank);
        }
        else {
            msg.send(sender, "invalid-reward-type");
            return true;
        }

        if (success) {
            msg.send(sender, "code-created", MessageManager.placeholders("code", codeName));

            if (maxGlobalUses == Code.UNLIMITED) {
                msg.send(sender, "code-created-unlimited-global");
            }
            if (maxPlayerUses == Code.UNLIMITED) {
                msg.send(sender, "code-created-unlimited-player");
            }
        } else {
            msg.send(sender, "code-already-exists", MessageManager.placeholders("code", codeName));
        }

        return true;
    }

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

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(msg.colorize("&cPoprawne użycie:"));
            sender.sendMessage(msg.colorize("&e/kod lista aktywne &7- Lista aktywnych kodów"));
            sender.sendMessage(msg.colorize("&e/kod lista zuzyte &7- Lista zużytych kodów"));
            sender.sendMessage(msg.colorize("&e/kod lista zuzyte wyczysc &7- Wyczyść zużyte kody"));
            return true;
        }

        String listType = args[1].toLowerCase();

        switch (listType) {
            case "aktywne":
                return handleListActive(sender);
            case "zuzyte":
                return handleListUsed(sender, args);
            default:
                sender.sendMessage(msg.colorize("&cNieznany typ listy: &e" + listType));
                sender.sendMessage(msg.colorize("&7Dostępne: &eaktywne&7, &ezuzyte"));
                return true;
        }
    }

    private boolean handleListActive(CommandSender sender) {
        sender.sendMessage(msg.colorize("&6&m─────&r &6Lista aktywnych kodów &6&m─────"));

        if (codeManager.getAllCodes().isEmpty()) {
            sender.sendMessage(msg.colorize("&7Brak aktywnych kodów."));
        } else {
            for (Code code : codeManager.getAllCodes()) {
                if (code.isGlobalUnlimited()) {
                    sender.sendMessage(msg.colorize("&7• &e" + code.getName() + " &7(&a∞ nielimitowane&7)"));
                } else {
                    sender.sendMessage(msg.colorize("&7• &e" + code.getName() + " &7(Pozostało: &a" + code.getRemainingGlobalUsesDisplay() + "&7)"));
                }
            }
        }

        return true;
    }

    private boolean handleListUsed(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("wyczysc")) {
            if (codeManager.clearUsedCodes()) {
                msg.send(sender, "used-codes-cleared");
            } else {
                sender.sendMessage(msg.colorize("&7Brak zużytych kodów do wyczyszczenia."));
            }
            return true;
        }

        sender.sendMessage(msg.colorize("&6&m─────&r &6Lista zużytych kodów &6&m─────"));

        var usedCodes = codeManager.getUsedCodes();
        if (usedCodes.isEmpty()) {
            sender.sendMessage(msg.colorize("&7Brak zużytych kodów."));
        } else {
            for (CodeManager.UsedCodeInfo info : usedCodes) {
                sender.sendMessage(msg.colorize("&7• &e" + info.name() + " &7| &a" + info.totalUses() + "x &7| " + info.rewardDisplay()));
            }
            sender.sendMessage(msg.colorize("&7Wpisz &e/kod lista zuzyte wyczysc &7aby wyczyścić listę"));
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cPoprawne użycie: /kod info <nazwa>");
            return true;
        }

        Code code = codeManager.getCode(args[1]);
        if (code == null) {
            msg.send(sender, "code-not-found", MessageManager.placeholders("code", args[1]));
            return true;
        }

        sender.sendMessage(msg.colorize("&6&m─────&r &6Kod: " + code.getName() + " &6&m─────"));

        if (code.isGlobalUnlimited()) {
            sender.sendMessage(msg.colorize("&7Użycia globalne: &e" + code.getGlobalUses() + "&7/&a∞"));
        } else {
            sender.sendMessage(msg.colorize("&7Użycia globalne: &e" + code.getGlobalUses() + "&7/&e" + code.getMaxGlobalUses()));
        }

        if (code.isPlayerUnlimited()) {
            sender.sendMessage(msg.colorize("&7Max użyć na gracza: &a∞"));
        } else {
            sender.sendMessage(msg.colorize("&7Max użyć na gracza: &e" + code.getMaxPlayerUses()));
        }

        sender.sendMessage(msg.colorize("&7Typ nagrody: &e" + code.getRewardType().name()));
        sender.sendMessage(msg.colorize("&7Nagroda: &e" + code.getRewardDisplay()));

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("codes.admin")) {
            msg.send(sender, "no-permission");
            return true;
        }

        plugin.reloadPlugin();
        msg.send(sender, "reload-success");
        return true;
    }

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
            case SUCCESS_ITEM:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-item");
                break;
            case SUCCESS_PERMISSION:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-permission",
                        MessageManager.placeholders("reward", code.getPermissionReward()));
                break;
            case SUCCESS_RANK:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-rank",
                        MessageManager.placeholders("reward", code.getRankReward()));
                break;
            case RANK_ERROR:
                msg.send(player, "rank-requires-luckperms");
                break;
            default:
                msg.send(player, "code-not-found", MessageManager.placeholders("code", codeName));
        }

        return true;
    }

    private void sendHelpPlayer(CommandSender sender) {
        if (!sender.hasPermission("codes.player")) {
            msg.send(sender, "no-permission");
            return;
        }

        sender.sendMessage(msg.colorize("&6&m─────&r &6Pomoc - Kody &6&m─────"));
        sender.sendMessage(msg.colorize("&e/kod <nazwa> &7- Wykorzystaj kod"));
        sender.sendMessage(msg.colorize("&e/kod pomoc &7- Wyświetla tę pomoc"));
    }

    private void sendHelpAdmin(CommandSender sender) {
        sender.sendMessage(msg.colorize("&6&m─────&r &6Pomoc Admin - Kody &6&m─────"));
        sender.sendMessage(msg.colorize(""));
        sender.sendMessage(msg.colorize("&e/kod stworz <nazwa> <globalne> <per_gracz> <nagroda>"));
        sender.sendMessage(msg.colorize(""));
        sender.sendMessage(msg.colorize("&7Typy nagród:"));
        sender.sendMessage(msg.colorize("&7  • &eprzedmiot &7- przedmiot trzymany w ręce"));
        sender.sendMessage(msg.colorize("&7  • &eprzedmiot:MATERIAL &7- np. przedmiot:DIAMOND"));
        sender.sendMessage(msg.colorize("&7  • &eprzedmiot:MATERIAL:ilość &7- np. przedmiot:DIAMOND:64"));
        sender.sendMessage(msg.colorize("&7  • &epermisja:nazwa &7- np. permisja:vip.fly"));
        sender.sendMessage(msg.colorize("&7  • &eranga:nazwa &7- np. ranga:vip &7(wymaga LuckPerms)"));
        sender.sendMessage(msg.colorize(""));
        sender.sendMessage(msg.colorize("&7Użyj &e0 &7dla nielimitowanych użyć"));
        sender.sendMessage(msg.colorize(""));
        sender.sendMessage(msg.colorize("&e/kod usun <nazwa> &7- Usuń kod"));
        sender.sendMessage(msg.colorize("&e/kod lista aktywne &7- Lista aktywnych kodów"));
        sender.sendMessage(msg.colorize("&e/kod lista zuzyte &7- Lista zużytych kodów"));
        sender.sendMessage(msg.colorize("&e/kod lista zuzyte wyczysc &7- Wyczyść zużyte kody"));
        sender.sendMessage(msg.colorize("&e/kod info <nazwa> &7- Szczegóły kodu"));
        sender.sendMessage(msg.colorize("&e/kod przeladuj &7- Przeładuj konfigurację"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {

        if (codeManager == null) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("codes.admin")) {
                completions.add("stworz");
                completions.add("usun");
                completions.add("lista");
                completions.add("info");
                completions.add("przeladuj");
                completions.add("pomoc");
                completions.addAll(codeManager.getCodeNames());
            } else if (sender.hasPermission("codes.player")) {
                completions.add("pomoc");
                completions.add("<nazwa_kodu>");
            }
        }
        else if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("pomoc") && sender.hasPermission("codes.admin")) {
                completions.add("admin");
            }
            else if ((sub.equals("usun") || sub.equals("info")) && sender.hasPermission("codes.admin")) {
                completions.addAll(codeManager.getCodeNames());
            }
            else if (sub.equals("lista") && sender.hasPermission("codes.admin")) {
                completions.add("aktywne");
                completions.add("zuzyte");
            }
            else if (sub.equals("stworz") && sender.hasPermission("codes.admin")) {
                completions.add("<nazwa_kodu>");
            }
        }
        else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String subType = args[1].toLowerCase();

            if (sub.equals("lista") && subType.equals("zuzyte") && sender.hasPermission("codes.admin")) {
                completions.add("wyczysc");
            }
            else if (sub.equals("stworz") && sender.hasPermission("codes.admin")) {
                completions.add("<użycia_globalne>");
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            completions.add("<użycia_gracza>");
        }
        else if (args.length == 5 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            String currentArg = args[4].toLowerCase();

            if (currentArg.isEmpty() || "przedmiot".startsWith(currentArg)) {
                completions.add("przedmiot");
                completions.add("przedmiot:");
            }
            if (currentArg.isEmpty() || "permisja:".startsWith(currentArg)) {
                completions.add("permisja:");
            }
            if (currentArg.isEmpty() || "ranga:".startsWith(currentArg)) {
                completions.add("ranga:");
            }

            if (currentArg.startsWith("przedmiot:")) {
                String partial = currentArg.substring(10).toUpperCase();

                List<String> popularItems = Arrays.asList(
                        "DIAMOND", "DIAMOND_BLOCK", "DIAMOND_SWORD",
                        "IRON_INGOT", "IRON_BLOCK",
                        "GOLD_INGOT", "GOLD_BLOCK",
                        "EMERALD", "EMERALD_BLOCK",
                        "NETHERITE_INGOT", "NETHERITE_BLOCK",
                        "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE",
                        "EXPERIENCE_BOTTLE", "NETHER_STAR",
                        "ELYTRA", "TOTEM_OF_UNDYING"
                );

                for (String item : popularItems) {
                    if (item.startsWith(partial)) {
                        completions.add("przedmiot:" + item);
                        completions.add("przedmiot:" + item + ":64");
                    }
                }

                if (partial.length() >= 2) {
                    Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .filter(m -> !m.name().startsWith("LEGACY_"))
                            .filter(m -> m.name().startsWith(partial))
                            .limit(15)
                            .forEach(m -> {
                                completions.add("przedmiot:" + m.name());
                                completions.add("przedmiot:" + m.name() + ":64");
                            });
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
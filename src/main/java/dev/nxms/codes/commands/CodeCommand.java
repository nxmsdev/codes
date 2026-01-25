package dev.nxms.codes.commands;

import dev.nxms.codes.Codes;
import dev.nxms.codes.managers.CodeManager;
import dev.nxms.codes.managers.MessageManager;
import dev.nxms.codes.models.Code;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

        if (args.length < 7) {
            msg.send(sender, "invalid-usage-create");
            msg.send(sender, "invalid-usage-create-hint");
            return true;
        }

        String codeName = args[1];
        int maxGlobalUses;
        int maxPlayerUses;
        int cooldown;
        boolean broadcast;

        try {
            maxGlobalUses = Integer.parseInt(args[2]);
            maxPlayerUses = Integer.parseInt(args[3]);
            cooldown = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            msg.send(sender, "invalid-number");
            return true;
        }

        if (maxGlobalUses < 0 || maxPlayerUses < 0 || cooldown < 0) {
            msg.send(sender, "invalid-number-negative");
            return true;
        }

        String broadcastArg = args[5].toLowerCase();
        if (broadcastArg.equals("true") || broadcastArg.equals("tak")) {
            broadcast = true;
        } else if (broadcastArg.equals("false") || broadcastArg.equals("nie")) {
            broadcast = false;
        } else {
            msg.send(sender, "invalid-broadcast");
            return true;
        }

        String rewardArg = args[6];
        boolean success;

        if (rewardArg.equalsIgnoreCase("przedmiot")) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "must-be-player");
                return true;
            }

            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem == null || heldItem.getType() == Material.AIR) {
                msg.send(sender, "must-hold-item");
                return true;
            }

            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, cooldown, broadcast, heldItem.clone());
        }
        else if (rewardArg.toLowerCase().startsWith("przedmiot:")) {
            String rewardData = rewardArg.substring(10);
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
                msg.send(sender, "unknown-item", MessageManager.placeholders("item", materialName));
                msg.send(sender, "unknown-item-hint");
                return true;
            }

            if (amount < 1) amount = 1;
            if (amount > 64) amount = 64;

            ItemStack itemReward = new ItemStack(material, amount);
            success = codeManager.createCode(codeName, maxGlobalUses, maxPlayerUses, cooldown, broadcast, itemReward);
        }
        else if (rewardArg.toLowerCase().startsWith("permisja:")) {
            String permission = rewardArg.substring(9);
            if (permission.isEmpty()) {
                msg.send(sender, "invalid-reward-type");
                return true;
            }
            success = codeManager.createCodeWithPermission(codeName, maxGlobalUses, maxPlayerUses, cooldown, broadcast, permission);
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

            success = codeManager.createCodeWithRank(codeName, maxGlobalUses, maxPlayerUses, cooldown, broadcast, rank);
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
            if (cooldown > 0) {
                msg.send(sender, "code-created-cooldown", MessageManager.placeholders("seconds", String.valueOf(cooldown)));
            }
            if (broadcast) {
                msg.send(sender, "code-created-broadcast-on");
            } else {
                msg.send(sender, "code-created-broadcast-off");
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
            msg.sendRaw(sender, "list-usage-header");
            msg.sendRaw(sender, "list-usage-active");
            msg.sendRaw(sender, "list-usage-used");
            msg.sendRaw(sender, "list-usage-clear");
            return true;
        }

        String listType = args[1].toLowerCase();

        switch (listType) {
            case "aktywne":
                return handleListActive(sender);
            case "zuzyte":
                return handleListUsed(sender, args);
            default:
                msg.sendRaw(sender, "list-unknown-type", MessageManager.placeholders("type", listType));
                msg.sendRaw(sender, "list-available-types");
                return true;
        }
    }

    private boolean handleListActive(CommandSender sender) {
        msg.sendRaw(sender, "list-active-header");

        if (codeManager.getAllCodes().isEmpty()) {
            msg.sendRaw(sender, "list-active-empty");
        } else {
            for (Code code : codeManager.getAllCodes()) {
                if (code.isGlobalUnlimited()) {
                    msg.sendRaw(sender, "list-active-entry-unlimited", MessageManager.placeholders(
                            "code", code.getName()
                    ));
                } else {
                    msg.sendRaw(sender, "list-active-entry", MessageManager.placeholders(
                            "code", code.getName(),
                            "uses", code.getRemainingGlobalUsesDisplay()
                    ));
                }
            }
        }

        return true;
    }

    private boolean handleListUsed(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("wyczysc")) {
            if (codeManager.clearUsedCodes()) {
                msg.send(sender, "list-used-cleared");
            } else {
                msg.sendRaw(sender, "list-used-clear-empty");
            }
            return true;
        }

        msg.sendRaw(sender, "list-used-header");

        var usedCodes = codeManager.getUsedCodes();
        if (usedCodes.isEmpty()) {
            msg.sendRaw(sender, "list-used-empty");
        } else {
            for (CodeManager.UsedCodeInfo info : usedCodes) {
                msg.sendRaw(sender, "list-used-entry", MessageManager.placeholders(
                        "code", info.name(),
                        "uses", String.valueOf(info.totalUses()),
                        "reward", info.rewardDisplay()
                ));
            }
            msg.sendRaw(sender, "list-used-clear-hint");
        }

        return true;
    }

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

        msg.sendRaw(sender, "info-cooldown", MessageManager.placeholders(
                "cooldown", code.getCooldownDisplay()
        ));

        msg.sendRaw(sender, "info-broadcast", MessageManager.placeholders(
                "broadcast", code.getBroadcastDisplay()
        ));

        msg.sendRaw(sender, "info-reward-type", MessageManager.placeholders(
                "type", code.getRewardType().name()
        ));
        msg.sendRaw(sender, "info-reward", MessageManager.placeholders(
                "reward", code.getRewardDisplay()
        ));

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

        if (code.isOnCooldown(player.getUniqueId())) {
            int remaining = code.getRemainingCooldown(player.getUniqueId());
            msg.send(sender, "code-cooldown", MessageManager.placeholders(
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
            case SUCCESS_ITEM:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-item");

                if (code.isBroadcast()) {
                    String rewardDisplay = getItemDisplayName(code.getItemReward());
                    msg.broadcast("broadcast-reward-item", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", rewardDisplay
                    ));
                }
                break;

            case SUCCESS_PERMISSION:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-permission",
                        MessageManager.placeholders("reward", code.getPermissionReward()));

                if (code.isBroadcast()) {
                    msg.broadcast("broadcast-reward-permission", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", code.getPermissionReward()
                    ));
                }
                break;

            case SUCCESS_RANK:
                msg.send(player, "code-redeemed", MessageManager.placeholders("code", code.getName()));
                msg.send(player, "reward-received-rank",
                        MessageManager.placeholders("reward", code.getRankReward()));

                if (code.isBroadcast()) {
                    msg.broadcast("broadcast-reward-rank", MessageManager.placeholders(
                            "player", player.getName(),
                            "reward", code.getRankReward()
                    ));
                }
                break;

            case RANK_ERROR:
                msg.send(player, "rank-requires-luckperms");
                break;

            default:
                msg.send(player, "code-not-found", MessageManager.placeholders("code", codeName));
        }

        return true;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "Przedmiot";
        }

        int amount = item.getAmount();

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component displayName = item.getItemMeta().displayName();
            if (displayName != null) {
                String name = PlainTextComponentSerializer.plainText().serialize(displayName);
                return name + " x" + amount;
            }
        }

        String materialName = item.getType().name()
                .toLowerCase()
                .replace("_", " ");

        String[] words = materialName.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return formatted.toString().trim() + " x" + amount;
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
        msg.sendRaw(sender, "help-admin-cooldown-hint");
        msg.sendRaw(sender, "help-admin-broadcast-hint");
        sender.sendMessage("");
        msg.sendRaw(sender, "help-admin-delete");
        msg.sendRaw(sender, "help-admin-list-active");
        msg.sendRaw(sender, "help-admin-list-used");
        msg.sendRaw(sender, "help-admin-list-clear");
        msg.sendRaw(sender, "help-admin-info");
        msg.sendRaw(sender, "help-admin-reload");
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
                completions.add("<użycia_ogólne>");
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            completions.add("<użycia_gracza>");
        }
        else if (args.length == 5 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            completions.add("<opóźnienie_sekundy>");
        }
        else if (args.length == 6 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            completions.add("tak");
            completions.add("nie");
        }
        else if (args.length == 7 && args[0].equalsIgnoreCase("stworz") && sender.hasPermission("codes.admin")) {
            String currentArg = args[6].toLowerCase();

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
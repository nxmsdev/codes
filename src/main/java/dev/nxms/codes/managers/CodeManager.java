package dev.nxms.codes.managers;

import dev.nxms.codes.Codes;
import dev.nxms.codes.models.Code;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CodeManager {

    private final Codes plugin;
    private final Map<String, Code> codes;
    private final Map<String, UsedCodeInfo> usedCodes;
    private File codesFile;
    private FileConfiguration codesConfig;

    public CodeManager(Codes plugin) {
        this.plugin = plugin;
        this.codes = new HashMap<>();
        this.usedCodes = new LinkedHashMap<>();
        loadCodes();
    }

    public void loadCodes() {
        codes.clear();
        usedCodes.clear();
        codesFile = new File(plugin.getDataFolder(), "codes.yml");

        if (!codesFile.exists()) {
            try {
                codesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Cannot create codes.yml file!");
                e.printStackTrace();
                return;
            }
        }

        codesConfig = YamlConfiguration.loadConfiguration(codesFile);

        loadActiveCodes();
        loadUsedCodes();

        plugin.getLogger().info("Załadowano " + codes.size() + " aktywnych kodów i " + usedCodes.size() + " zużytych kodów.");
    }

    private void loadActiveCodes() {
        ConfigurationSection codesSection = codesConfig.getConfigurationSection("codes");
        if (codesSection == null) return;

        for (String codeName : codesSection.getKeys(false)) {
            ConfigurationSection codeSection = codesSection.getConfigurationSection(codeName);
            if (codeSection == null) continue;

            try {
                int globalUses = codeSection.getInt("global-uses", 0);
                int maxGlobalUses = codeSection.getInt("max-global-uses", 100);
                int maxPlayerUses = codeSection.getInt("max-player-uses", 1);
                int cooldown = codeSection.getInt("cooldown", 0);
                boolean broadcast = codeSection.getBoolean("broadcast", true);
                String rewardTypeStr = codeSection.getString("reward-type", "ITEM");
                Code.RewardType rewardType = Code.RewardType.valueOf(rewardTypeStr);

                ItemStack itemReward = null;
                String permissionReward = null;
                String rankReward = null;

                if (rewardType == Code.RewardType.ITEM) {
                    itemReward = codeSection.getItemStack("item-reward");
                } else if (rewardType == Code.RewardType.PERMISSION) {
                    permissionReward = codeSection.getString("permission-reward");
                } else if (rewardType == Code.RewardType.RANK) {
                    rankReward = codeSection.getString("rank-reward");
                }

                Map<UUID, Integer> playerUses = new HashMap<>();
                ConfigurationSection playerUsesSection = codeSection.getConfigurationSection("player-uses");
                if (playerUsesSection != null) {
                    for (String uuidStr : playerUsesSection.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            int uses = playerUsesSection.getInt(uuidStr);
                            playerUses.put(uuid, uses);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                Map<UUID, Long> playerCooldowns = new HashMap<>();
                ConfigurationSection cooldownsSection = codeSection.getConfigurationSection("player-cooldowns");
                if (cooldownsSection != null) {
                    for (String uuidStr : cooldownsSection.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            long lastUse = cooldownsSection.getLong(uuidStr);
                            playerCooldowns.put(uuid, lastUse);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                Code code = new Code(codeName, globalUses, maxGlobalUses, maxPlayerUses, cooldown, broadcast,
                        rewardType, itemReward, permissionReward, rankReward, playerUses, playerCooldowns);
                codes.put(codeName.toLowerCase(), code);

            } catch (Exception e) {
                plugin.getLogger().warning("Cannot load code: " + codeName);
                e.printStackTrace();
            }
        }
    }

    private void loadUsedCodes() {
        ConfigurationSection usedSection = codesConfig.getConfigurationSection("used-codes");
        if (usedSection == null) return;

        for (String codeName : usedSection.getKeys(false)) {
            ConfigurationSection codeSection = usedSection.getConfigurationSection(codeName);
            if (codeSection == null) continue;

            try {
                String rewardDisplay = codeSection.getString("reward", "Unknown");
                int totalUses = codeSection.getInt("total-uses", 0);
                int maxGlobalUses = codeSection.getInt("max-global-uses", 0);
                int maxPlayerUses = codeSection.getInt("max-player-uses", 0);

                Map<UUID, Integer> playerUses = new HashMap<>();
                ConfigurationSection pu = codeSection.getConfigurationSection("player-uses");
                if (pu != null) {
                    for (String uuidStr : pu.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            playerUses.put(uuid, pu.getInt(uuidStr, 0));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                UsedCodeInfo info = new UsedCodeInfo(codeName, rewardDisplay, totalUses, maxGlobalUses, maxPlayerUses, playerUses);
                usedCodes.put(codeName.toLowerCase(), info);

            } catch (Exception e) {
                plugin.getLogger().warning("Cannot load used code: " + codeName);
            }
        }
    }

    public void saveCodes() {
        codesConfig = new YamlConfiguration();

        for (Code code : codes.values()) {
            String path = "codes." + code.getName();

            codesConfig.set(path + ".global-uses", code.getGlobalUses());
            codesConfig.set(path + ".max-global-uses", code.getMaxGlobalUses());
            codesConfig.set(path + ".max-player-uses", code.getMaxPlayerUses());
            codesConfig.set(path + ".cooldown", code.getCooldown());
            codesConfig.set(path + ".broadcast", code.isBroadcast());
            codesConfig.set(path + ".reward-type", code.getRewardType().name());

            if (code.getRewardType() == Code.RewardType.ITEM) {
                codesConfig.set(path + ".item-reward", code.getItemReward());
            } else if (code.getRewardType() == Code.RewardType.PERMISSION) {
                codesConfig.set(path + ".permission-reward", code.getPermissionReward());
            } else if (code.getRewardType() == Code.RewardType.RANK) {
                codesConfig.set(path + ".rank-reward", code.getRankReward());
            }

            for (Map.Entry<UUID, Integer> entry : code.getPlayerUses().entrySet()) {
                codesConfig.set(path + ".player-uses." + entry.getKey().toString(), entry.getValue());
            }

            for (Map.Entry<UUID, Long> entry : code.getPlayerCooldowns().entrySet()) {
                codesConfig.set(path + ".player-cooldowns." + entry.getKey().toString(), entry.getValue());
            }
        }

        for (UsedCodeInfo info : usedCodes.values()) {
            String path = "used-codes." + info.name();
            codesConfig.set(path + ".reward", info.rewardDisplay());
            codesConfig.set(path + ".total-uses", info.totalUses());
            codesConfig.set(path + ".max-global-uses", info.maxGlobalUses());
            codesConfig.set(path + ".max-player-uses", info.maxPlayerUses());

            if (info.playerUses() != null) {
                for (Map.Entry<UUID, Integer> e : info.playerUses().entrySet()) {
                    codesConfig.set(path + ".player-uses." + e.getKey().toString(), e.getValue());
                }
            }
        }

        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save codes.yml file!");
            e.printStackTrace();
        }
    }

    public boolean createCode(String name, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast, ItemStack itemReward) {
        if (codes.containsKey(name.toLowerCase())) {
            return false;
        }

        Code code = new Code(name, maxGlobalUses, maxPlayerUses, cooldown, broadcast, itemReward);
        codes.put(name.toLowerCase(), code);
        saveCodes();
        return true;
    }

    public boolean createCodeWithPermission(String name, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast, String permission) {
        if (codes.containsKey(name.toLowerCase())) {
            return false;
        }

        Code code = new Code(name, maxGlobalUses, maxPlayerUses, cooldown, broadcast, permission, Code.RewardType.PERMISSION);
        codes.put(name.toLowerCase(), code);
        saveCodes();
        return true;
    }

    public boolean createCodeWithRank(String name, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast, String rank) {
        if (codes.containsKey(name.toLowerCase())) {
            return false;
        }

        Code code = new Code(name, maxGlobalUses, maxPlayerUses, cooldown, broadcast, rank, Code.RewardType.RANK);
        codes.put(name.toLowerCase(), code);
        saveCodes();
        return true;
    }

    public boolean deleteCode(String name) {
        Code removed = codes.remove(name.toLowerCase());
        if (removed != null) {
            saveCodes();
            return true;
        }
        return false;
    }

    public Code getCode(String name) {
        return codes.get(name.toLowerCase());
    }

    public Collection<Code> getAllCodes() {
        return codes.values();
    }

    public Set<String> getCodeNames() {
        return codes.keySet();
    }

    public Collection<UsedCodeInfo> getUsedCodes() {
        return usedCodes.values();
    }

    public boolean clearUsedCodes() {
        if (usedCodes.isEmpty()) {
            return false;
        }
        usedCodes.clear();
        saveCodes();
        return true;
    }

    public enum RedeemResult {
        SUCCESS_ITEM,
        SUCCESS_PERMISSION,
        SUCCESS_RANK,
        COOLDOWN,
        NOT_FOUND,
        RANK_ERROR
    }

    public RedeemResult redeemCode(Player player, String codeName) {
        Code code = getCode(codeName);
        if (code == null) {
            return RedeemResult.NOT_FOUND;
        }

        if (code.isOnCooldown(player.getUniqueId())) {
            return RedeemResult.COOLDOWN;
        }

        code.use(player.getUniqueId());

        code.use(player.getUniqueId());

        RedeemResult result = RedeemResult.SUCCESS_ITEM;

        if (code.getRewardType() == Code.RewardType.ITEM) {
            ItemStack reward = code.getItemReward();
            if (reward != null) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            result = RedeemResult.SUCCESS_ITEM;
        } else if (code.getRewardType() == Code.RewardType.PERMISSION) {
            String permission = code.getPermissionReward();
            if (permission != null && !permission.isEmpty()) {
                grantPermission(player, permission);
            }
            result = RedeemResult.SUCCESS_PERMISSION;
        } else if (code.getRewardType() == Code.RewardType.RANK) {
            String rank = code.getRankReward();
            if (rank != null && !rank.isEmpty()) {
                if (!grantRank(player, rank)) {
                    result = RedeemResult.RANK_ERROR;
                } else {
                    result = RedeemResult.SUCCESS_RANK;
                }
            }
        }

        if (!code.isGlobalUnlimited() && !code.canBeUsed()) {
            archiveCode(code);
        }

        saveCodes();
        return result;
    }

    private void archiveCode(Code code) {
        UsedCodeInfo info = new UsedCodeInfo(
                code.getName(),
                code.getRewardDisplay(),
                code.getGlobalUses(),
                code.getMaxGlobalUses(),
                code.getMaxPlayerUses(),
                new HashMap<>(code.getPlayerUses())
        );

        usedCodes.put(code.getName().toLowerCase(), info);
        codes.remove(code.getName().toLowerCase());

        plugin.getLogger().info("Kod '" + code.getName() + "' został zużyty i zarchiwizowany.");
    }

    private void grantPermission(Player player, String permission) {
        if (plugin.isLuckPermsEnabled() && plugin.getLuckPermsHook() != null) {
            if (plugin.getLuckPermsHook().grantPermission(player, permission)) {
                return;
            }
        }

        player.addAttachment(plugin, permission, true);
        plugin.getLogger().info("Nadano permisję " + permission + " graczowi " + player.getName() + " (tymczasowo)");
    }

    private boolean grantRank(Player player, String rank) {
        if (plugin.isLuckPermsEnabled() && plugin.getLuckPermsHook() != null) {
            return plugin.getLuckPermsHook().grantRank(player, rank);
        }
        return false;
    }

    public boolean isLuckPermsAvailable() {
        return plugin.isLuckPermsEnabled() && plugin.getLuckPermsHook() != null;
    }

    public boolean rankExists(String rankName) {
        if (plugin.getLuckPermsHook() != null) {
            return plugin.getLuckPermsHook().rankExists(rankName);
        }
        return false;
    }

    public record UsedCodeInfo(
            String name,
            String rewardDisplay,
            int totalUses,
            int maxGlobalUses,
            int maxPlayerUses,
            Map<UUID, Integer> playerUses
    ) { }
}
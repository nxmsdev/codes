package dev.nxms.codes.models;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Code {

    public enum RewardType {
        ITEM,
        PERMISSION,
        RANK
    }

    public static final int UNLIMITED = 0;

    private final String name;
    private int globalUses;
    private final int maxGlobalUses;
    private final int maxPlayerUses;
    private final int cooldown;
    private final boolean broadcast;
    private final RewardType rewardType;
    private final ItemStack itemReward;
    private final String permissionReward;
    private final String rankReward;
    private final Map<UUID, Integer> playerUses;
    private final Map<UUID, Long> playerCooldowns;

    // Konstruktor dla ITEM
    public Code(String name, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast, ItemStack itemReward) {
        this.name = name;
        this.globalUses = 0;
        this.maxGlobalUses = maxGlobalUses;
        this.maxPlayerUses = maxPlayerUses;
        this.cooldown = cooldown;
        this.broadcast = broadcast;
        this.rewardType = RewardType.ITEM;
        this.itemReward = itemReward.clone();
        this.permissionReward = null;
        this.rankReward = null;
        this.playerUses = new HashMap<>();
        this.playerCooldowns = new HashMap<>();
    }

    // Konstruktor dla PERMISSION lub RANK
    public Code(String name, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast, String reward, RewardType type) {
        this.name = name;
        this.globalUses = 0;
        this.maxGlobalUses = maxGlobalUses;
        this.maxPlayerUses = maxPlayerUses;
        this.cooldown = cooldown;
        this.broadcast = broadcast;
        this.rewardType = type;
        this.itemReward = null;

        if (type == RewardType.PERMISSION) {
            this.permissionReward = reward;
            this.rankReward = null;
        } else if (type == RewardType.RANK) {
            this.permissionReward = null;
            this.rankReward = reward;
        } else {
            this.permissionReward = null;
            this.rankReward = null;
        }

        this.playerUses = new HashMap<>();
        this.playerCooldowns = new HashMap<>();
    }

    // Konstruktor do ładowania z pliku
    public Code(String name, int globalUses, int maxGlobalUses, int maxPlayerUses, int cooldown, boolean broadcast,
                RewardType rewardType, ItemStack itemReward, String permissionReward,
                String rankReward, Map<UUID, Integer> playerUses, Map<UUID, Long> playerCooldowns) {
        this.name = name;
        this.globalUses = globalUses;
        this.maxGlobalUses = maxGlobalUses;
        this.maxPlayerUses = maxPlayerUses;
        this.cooldown = cooldown;
        this.broadcast = broadcast;
        this.rewardType = rewardType;
        this.itemReward = itemReward;
        this.permissionReward = permissionReward;
        this.rankReward = rankReward;
        this.playerUses = new HashMap<>(playerUses);
        this.playerCooldowns = new HashMap<>(playerCooldowns);
    }

    public String getName() {
        return name;
    }

    public int getGlobalUses() {
        return globalUses;
    }

    public int getMaxGlobalUses() {
        return maxGlobalUses;
    }

    public int getMaxPlayerUses() {
        return maxPlayerUses;
    }

    public int getCooldown() {
        return cooldown;
    }

    public boolean hasCooldown() {
        return cooldown > 0;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public boolean isGlobalUnlimited() {
        return maxGlobalUses == UNLIMITED;
    }

    public boolean isPlayerUnlimited() {
        return maxPlayerUses == UNLIMITED;
    }

    public int getRemainingGlobalUses() {
        if (isGlobalUnlimited()) {
            return -1;
        }
        return maxGlobalUses - globalUses;
    }

    public int getRemainingPlayerUses(UUID playerUuid) {
        if (isPlayerUnlimited()) {
            return -1;
        }
        return maxPlayerUses - getPlayerUseCount(playerUuid);
    }

    public RewardType getRewardType() {
        return rewardType;
    }

    public ItemStack getItemReward() {
        return itemReward != null ? itemReward.clone() : null;
    }

    public String getPermissionReward() {
        return permissionReward;
    }

    public String getRankReward() {
        return rankReward;
    }

    public Map<UUID, Integer> getPlayerUses() {
        return new HashMap<>(playerUses);
    }

    public Map<UUID, Long> getPlayerCooldowns() {
        return new HashMap<>(playerCooldowns);
    }

    public int getPlayerUseCount(UUID playerUuid) {
        return playerUses.getOrDefault(playerUuid, 0);
    }

    public boolean canBeUsed() {
        if (isGlobalUnlimited()) {
            return true;
        }
        return globalUses < maxGlobalUses;
    }

    public boolean canPlayerUse(UUID playerUuid) {
        if (isPlayerUnlimited()) {
            return true;
        }
        return getPlayerUseCount(playerUuid) < maxPlayerUses;
    }

    public int getRemainingCooldown(UUID playerUuid) {
        if (!hasCooldown()) {
            return 0;
        }

        Long lastUse = playerCooldowns.get(playerUuid);
        if (lastUse == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastUse) / 1000;
        int remaining = (int) (cooldown - elapsedSeconds);

        return Math.max(0, remaining);
    }

    public boolean isOnCooldown(UUID playerUuid) {
        return getRemainingCooldown(playerUuid) > 0;
    }

    public void use(UUID playerUuid) {
        globalUses++;
        playerUses.merge(playerUuid, 1, Integer::sum);

        if (hasCooldown()) {
            playerCooldowns.put(playerUuid, System.currentTimeMillis());
        }
    }

    public String getRewardDisplay() {
        switch (rewardType) {
            case PERMISSION:
                return "Permisja: " + permissionReward;
            case RANK:
                return "Ranga: " + rankReward;
            case ITEM:
                if (itemReward != null) {
                    return "Przedmiot: " + itemReward.getType().name() + " x" + itemReward.getAmount();
                }
            default:
                return "Nieznana nagroda";
        }
    }

    public String getGlobalUsesDisplay() {
        if (isGlobalUnlimited()) {
            return globalUses + "/∞";
        }
        return globalUses + "/" + maxGlobalUses;
    }

    public String getPlayerUsesDisplay() {
        if (isPlayerUnlimited()) {
            return "∞";
        }
        return String.valueOf(maxPlayerUses);
    }

    public String getRemainingGlobalUsesDisplay() {
        if (isGlobalUnlimited()) {
            return "∞";
        }
        return String.valueOf(getRemainingGlobalUses());
    }

    public String getCooldownDisplay() {
        if (!hasCooldown()) {
            return "Brak";
        }
        return cooldown + "s";
    }

    public String getBroadcastDisplay() {
        return broadcast ? "Tak" : "Nie";
    }
}
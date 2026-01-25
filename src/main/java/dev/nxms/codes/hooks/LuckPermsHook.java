package dev.nxms.codes.hooks;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

public class LuckPermsHook {

    private final Logger logger;
    private LuckPerms luckPerms;

    public LuckPermsHook(Logger logger) {
        this.logger = logger;
        try {
            this.luckPerms = LuckPermsProvider.get();
        } catch (Exception e) {
            this.luckPerms = null;
            logger.warning("Nie można połączyć z LuckPerms: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return luckPerms != null;
    }

    public boolean grantPermission(Player player, String permission) {
        if (luckPerms == null) {
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                user.data().add(Node.builder(permission).build());
                luckPerms.getUserManager().saveUser(user);
                logger.info("Nadano permisję " + permission + " graczowi " + player.getName() + " przez LuckPerms");
                return true;
            }
        } catch (Exception e) {
            logger.warning("Błąd przy nadawaniu permisji przez LuckPerms: " + e.getMessage());
        }

        return false;
    }

    public boolean grantRank(Player player, String rankName) {
        if (luckPerms == null) {
            return false;
        }

        try {
            // Sprawdź czy ranga istnieje
            Group group = luckPerms.getGroupManager().getGroup(rankName);
            if (group == null) {
                logger.warning("Ranga '" + rankName + "' nie istnieje w LuckPerms!");
                return false;
            }

            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                // Dodaj gracza do grupy
                InheritanceNode node = InheritanceNode.builder(group).build();
                user.data().add(node);
                luckPerms.getUserManager().saveUser(user);
                logger.info("Nadano rangę " + rankName + " graczowi " + player.getName() + " przez LuckPerms");
                return true;
            }
        } catch (Exception e) {
            logger.warning("Błąd przy nadawaniu rangi przez LuckPerms: " + e.getMessage());
        }

        return false;
    }

    public boolean rankExists(String rankName) {
        if (luckPerms == null) {
            return false;
        }

        try {
            Group group = luckPerms.getGroupManager().getGroup(rankName);
            return group != null;
        } catch (Exception e) {
            return false;
        }
    }
}
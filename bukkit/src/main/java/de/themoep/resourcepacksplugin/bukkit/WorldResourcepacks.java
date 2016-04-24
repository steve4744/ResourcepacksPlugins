package de.themoep.resourcepacksplugin.bukkit;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.themoep.resourcepacksplugin.bukkit.events.ResourcePackSelectEvent;
import de.themoep.resourcepacksplugin.bukkit.events.ResourcePackSendEvent;
import de.themoep.resourcepacksplugin.bukkit.listeners.AuthmeLoginListener;
import de.themoep.resourcepacksplugin.bukkit.listeners.DisconnectListener;
import de.themoep.resourcepacksplugin.bukkit.listeners.ProxyPackListener;
import de.themoep.resourcepacksplugin.bukkit.listeners.WorldSwitchListener;
import de.themoep.resourcepacksplugin.core.PackManager;
import de.themoep.resourcepacksplugin.core.ResourcePack;
import de.themoep.resourcepacksplugin.core.ResourcepacksPlayer;
import de.themoep.resourcepacksplugin.core.ResourcepacksPlugin;
import de.themoep.resourcepacksplugin.core.events.IResourcePackSelectEvent;
import fr.xephi.authme.api.NewAPI;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import us.myles.ViaVersion.api.ViaVersionAPI;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Created by Phoenix616 on 18.03.2015.
 */
public class WorldResourcepacks extends JavaPlugin implements ResourcepacksPlugin {

    private PackManager pm;

    public Level loglevel;

    private int serverPackFormat = Integer.MAX_VALUE;

    private ViaVersionAPI viaVersion;
    private NewAPI authmeApi;

    public void onEnable() {
        if(loadConfig()) {
            getServer().getPluginManager().registerEvents(new DisconnectListener(this), this);
            getServer().getPluginManager().registerEvents(new WorldSwitchListener(this), this);

            getServer().getMessenger().registerOutgoingPluginChannel(this, "Resourcepack");
            getServer().getMessenger().registerIncomingPluginChannel(this, "Resourcepack", new ProxyPackListener(this));

            getCommand(getName().toLowerCase().charAt(0) + "rp").setExecutor(new WorldResourcepacksCommand(this));
            getCommand("usepack").setExecutor(new UsePackCommand(this));

            try {
                int serverVersion = Integer.valueOf(getServer().getVersion().split(".")[1]);
                if(serverVersion < 8) {
                    serverPackFormat = 0;
                } else if(serverVersion < 9) {
                    serverPackFormat = 1;
                } else {
                    serverPackFormat = 2;
                }
                getLogger().log(Level.INFO, "Detected server packformat " + serverPackFormat + "!");
            } catch(NumberFormatException e) {
                getLogger().log(Level.WARNING, "Could not get version of the server! (" + getServer().getVersion() + "/" + getServer().getVersion().split(".")[1] + ")");
            }

            viaVersion = (ViaVersionAPI) getServer().getPluginManager().getPlugin("ViaVersion");
            if(viaVersion != null) {
                getLogger().log(Level.INFO, "Detected ViaVersion " + viaVersion.getVersion());
            }

            if(getConfig().getBoolean("useauthme", true) && getServer().getPluginManager().getPlugin("AuthMe") != null) {
                authmeApi = NewAPI.getInstance();
                getLogger().log(Level.INFO, "Detected AuthMe " + getServer().getPluginManager().getPlugin("AuthMe").getDescription().getVersion());
                getServer().getPluginManager().registerEvents(new AuthmeLoginListener(this), this);
            }
        } else {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public boolean loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        getLogger().log(Level.INFO, "Loading config!");
        if(getConfig().getString("debug","true").equalsIgnoreCase("true")) {
            loglevel = Level.INFO;
        } else {
            loglevel = Level.FINE;
        }
        getLogger().log(Level.INFO, "Debug level: " + getLogLevel().getName());

        pm = new PackManager(this);
        ConfigurationSection packs = getConfig().getConfigurationSection("packs");
        getLogger().log(getLogLevel(), "Loading packs:");
        for(String s : packs.getKeys(false)) {
            ConfigurationSection packSection = packs.getConfigurationSection(s);

            String packName = s.toLowerCase();
            String packUrl = packSection.getString("url", "");
            if(packUrl.isEmpty()) {
                getLogger().log(Level.SEVERE, "Pack " + packName + " does not have an url defined!");
                continue;
            }
            String packHash =  packSection.getString("hash", "");
            int packFormat = packSection.getInt("format", 0);
            boolean packRestricted = packSection.getBoolean("restricted", false);
            String packPerm = packSection.getString("permission", getName().toLowerCase() + ".pack." + packName);

            ResourcePack pack = new ResourcePack(packName, packUrl, packHash, packFormat, packRestricted, packPerm);

            getPackManager().addPack(pack);

            if(getServer().getPluginManager().getPermission(packPerm) == null) {
                Permission perm = new Permission(packPerm);
                perm.setDefault(PermissionDefault.OP);
                perm.setDescription("Permission for access to the resourcepack " + pack.getName() + " via the usepack command.");
                getServer().getPluginManager().addPermission(perm);
            }
            getLogger().log(getLogLevel(), pack.getName() + " - " + pack.getUrl() + " - " + pack.getHash());
        }

        String emptypackname = getConfig().getString("empty", null);
        if(emptypackname != null && !emptypackname.isEmpty()) {
            ResourcePack ep = getPackManager().getByName(emptypackname);
            if(ep != null) {
                getLogger().log(getLogLevel(), "Empty pack: " + ep.getName());
                getPackManager().setEmptyPack(ep);
            } else {
                getLogger().warning("Cannot set empty resourcepack as there is no pack with the name " + emptypackname + " defined!");
            }
        }

        String globalpackname = getConfig().getString("server.pack", "");
        if(!globalpackname.isEmpty()) {
            ResourcePack gp = getPackManager().getByName(globalpackname);
            if(gp != null) {
                getLogger().log(getLogLevel(), "Server pack: " + gp.getName() + "!");
                getPackManager().setGlobalPack(gp);
            } else {
                getLogger().warning("Cannot set server resourcepack as there is no pack with the name " + globalpackname + " defined!");
            }
        }
        List<String> globalsecondary = getConfig().getStringList("server.secondary");
        if(globalsecondary != null && globalsecondary.size() > 0) {
            getLogger().log(getLogLevel(), "Server secondary packs:");
            for(String secondarypack : globalsecondary) {
                ResourcePack sp = getPackManager().getByName(secondarypack);
                if (sp != null) {
                    getPackManager().addGlobalSecondary(sp);
                    getLogger().log(getLogLevel(), sp.getName());
                } else {
                    getLogger().warning("Cannot add resourcepack as a server secondary pack as there is no pack with the name " + secondarypack + " defined!");
                }
            }
        }

        ConfigurationSection servers = getConfig().getConfigurationSection("worlds");
        for(String s : servers.getKeys(false)) {
            getLogger().log(getLogLevel(), "Loading settings for world " + s + "!");
            String packname = servers.getString(s + ".pack", "");
            if(!packname.isEmpty()) {
                ResourcePack sp = getPackManager().getByName(packname);
                if(sp != null) {
                    getPackManager().addServer(s, sp);
                    getLogger().log(getLogLevel(), "Pack: " + sp.getName() + "!");
                } else {
                    getLogger().warning("Cannot set resourcepack for " + s + " as there is no pack with the name " + packname + " defined!");
                }
            }  else {
                getLogger().log(getLogLevel(), "No pack setting for " + s + "!");
            }
            List<String> serversecondary = servers.getStringList(s + ".secondary");
            if(serversecondary != null && serversecondary.size() > 0) {
                getLogger().log(getLogLevel(), "Secondary packs:");
                for(String secondarypack : serversecondary) {
                    ResourcePack sp = getPackManager().getByName(secondarypack);
                    if (sp != null) {
                        getPackManager().addServerSecondary(s, sp);
                        getLogger().log(getLogLevel(), sp.getName());
                    } else {
                        getLogger().warning("Cannot add resourcepack as a secondary pack for world " + s + " as there is no pack with the name " + secondarypack + " defined!");
                    }
                }
            }
        }
        return true;
    }

    /**
     * Reloads the configuration from the file and
     * resends the resource pack to all online players
     */
    public void reloadConfig(boolean resend) {
        loadConfig();
        getLogger().log(Level.INFO, "Reloaded config.");
        if(isEnabled() && resend) {
            getLogger().log(Level.INFO, "Resending packs for all online players!");
            for(Player p : getServer().getOnlinePlayers()) {
                resendPack(p);
            }
        }
    }

    public void resendPack(java.util.UUID playerId) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            resendPack(player);
        }
    }

    /**
     * Resends the pack that corresponds to the player's world
     * @param player The player to set the pack for
     */
    public void resendPack(Player player) {
        String worldName = "";
        if(player.getWorld() != null) {
            worldName = player.getWorld().getName();
        }
        getPackManager().applyPack(player.getUniqueId(), worldName);
    }

    public void setPack(java.util.UUID playerId, ResourcePack pack) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            setPack(player, pack);
        }
    }

    /**
     * Set the resoucepack of a connected player
     * @param player The ProxiedPlayer to set the pack for
     * @param pack The resourcepack to set for the player
     */
    protected void setPack(Player player, ResourcePack pack) {
        ResourcePackSendEvent sendEvent = new ResourcePackSendEvent(player.getUniqueId(), pack);
        getServer().getPluginManager().callEvent(sendEvent);
        if(sendEvent.isCancelled() || sendEvent.getPack() == null) {
            getLogger().log(loglevel, "Pack send event for " + player.getName() + " was cancelled!");
            return;
        }
        pack = sendEvent.getPack();
        player.setResourcePack(pack.getUrl());
        getPackManager().setUserPack(player.getUniqueId(), pack);
        getLogger().log(loglevel, "Send pack " + pack.getName() + " (" + pack.getUrl() + ") to " + player.getName());
    }

    public void clearPack(java.util.UUID playerId) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            clearPack(player);
        }
    }

    public void clearPack(Player player) {
        getPackManager().clearUserPack(player.getUniqueId());
    }

    public PackManager getPackManager() {
        return pm;
    }

    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key, getConfig().getDefaults().getString("messages." + key));
        if(msg.isEmpty()) {
            msg = "&cUnknown message key: &6messages." + key;
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String msg = getMessage(key);
        if (replacements != null) {
            for(Map.Entry<String, String> repl : replacements.entrySet()) {
                msg = msg.replace("%" + repl.getKey() + "%", repl.getValue());
            }
        }
        return msg;
    }

    public String getVersion() {
        return getDescription().getVersion();
    }

    public Level getLogLevel() {
        return loglevel;
    }

    @Override
    public ResourcepacksPlayer getPlayer(UUID playerId) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            return new ResourcepacksPlayer(player.getName(), player.getUniqueId());
        }
        return null;
    }

    @Override
    public ResourcepacksPlayer getPlayer(String playerName) {
        Player player = getServer().getPlayer(playerName);
        if(player != null) {
            return new ResourcepacksPlayer(player.getName(), player.getUniqueId());
        }
        return null;
    }

    @Override
    public boolean sendMessage(ResourcepacksPlayer packPlayer, String message) {
        if(packPlayer != null) {
            Player player = getServer().getPlayer(packPlayer.getUniqueId());
            if(player != null) {
                player.sendMessage(message);
                return true;
            }
        } else {
            getServer().getConsoleSender().sendMessage(message);
        }
        return false;
    }



    @Override
    public boolean checkPermission(ResourcepacksPlayer player, String perm) {
        // Console
        if(player == null)
            return true;
        return checkPermission(player.getUniqueId(), perm);

    }

    @Override
    public boolean checkPermission(UUID playerId, String perm) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            return player.hasPermission(perm);
        }
        return false;

    }

    @Override
    public int getPlayerPackFormat(UUID playerId) {
        Player player = getServer().getPlayer(playerId);
        if(player != null) {
            if(viaVersion != null) {
                int version = viaVersion.getPlayerVersion(playerId);
                if(version < 47) { // pre 1.8
                    return 0;
                } else if(version < 107) { // pre 1.9
                    return 1;
                } else { // current
                    return 2;
                }
            }
            return serverPackFormat;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public IResourcePackSelectEvent callPackSelectEvent(UUID playerId, ResourcePack pack, IResourcePackSelectEvent.Status status) {
        ResourcePackSelectEvent selectEvent = new ResourcePackSelectEvent(playerId, pack, status);
        getServer().getPluginManager().callEvent(selectEvent);
        return selectEvent;
    }

    @Override
    public boolean isAuthenticated(UUID playerId) {
        if(authmeApi == null)
            return true;
        Player player = getServer().getPlayer(playerId);
        return player != null && authmeApi.isAuthenticated(player);
    }
}
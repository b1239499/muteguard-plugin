package com.fkc.muteguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Intercepts /msg, /reply and similar private-message commands at the
 * command-preprocess stage (before any other plugin, including a separate
 * Msg plugin, gets to handle them) and blocks them if the sender is
 * currently mute-blocked from public chat.
 * <p>
 * Deliberately does NOT depend on CMI's API (or any specific mute plugin's
 * API). Instead it fires a synthetic, non-broadcast AsyncPlayerChatEvent
 * and checks whether anything cancels it — this is the exact same "ask the
 * server generically" trick used for the land-protection check in the
 * CatchMe plugin. Whatever plugin is actually enforcing mute (CMI today,
 * possibly something else later) almost certainly hooks the standard chat
 * event to block public chat, so this keeps working even if the mute
 * plugin changes.
 */
public class MuteGuardListener implements Listener {

    private final Plugin plugin;

    public MuteGuardListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        String label = extractLabel(event.getMessage());
        if (label == null || !isGuarded(label)) {
            return;
        }

        if (player.hasPermission("muteguard.bypass")) {
            return;
        }

        if (!isCurrentlyMuted(player)) {
            return;
        }

        event.setCancelled(true);

        String message = plugin.getConfig().getString("mute-message",
                "&c你目前被禁言中，無法傳送密語。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * "/msg SomePlayer hello there" -> "msg"
     * Returns null if the message isn't actually a command (shouldn't
     * happen for this event, but defensive coding costs nothing).
     */
    private String extractLabel(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty() || rawMessage.charAt(0) != '/') {
            return null;
        }
        String withoutSlash = rawMessage.substring(1);
        int spaceIndex = withoutSlash.indexOf(' ');
        String label = spaceIndex == -1 ? withoutSlash : withoutSlash.substring(0, spaceIndex);
        // Strip a plugin-name prefix if present, e.g. "msg:msg" -> "msg"
        int colonIndex = label.indexOf(':');
        if (colonIndex != -1) {
            label = label.substring(colonIndex + 1);
        }
        return label.toLowerCase(Locale.ROOT);
    }

    private boolean isGuarded(String label) {
        for (String guarded : plugin.getConfig().getStringList("guarded-commands")) {
            if (guarded.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCurrentlyMuted(Player player) {
        Set<Player> noRecipients = new HashSet<>();
        // async=false so this is safe to fire synchronously from the main /
        // region thread instead of requiring an actual background thread.
        AsyncPlayerChatEvent probe = new AsyncPlayerChatEvent(false, player, "", noRecipients);
        Bukkit.getPluginManager().callEvent(probe);
        return probe.isCancelled();
    }
}

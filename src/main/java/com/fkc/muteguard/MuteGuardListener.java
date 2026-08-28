package com.fkc.muteguard;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Intercepts /msg, /reply and similar private-message commands at the
 * command-preprocess stage (before any other plugin, including a separate
 * Msg plugin, gets to handle them) and blocks them if the sender is
 * currently tracked as muted by MuteTracker.
 * <p>
 * IMPORTANT: this class used to detect "is this player muted" by firing a
 * synthetic AsyncPlayerChatEvent with blank content and checking whether
 * anything cancelled it. That was removed — it turned out an external
 * Discord-relay bot was blindly listening for chat events and re-posting
 * them, so every /msg attempt produced a fake, blank chat message that
 * leaked into a Discord channel. Mute state is now tracked entirely
 * in-house by MuteTracker/MuteStateListener instead, so no synthetic event
 * that could be mistaken for real activity is ever created.
 */
public class MuteGuardListener implements Listener {

    private final Plugin plugin;
    private final MuteTracker tracker;

    public MuteGuardListener(Plugin plugin, MuteTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
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

        if (!tracker.isMuted(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        String message = plugin.getConfig().getString("mute-message",
                "&c你目前被禁言中，無法傳送密語。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * "/msg SomePlayer hello there" -> "msg"
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
}

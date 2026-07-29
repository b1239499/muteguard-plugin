package com.fkc.muteguard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Watches for "/cmi mute <player> ..." and "/cmi unmute <player>" (or the
 * short "/mute", "/unmute" aliases some setups use) being run by a player
 * OR from the console, and mirrors that state into MuteTracker.
 * <p>
 * This is a best-effort mirror, not a perfect one: it doesn't know about
 * mute durations, so if a timed mute expires naturally on CMI's side,
 * MuteGuard won't automatically notice — an admin running /cmi unmute (or
 * a server restart, which clears MuteGuard's in-memory state) will clear
 * it. That trade-off is intentional: it keeps this plugin simple and,
 * more importantly, means it never has to ask any other system "is this
 * player muted right now" in a way that could leak into somewhere it
 * shouldn't.
 */
public class MuteStateListener implements Listener {

    private final MuteTracker tracker;

    public MuteStateListener(MuteTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handle(event.getMessage(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        handle("/" + event.getCommand(), null);
    }

    private void handle(String rawMessage, Player fallbackSenderIfArgMissing) {
        if (rawMessage == null || rawMessage.isEmpty() || rawMessage.charAt(0) != '/') {
            return;
        }
        String[] tokens = rawMessage.substring(1).trim().split("\\s+");
        if (tokens.length == 0) {
            return;
        }

        int actionIndex = 0;
        // Support both "/cmi mute Foo" and a bare "/mute Foo" alias.
        if (tokens[0].equalsIgnoreCase("cmi") && tokens.length > 1) {
            actionIndex = 1;
        }
        if (tokens.length <= actionIndex) {
            return;
        }

        String action = tokens[actionIndex].toLowerCase(Locale.ROOT);
        boolean isMute = action.equals("mute");
        boolean isUnmute = action.equals("unmute");
        if (!isMute && !isUnmute) {
            return;
        }

        String targetName = tokens.length > actionIndex + 1 ? tokens[actionIndex + 1] : null;
        if (targetName == null) {
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Target isn't online (or name typo'd) — nothing for us to track.
            return;
        }

        UUID targetId = target.getUniqueId();
        if (isMute) {
            tracker.markMuted(targetId);
        } else {
            tracker.markUnmuted(targetId);
        }
    }
}

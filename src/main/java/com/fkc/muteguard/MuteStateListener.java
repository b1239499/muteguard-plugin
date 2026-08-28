package com.fkc.muteguard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches for "/cmi mute <player> ..." and "/cmi unmute <player>" (or the
 * short "/mute", "/unmute" aliases some setups use) being run by a player
 * OR from the console, and mirrors that state into MuteTracker.
 * <p>
 * A timed mute's duration is parsed straight out of the command itself
 * (matching CMI's own documented "(time)" argument and its "defaults to
 * 1 hour if not given" behavior) and a matching local auto-clear is
 * scheduled — this deliberately does NOT rely on asking CMI's own API
 * whether a player is still muted (no confirmed, documented public method
 * for that was found), so instead MuteGuard keeps its own independent
 * timer that mirrors what CMI is expected to be doing internally. This
 * isn't a perfect mirror of CMI's exact internal clock, but it means a
 * naturally-expiring timed mute doesn't leave the player stuck being
 * blocked forever, which was the actual reported problem.
 */
public class MuteStateListener implements Listener {

    private final MuteTracker tracker;
    private final Plugin plugin;

    // Matches CMI-style durations like "1h", "30m", "3d", "45s" — a number
    // followed by a single unit letter. CMI's own docs describe the (time)
    // argument this way; combined durations like "1h30m" aren't attempted
    // here and just fall back to the 1-hour default below, same as CMI's
    // own documented default for an unparseable/missing time argument.
    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);
    private static final long DEFAULT_DURATION_TICKS = 20L * 60 * 60; // CMI's own documented default: 1 hour

    public MuteStateListener(MuteTracker tracker, Plugin plugin) {
        this.tracker = tracker;
        this.plugin = plugin;
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
        if (isUnmute) {
            tracker.markUnmuted(targetId);
            return;
        }

        // isMute
        int generation = tracker.markMuted(targetId);

        // CMI's syntax: /cmi mute [playerName] (time) (reason) — the time
        // argument, if present, is the very next token after the name.
        String timeArg = tokens.length > actionIndex + 2 ? tokens[actionIndex + 2] : null;
        long durationTicks = parseDurationTicks(timeArg);

        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                task -> tracker.markUnmutedIfCurrent(targetId, generation),
                durationTicks);
    }

    /** Parses a CMI-style duration ("1h", "30m", "3d", "45s") into ticks, defaulting to 1 hour (CMI's own documented default) if missing or unparseable. */
    private static long parseDurationTicks(String raw) {
        if (raw == null) {
            return DEFAULT_DURATION_TICKS;
        }
        Matcher m = DURATION.matcher(raw);
        if (!m.matches()) {
            return DEFAULT_DURATION_TICKS;
        }
        long amount = Long.parseLong(m.group(1));
        char unit = Character.toLowerCase(m.group(2).charAt(0));
        long seconds = switch (unit) {
            case 's' -> amount;
            case 'm' -> amount * 60;
            case 'h' -> amount * 60 * 60;
            case 'd' -> amount * 60 * 60 * 24;
            default -> 60 * 60; // unreachable given the regex, but keeps the compiler happy
        };
        return seconds * 20L; // 20 ticks per second
    }
}

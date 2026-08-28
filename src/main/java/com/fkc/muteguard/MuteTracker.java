package com.fkc.muteguard;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently mute-blocked, purely by watching for
 * admins running the mute/unmute command themselves (see MuteStateListener)
 * — never by asking any other plugin/event whether a player "is muted": an
 * earlier version tried that by firing a synthetic chat event as a probe,
 * which turned out to have a nasty side effect: any other plugin (e.g. a
 * Discord relay bot) that blindly listens for chat events and rebroadcasts
 * them could pick up that fake, blank-content event and post it somewhere
 * it should never have gone. This version never creates any event that
 * could be mistaken for real player activity.
 * <p>
 * A timed mute's duration is parsed from the /cmi mute command itself (see
 * MuteStateListener) and a matching local auto-clear is scheduled, so a
 * naturally-expiring timed mute doesn't leave the player stuck unable to
 * send private messages forever. Each mute gets a "generation" number —
 * if the player is muted again before the first mute's scheduled auto-
 * clear fires, that stale, earlier-scheduled clear must NOT be allowed to
 * prematurely end the newer mute period (the exact same class of bug as a
 * leftover timer from an earlier state firing late and clobbering
 * something newer).
 */
public class MuteTracker {

    private final Set<UUID> mutedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Integer> generation = new ConcurrentHashMap<>();

    /** @return the new generation number for this mute — pass this to scheduleAutoClear/markUnmutedIfCurrent so a later re-mute isn't clobbered by a stale scheduled clear. */
    public synchronized int markMuted(UUID playerId) {
        mutedPlayers.add(playerId);
        int gen = generation.merge(playerId, 1, Integer::sum);
        return gen;
    }

    public synchronized void markUnmuted(UUID playerId) {
        mutedPlayers.remove(playerId);
        generation.merge(playerId, 1, Integer::sum); // invalidate any pending scheduled auto-clear for the mute this un-mute is clearing
    }

    /** Only clears if no newer mute/unmute has happened since generation `expectedGeneration` was issued — see class javadoc. */
    public synchronized void markUnmutedIfCurrent(UUID playerId, int expectedGeneration) {
        Integer current = generation.get(playerId);
        if (current != null && current == expectedGeneration) {
            mutedPlayers.remove(playerId);
        }
    }

    public boolean isMuted(UUID playerId) {
        return mutedPlayers.contains(playerId);
    }
}

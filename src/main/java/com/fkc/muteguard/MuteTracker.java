package com.fkc.muteguard;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently mute-blocked, purely by watching for
 * admins running the mute/unmute command themselves. This is intentionally
 * NOT based on asking any other plugin/event whether a player "is muted" —
 * an earlier version tried that by firing a synthetic chat event as a
 * probe, which turned out to have a nasty side effect: any other plugin
 * (e.g. a Discord relay bot) that blindly listens for chat events and
 * rebroadcasts them could pick up that fake, blank-content event and post
 * it somewhere it should never have gone. This version never creates any
 * event that could be mistaken for real player activity.
 */
public class MuteTracker {

    private final Set<UUID> mutedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void markMuted(UUID playerId) {
        mutedPlayers.add(playerId);
    }

    public void markUnmuted(UUID playerId) {
        mutedPlayers.remove(playerId);
    }

    public boolean isMuted(UUID playerId) {
        return mutedPlayers.contains(playerId);
    }
}

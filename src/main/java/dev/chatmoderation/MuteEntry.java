package dev.chatmoderation;

import dev.chatmoderation.util.DurationUtil;

import java.util.UUID;

/** An active mute. {@code expiresAt} is an epoch-millis timestamp, or -1 for a permanent mute. */
public record MuteEntry(UUID uuid, String name, String reason, String staff, long createdAt, long expiresAt) {

    public boolean isPermanent() {
        return expiresAt == DurationUtil.PERMANENT;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && now >= expiresAt;
    }

    public long remaining(long now) {
        return isPermanent() ? -1 : Math.max(0, expiresAt - now);
    }
}

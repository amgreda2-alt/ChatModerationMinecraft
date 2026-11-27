package dev.chatmoderation.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

/** Per-player chat history used for spam detection. Thread-safe (chat is async). */
public final class PlayerChatData {

    public enum Type { OK, COOLDOWN, RATE, REPEAT, FLOOD, CAPS }

    /** Result of a spam check. */
    public record Verdict(Type type, long remainingMs) {
        static final Verdict OK = new Verdict(Type.OK, 0);

        public boolean isSpam() {
            return type == Type.RATE || type == Type.REPEAT || type == Type.FLOOD;
        }
    }

    /** Thresholds, decoupled from Bukkit configuration so they can be unit tested. */
    public record Limits(
            boolean cooldownEnabled, long cooldownMs,
            boolean rateEnabled, int rateMax, long rateWindowMs,
            boolean repeatEnabled, long repeatWindowMs, int repeatHistory,
            boolean capsEnabled, int capsMinLength, int capsMaxPercent,
            boolean floodEnabled, Pattern floodPattern) {}

    private record Entry(String text, long time) {}

    private final Deque<Long> recentTimes = new ArrayDeque<>();
    private final Deque<Entry> history = new ArrayDeque<>();
    private final Deque<Long> violations = new ArrayDeque<>();
    private long lastMessage = 0;

    public static String normalizeForCompare(String msg) {
        return msg.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    public synchronized Verdict check(String message, long now, Limits l) {
        if (l.cooldownEnabled() && lastMessage > 0) {
            long remaining = l.cooldownMs() - (now - lastMessage);
            if (remaining > 0) return new Verdict(Type.COOLDOWN, remaining);
        }
        if (l.rateEnabled()) {
            while (!recentTimes.isEmpty() && now - recentTimes.peekFirst() > l.rateWindowMs()) recentTimes.pollFirst();
            if (recentTimes.size() >= l.rateMax()) return new Verdict(Type.RATE, 0);
        }
        if (l.repeatEnabled()) {
            String norm = normalizeForCompare(message);
            for (Entry e : history) {
                if (now - e.time() <= l.repeatWindowMs() && e.text().equals(norm)) return new Verdict(Type.REPEAT, 0);
            }
        }
        if (l.floodEnabled() && l.floodPattern().matcher(message).find()) return new Verdict(Type.FLOOD, 0);
        if (l.capsEnabled() && isCaps(message, l)) return new Verdict(Type.CAPS, 0);
        return Verdict.OK;
    }

    private static boolean isCaps(String msg, Limits l) {
        int letters = 0, upper = 0;
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters >= l.capsMinLength() && (upper * 100 / letters) > l.capsMaxPercent();
    }

    /** Record an accepted message. */
    public synchronized void record(String message, long now, Limits l) {
        lastMessage = now;
        recentTimes.addLast(now);
        history.addLast(new Entry(normalizeForCompare(message), now));
        while (history.size() > Math.max(1, l.repeatHistory())) history.pollFirst();
        while (recentTimes.size() > 64) recentTimes.pollFirst();
    }

    /** @return number of spam violations inside the window, including this one. */
    public synchronized int addViolation(long now, long windowMs) {
        violations.addLast(now);
        while (!violations.isEmpty() && now - violations.peekFirst() > windowMs) violations.pollFirst();
        return violations.size();
    }

    public synchronized void clearViolations() {
        violations.clear();
    }
}

package dev.chatmoderation.util;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats durations such as "30s", "10m", "2h", "1d", "1w", "1h30m" or "perm". */
public final class DurationUtil {

    /** Value returned for permanent durations. */
    public static final long PERMANENT = -1L;

    private static final long MAX_MS = 100L * 365 * 24 * 3600 * 1000;
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])");

    private DurationUtil() {}

    /** @return duration in milliseconds, {@link #PERMANENT}, or empty if the input is invalid. */
    public static OptionalLong parse(String input) {
        if (input == null) return OptionalLong.empty();
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return OptionalLong.empty();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever") || s.equals("permanente")) {
            return OptionalLong.of(PERMANENT);
        }
        Matcher m = TOKEN.matcher(s);
        int pos = 0;
        long total = 0;
        try {
            while (pos < s.length()) {
                m.region(pos, s.length());
                if (!m.lookingAt()) return OptionalLong.empty();
                long amount = Long.parseLong(m.group(1));
                total = Math.addExact(total, Math.multiplyExact(amount, unitMs(m.group(2).charAt(0))));
                pos = m.end();
            }
        } catch (ArithmeticException | NumberFormatException ex) {
            return OptionalLong.empty();
        }
        if (total <= 0 || total > MAX_MS) return OptionalLong.empty();
        return OptionalLong.of(total);
    }

    private static long unitMs(char unit) {
        return switch (unit) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 604_800_000L; // 'w'
        };
    }

    /** Formats milliseconds as e.g. "1d 2h 3m 4s" (rounded up to whole seconds). */
    public static String format(long ms) {
        long totalSeconds = Math.max(0, (ms + 999) / 1000);
        long d = totalSeconds / 86400;
        long h = (totalSeconds % 86400) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s ");
        return sb.toString().trim();
    }
}

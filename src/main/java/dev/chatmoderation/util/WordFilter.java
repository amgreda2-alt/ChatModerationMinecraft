package dev.chatmoderation.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects blocked words. Handles case, accents, leetspeak (sh1t), repeated letters (shiiit)
 * and letters separated by symbols or spaces (s h i t, s.h.i.t).
 */
public final class WordFilter {

    private final List<String> words = new ArrayList<>();
    private final List<String> collapsedWords = new ArrayList<>();
    private final List<Pattern> censorPatterns = new ArrayList<>();
    private final boolean matchInside;

    public WordFilter(Collection<String> rawWords, boolean matchInside) {
        this.matchInside = matchInside;
        for (String raw : rawWords) {
            if (raw == null || raw.isBlank()) continue;
            String w = normalize(raw.trim());
            if (w.isEmpty()) continue;
            words.add(w);
            collapsedWords.add(collapse(w));
            String q = Pattern.quote(raw.trim());
            censorPatterns.add(Pattern.compile(matchInside ? q : "(?<![\\p{L}\\p{N}])" + q + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    public boolean isEmpty() {
        return words.isEmpty();
    }

    /** Lowercases, strips accents and maps common leetspeak characters to letters. */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder(n.length());
        for (char c : n.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(switch (c) {
                case '@', '4' -> 'a';
                case '0' -> 'o';
                case '1', '!' -> 'i';
                case '3' -> 'e';
                case '$', '5' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return sb.toString();
    }

    private static String collapse(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        char last = 0;
        for (char c : s.toCharArray()) {
            if (c != last) sb.append(c);
            last = c;
        }
        return sb.toString();
    }

    public boolean containsBlocked(String message) {
        if (words.isEmpty()) return false;
        String[] raw = normalize(message).replaceAll("[^\\p{L}]+", " ").trim().split(" ");
        List<String> tokens = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (String t : raw) {
            if (t.isEmpty()) continue;
            if (t.length() == 1) {
                run.append(t);
                continue;
            }
            if (run.length() > 1) tokens.add(run.toString());
            run.setLength(0);
            tokens.add(t);
        }
        if (run.length() > 1) tokens.add(run.toString());

        for (String token : tokens) {
            String collapsed = collapse(token);
            for (int i = 0; i < words.size(); i++) {
                if (matches(token, words.get(i)) || matches(collapsed, collapsedWords.get(i))) return true;
            }
        }
        return false;
    }

    private boolean matches(String token, String word) {
        return matchInside ? token.contains(word) : token.equals(word);
    }

    /** Replaces literal occurrences of blocked words. Obfuscated variants are not replaced. */
    public String censor(String message, String replacement) {
        String result = message;
        String safe = Matcher.quoteReplacement(replacement);
        for (Pattern p : censorPatterns) {
            result = p.matcher(result).replaceAll(safe);
        }
        return result;
    }
}

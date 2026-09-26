package in.dukkan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ranks a name (and weaker extra fields) against a search query.
 * An exact or leading name match outranks a mention buried in a description or address.
 */
public final class NameRelevance {

    /** Below this, the text is not a real name match. */
    public static final double MIN_SCORE = 12;

    public record Field(String value, double weight) {}

    private NameRelevance() {}

    public static Field field(String value, double weight) {
        return new Field(value, weight);
    }

    public static double score(String query, Field... fields) {
        String needle = normalize(query);
        if (needle.isEmpty() || fields == null || fields.length == 0) {
            return 0;
        }
        double best = 0;
        double sum = 0;
        for (Field field : fields) {
            if (field == null || field.weight() <= 0 || field.value() == null || field.value().isBlank()) {
                continue;
            }
            double weighted = fieldScore(needle, normalize(field.value())) * field.weight();
            best = Math.max(best, weighted);
            sum += weighted;
        }
        if (best <= 0) {
            return 0;
        }
        return best + (sum - best) * 0.08;
    }

    static double fieldScore(String needle, String value) {
        if (needle.isEmpty() || value.isEmpty()) {
            return 0;
        }
        if (value.equals(needle)) {
            return 100;
        }
        if (value.startsWith(needle)) {
            return 86;
        }
        List<String> words = words(value);
        if (words.contains(needle)) {
            return 74;
        }
        if (value.contains(needle)) {
            return 58;
        }
        List<String> tokens = words(needle);
        if (tokens.isEmpty()) {
            return 0;
        }
        double total = 0;
        int matched = 0;
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (words.contains(token)) {
                total += 30;
                matched++;
            } else if (startsWord(words, token)) {
                total += 20;
                matched++;
            } else if (value.contains(token)) {
                total += 8;
                matched++;
            }
        }
        int usable = 0;
        for (String token : tokens) {
            if (token.length() >= 2) {
                usable++;
            }
        }
        if (usable > 1 && matched == usable) {
            total += 18;
        }
        return total;
    }

    private static boolean startsWord(List<String> words, String token) {
        for (String word : words) {
            if (word.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean space = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                space = false;
            } else if (!space && out.length() > 0) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        return out.substring(0, end);
    }

    private static List<String> words(String normalized) {
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] parts = normalized.split(" ");
        List<String> words = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words;
    }
}

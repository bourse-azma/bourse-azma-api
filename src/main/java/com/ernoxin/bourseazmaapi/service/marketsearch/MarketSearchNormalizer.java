package com.ernoxin.bourseazmaapi.service.marketsearch;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MarketSearchNormalizer {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern QUERY_TOKEN_SPLIT_PATTERN = Pattern.compile("[,\\s]+");

    private MarketSearchNormalizer() {
    }

    public static String normalizeForTokenization(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replace('ة', 'ه')
                .replace('ۀ', 'ه')
                .replace('\u200c', ' ')
                .replace('\u200f', ' ')
                .replace('\u200e', ' ')
                .replace('\u00a0', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    public static String normalizeCompact(String value) {
        return normalizeForTokenization(value).replace(" ", "");
    }

    public static List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return QUERY_TOKEN_SPLIT_PATTERN.splitAsStream(query)
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(MarketSearchNormalizer::normalizeCompact)
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }

    public static String[] tokenizeNameWords(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return new String[0];
        }
        String[] rawWords = normalizeForTokenization(rawName).split("\\s+");
        List<String> words = new ArrayList<>(rawWords.length);
        for (String rawWord : rawWords) {
            String word = normalizeCompact(rawWord);
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        return words.toArray(String[]::new);
    }

    public static String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }
}

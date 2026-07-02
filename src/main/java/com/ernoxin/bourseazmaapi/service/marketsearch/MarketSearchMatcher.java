package com.ernoxin.bourseazmaapi.service.marketsearch;

import org.springframework.stereotype.Component;

import java.util.*;

import static com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchIndex.IndexedRow;
import static com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchIndex.SearchIndex;

@Component
public class MarketSearchMatcher {

    public static final int NO_MATCH = Integer.MAX_VALUE;

    public List<Map<String, Object>> match(
            SearchIndex searchIndex,
            List<String> queryTokens,
            String oldInscodesHeader
    ) {
        if (searchIndex.rows().isEmpty()) {
            return List.of();
        }

        int[] candidateIndices = resolveCandidateIndices(searchIndex, queryTokens);
        if (candidateIndices.length == 0) {
            return List.of();
        }

        List<SearchCandidate> matches = new ArrayList<>(Math.min(candidateIndices.length, 64));
        IndexedRow[] rows = searchIndex.rowsArray();
        for (int rowIndex : candidateIndices) {
            IndexedRow indexedRow = rows[rowIndex];
            int symbolMatchRank = computeSymbolMatchRank(indexedRow, queryTokens);
            int nameMatchRank = computeNameMatchRank(indexedRow, queryTokens);
            if (symbolMatchRank == NO_MATCH && nameMatchRank == NO_MATCH) {
                continue;
            }
            matches.add(new SearchCandidate(
                    indexedRow.row(),
                    symbolMatchRank,
                    nameMatchRank,
                    indexedRow.compactSymbol(),
                    indexedRow.compactName()
            ));
        }

        if (matches.isEmpty()) {
            return List.of();
        }

        matches.sort(searchComparator());
        List<Map<String, Object>> results = new ArrayList<>(matches.size());
        for (SearchCandidate candidate : matches) {
            results.add(convertRow(candidate.row(), oldInscodesHeader));
        }
        return List.copyOf(results);
    }

    private int[] resolveCandidateIndices(SearchIndex searchIndex, List<String> queryTokens) {
        int[] candidates = null;
        IndexedRow[] rows = searchIndex.rowsArray();
        for (String token : queryTokens) {
            int[] tokenCandidates = candidatesForToken(searchIndex, token, rows.length);
            candidates = candidates == null ? tokenCandidates : intersectSorted(candidates, tokenCandidates);
            if (candidates.length == 0) {
                return new int[0];
            }
        }

        if (candidates == null) {
            return new int[0];
        }

        int[] verified = new int[candidates.length];
        int verifiedCount = 0;
        int previousRowIndex = -1;
        for (int rowIndex : candidates) {
            if (rowIndex == previousRowIndex) {
                continue;
            }
            previousRowIndex = rowIndex;
            if (containsAllTokens(rows[rowIndex].searchText(), queryTokens)) {
                verified[verifiedCount++] = rowIndex;
            }
        }
        return Arrays.copyOf(verified, verifiedCount);
    }

    private int[] candidatesForToken(SearchIndex searchIndex, String token, int rowCount) {
        if (token.length() >= 3) {
            int[] firstGram = searchIndex.gramPostings().get(token.substring(0, 3));
            if (firstGram == null) {
                return new int[0];
            }
            int[] candidates = firstGram;
            for (int i = 1; i <= token.length() - 3; i++) {
                int[] gramRows = searchIndex.gramPostings().get(token.substring(i, i + 3));
                if (gramRows == null) {
                    return new int[0];
                }
                candidates = intersectSorted(candidates, gramRows);
                if (candidates.length == 0) {
                    return new int[0];
                }
            }
            return candidates;
        }

        int[] allRows = new int[rowCount];
        for (int i = 0; i < rowCount; i++) {
            allRows[i] = i;
        }
        return allRows;
    }

    private boolean containsAllTokens(String searchText, List<String> queryTokens) {
        for (String token : queryTokens) {
            if (!searchText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private int[] intersectSorted(int[] left, int[] right) {
        if (left.length == 0 || right.length == 0) {
            return new int[0];
        }

        int[] intersection = new int[Math.min(left.length, right.length)];
        int leftIndex = 0;
        int rightIndex = 0;
        int intersectionIndex = 0;
        while (leftIndex < left.length && rightIndex < right.length) {
            int leftValue = left[leftIndex];
            int rightValue = right[rightIndex];
            if (leftValue == rightValue) {
                intersection[intersectionIndex++] = leftValue;
                leftIndex++;
                rightIndex++;
            } else if (leftValue < rightValue) {
                leftIndex++;
            } else {
                rightIndex++;
            }
        }
        return intersectionIndex == intersection.length ? intersection : Arrays.copyOf(intersection, intersectionIndex);
    }

    private Comparator<SearchCandidate> searchComparator() {
        return Comparator
                .comparingInt((SearchCandidate candidate) -> candidate.symbolMatchRank() == NO_MATCH ? 1 : 0)
                .thenComparingInt(candidate ->
                        candidate.symbolMatchRank() == NO_MATCH
                                ? candidate.nameMatchRank()
                                : candidate.symbolMatchRank())
                .thenComparingInt(candidate -> candidate.symbol().isBlank() ? Integer.MAX_VALUE : candidate.symbol().length())
                .thenComparingInt(candidate -> candidate.name().isBlank() ? Integer.MAX_VALUE : candidate.name().length())
                .thenComparing(SearchCandidate::symbol)
                .thenComparing(SearchCandidate::name);
    }

    private int computeSymbolMatchRank(IndexedRow indexedRow, List<String> queryTokens) {
        String symbol = indexedRow.compactSymbol();
        if (symbol.isBlank() || queryTokens.isEmpty()) {
            return NO_MATCH;
        }
        if (!containsAllTokens(symbol, queryTokens)) {
            return NO_MATCH;
        }
        if (queryTokens.size() == 1) {
            String token = queryTokens.get(0);
            if (symbol.equals(token)) {
                return 0;
            }
            if (symbol.startsWith(token)) {
                return 1;
            }
            return 2;
        }
        return symbol.startsWith(queryTokens.get(0)) ? 3 : 4;
    }

    private int computeNameMatchRank(IndexedRow indexedRow, List<String> queryTokens) {
        String compactName = indexedRow.compactName();
        String[] words = indexedRow.nameWords();
        if (compactName.isBlank() || queryTokens.isEmpty()) {
            return NO_MATCH;
        }

        int worstRank = 0;
        for (String token : queryTokens) {
            int tokenBestRank = NO_MATCH;
            for (String word : words) {
                tokenBestRank = Math.min(tokenBestRank, rankTokenInWord(token, word));
            }
            if (tokenBestRank == NO_MATCH && compactName.contains(token)) {
                tokenBestRank = 3;
            }
            if (tokenBestRank == NO_MATCH) {
                return NO_MATCH;
            }
            worstRank = Math.max(worstRank, tokenBestRank);
        }
        return worstRank;
    }

    private int rankTokenInWord(String token, String word) {
        if (word.equals(token)) {
            return 0;
        }
        if (word.startsWith(token)) {
            return 1;
        }
        if (word.contains(token)) {
            return 2;
        }
        return NO_MATCH;
    }

    private Map<String, Object> convertRow(Map<String, String> row, String oldInscodesHeader) {
        Map<String, Object> result = new LinkedHashMap<>(row.size());
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (oldInscodesHeader != null && entry.getKey().equals(oldInscodesHeader)) {
                result.put(entry.getKey(), splitOldInscodes(entry.getValue()));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private List<String> splitOldInscodes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawValue.split("\\|"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private record SearchCandidate(
            Map<String, String> row,
            int symbolMatchRank,
            int nameMatchRank,
            String symbol,
            String name
    ) {
    }
}

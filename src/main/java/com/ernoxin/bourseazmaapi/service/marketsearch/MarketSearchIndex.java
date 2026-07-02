package com.ernoxin.bourseazmaapi.service.marketsearch;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MarketSearchIndex {

    private static String getColumnValue(Map<String, String> row, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return row.getOrDefault(key, "");
    }

    public SearchIndex build(List<Map<String, String>> rawRows, String symbolHeader, String nameHeader) {
        int rowCount = rawRows.size();
        IndexedRow[] rows = new IndexedRow[rowCount];
        Map<String, List<Integer>> gramPostings = new HashMap<>();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, String> row = rawRows.get(rowIndex);
            String rawSymbol = getColumnValue(row, symbolHeader);
            String rawName = getColumnValue(row, nameHeader);
            String compactSymbol = MarketSearchNormalizer.normalizeCompact(rawSymbol);
            String compactName = MarketSearchNormalizer.normalizeCompact(rawName);
            String searchText = compactSymbol + compactName;
            String[] nameWords = MarketSearchNormalizer.tokenizeNameWords(rawName);

            rows[rowIndex] = new IndexedRow(row, compactSymbol, compactName, searchText, nameWords);
            indexSearchTextGrams(gramPostings, searchText, rowIndex);
        }

        Map<String, int[]> compactGramPostings = new HashMap<>(gramPostings.size());
        for (Map.Entry<String, List<Integer>> entry : gramPostings.entrySet()) {
            int[] values = entry.getValue().stream()
                    .mapToInt(Integer::intValue)
                    .distinct()
                    .sorted()
                    .toArray();
            compactGramPostings.put(entry.getKey(), values);
        }

        return new SearchIndex(List.of(rows), rows, compactGramPostings);
    }

    private void indexSearchTextGrams(Map<String, List<Integer>> gramPostings, String searchText, int rowIndex) {
        if (searchText.isBlank()) {
            return;
        }
        if (searchText.length() < 3) {
            addPosting(gramPostings, searchText, rowIndex);
            return;
        }
        for (int i = 0; i <= searchText.length() - 3; i++) {
            addPosting(gramPostings, searchText.substring(i, i + 3), rowIndex);
        }
    }

    private void addPosting(Map<String, List<Integer>> gramPostings, String gram, int rowIndex) {
        List<Integer> postings = gramPostings.computeIfAbsent(gram, ignored -> new ArrayList<>(4));
        if (postings.isEmpty() || postings.get(postings.size() - 1) != rowIndex) {
            postings.add(rowIndex);
        }
    }

    public record IndexedRow(
            Map<String, String> row,
            String compactSymbol,
            String compactName,
            String searchText,
            String[] nameWords
    ) {
    }

    public record SearchIndex(
            List<IndexedRow> rows,
            IndexedRow[] rowsArray,
            Map<String, int[]> gramPostings
    ) {
    }
}

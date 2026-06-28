package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolsResult;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSearchServiceImpl implements MarketSearchService {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern QUERY_TOKEN_SPLIT_PATTERN = Pattern.compile("[,\\s]+");
    private static final int NO_MATCH = Integer.MAX_VALUE;
    private static final int SEARCH_CACHE_MAX_ENTRIES = 256;
    private static final String SYMBOL_COLUMN = "symbol";
    private static final String NAME_COLUMN = "name";
    private static final String INDUSTRY_COLUMN = "industry";
    private static final String INSTRUMENT_CODE_COLUMN = "instrumentCode";
    private static final String OLD_INSCODES_COLUMN = "old_inscodes";
    private final Map<String, List<Map<String, Object>>> searchResultCache =
            Collections.synchronizedMap(new LinkedHashMap<>(SEARCH_CACHE_MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> eldest) {
                    return size() > SEARCH_CACHE_MAX_ENTRIES;
                }
            });
    @Value("${app.market-search.csv-path}")
    private String csvPath;
    private volatile CsvCache cache;

    private static String normalizeForTokenization(String value) {
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

    private static String normalizeCompact(String value) {
        return normalizeForTokenization(value).replace(" ", "");
    }

    @Override
    public List<Map<String, Object>> search(String query) {
        List<String> queryTokens = tokenizeQuery(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String cacheKey = String.join("\u0000", queryTokens);
        List<Map<String, Object>> cached = searchResultCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        CsvCache csvCache = getCsvCache();
        SearchIndex searchIndex = csvCache.searchIndex();
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
        String oldInscodesHeader = csvCache.oldInscodesHeader();
        for (SearchCandidate candidate : matches) {
            results.add(convertRow(candidate.row(), oldInscodesHeader));
        }

        List<Map<String, Object>> immutableResults = List.copyOf(results);
        searchResultCache.put(cacheKey, immutableResults);
        return immutableResults;
    }

    @Override
    public List<IndustrySummaryResponse> getIndustries() {
        CsvCache csvCache = getCsvCache();
        return csvCache.industrySummaries();
    }

    @Override
    public IndustrySymbolsResult getIndustrySymbols(String industry) {
        if (industry == null || industry.isBlank()) {
            throw new ResourceNotFoundException("صنعت مورد نظر یافت نشد.");
        }

        String requestedIndustry = industry.trim();
        CsvCache csvCache = getCsvCache();
        List<IndustrySymbolResponse> symbols = csvCache.industrySymbols().get(requestedIndustry);
        if (symbols == null || symbols.isEmpty()) {
            throw new ResourceNotFoundException("صنعت مورد نظر یافت نشد.");
        }
        return new IndustrySymbolsResult(requestedIndustry, symbols);
    }

    private CsvCache getCsvCache() {
        Path csvPath = resolveCsvPath();
        try {
            if (!Files.exists(csvPath) || !Files.isRegularFile(csvPath)) {
                throw new ResourceNotFoundException("فایل نمادها یافت نشد.");
            }

            long fileSize = Files.size(csvPath);
            long lastModified = Files.getLastModifiedTime(csvPath).toMillis();

            CsvCache current = cache;
            if (current != null && current.fileSize() == fileSize && current.lastModified() == lastModified) {
                return current;
            }

            synchronized (this) {
                CsvCache recheck = cache;
                if (recheck != null && recheck.fileSize() == fileSize && recheck.lastModified() == lastModified) {
                    return recheck;
                }

                CsvCache loaded = loadCsv(csvPath, fileSize, lastModified);
                cache = loaded;
                searchResultCache.clear();
                return loaded;
            }
        } catch (IOException ex) {
            log.error("Could not read market csv file. path={}", csvPath, ex);
            throw new IllegalStateException("خواندن فایل نمادها با خطا مواجه شد.");
        }
    }

    private CsvCache loadCsv(Path csvPath, long fileSize, long lastModified) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return CsvCache.empty(fileSize, lastModified);
            }

            List<String> headers = sanitizeHeaders(parseCsvLine(headerLine));
            String symbolHeader = findHeader(headers, SYMBOL_COLUMN);
            String nameHeader = findHeader(headers, NAME_COLUMN);
            String industryHeader = findHeader(headers, INDUSTRY_COLUMN);
            String instrumentCodeHeader = findHeader(headers, INSTRUMENT_CODE_COLUMN);
            String oldInscodesHeader = findHeader(headers, OLD_INSCODES_COLUMN);

            List<Map<String, String>> rawRows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                int maxColumns = Math.max(headers.size(), values.size());
                for (int i = 0; i < maxColumns; i++) {
                    String key = i < headers.size() ? headers.get(i) : "column_" + (i + 1);
                    String value = i < values.size() ? values.get(i) : "";
                    row.put(key, value);
                }
                rawRows.add(Collections.unmodifiableMap(row));
            }

            SearchIndex searchIndex = buildSearchIndex(rawRows, symbolHeader, nameHeader);
            Map<String, Integer> industryCounts = new TreeMap<>();
            Map<String, List<IndustrySymbolResponse>> industrySymbols = new HashMap<>();
            if (industryHeader != null && symbolHeader != null && nameHeader != null) {
                for (Map<String, String> row : rawRows) {
                    String industry = getColumnValue(row, industryHeader).trim();
                    if (industry.isBlank()) {
                        continue;
                    }
                    industryCounts.merge(industry, 1, Integer::sum);

                    String symbol = getColumnValue(row, symbolHeader).trim();
                    String name = getColumnValue(row, nameHeader).trim();
                    if (symbol.isBlank() || name.isBlank()) {
                        continue;
                    }
                    String instrumentCode = instrumentCodeHeader == null
                            ? ""
                            : getColumnValue(row, instrumentCodeHeader).trim();
                    industrySymbols
                            .computeIfAbsent(industry, ignored -> new ArrayList<>())
                            .add(new IndustrySymbolResponse(symbol, name, instrumentCode));
                }
                industrySymbols.replaceAll((industry, symbols) -> {
                    symbols.sort(Comparator.comparing(IndustrySymbolResponse::symbol).thenComparing(IndustrySymbolResponse::name));
                    return List.copyOf(symbols);
                });
            }

            List<IndustrySummaryResponse> industrySummaries = industryCounts.entrySet().stream()
                    .map(entry -> new IndustrySummaryResponse(entry.getKey(), entry.getValue()))
                    .toList();

            log.info("Market CSV loaded. path={} rows={} indexGrams={}", csvPath, rawRows.size(), searchIndex.gramPostings().size());
            return new CsvCache(
                    fileSize,
                    lastModified,
                    symbolHeader,
                    nameHeader,
                    industryHeader,
                    instrumentCodeHeader,
                    oldInscodesHeader,
                    searchIndex,
                    industrySummaries,
                    Map.copyOf(industrySymbols)
            );
        }
    }

    private SearchIndex buildSearchIndex(List<Map<String, String>> rawRows, String symbolHeader, String nameHeader) {
        int rowCount = rawRows.size();
        IndexedRow[] rows = new IndexedRow[rowCount];
        Map<String, List<Integer>> gramPostings = new HashMap<>();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Map<String, String> row = rawRows.get(rowIndex);
            String rawSymbol = getColumnValue(row, symbolHeader);
            String rawName = getColumnValue(row, nameHeader);
            String compactSymbol = normalizeCompact(rawSymbol);
            String compactName = normalizeCompact(rawName);
            String searchText = compactSymbol + compactName;
            String[] nameWords = tokenizeNameWords(rawName);

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

    private Path resolveCsvPath() {
        if (csvPath == null || csvPath.isBlank()) {
            throw new IllegalStateException("مسیر فایل نمادها تنظیم نشده است.");
        }
        Path path = Paths.get(csvPath.trim());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path).normalize();
        }
        return path;
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

    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return QUERY_TOKEN_SPLIT_PATTERN.splitAsStream(query)
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(MarketSearchServiceImpl::normalizeCompact)
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
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

    private String[] tokenizeNameWords(String rawName) {
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

    private String getColumnValue(Map<String, String> row, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return row.getOrDefault(key, "");
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

    private List<String> sanitizeHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>(rawHeaders.size());
        Set<String> usedHeaders = new HashSet<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            String header = stripBom(rawHeaders.get(i)).trim();
            if (header.isBlank()) {
                header = "column_" + (i + 1);
            }

            String uniqueHeader = header;
            int duplicateIndex = 2;
            while (!usedHeaders.add(uniqueHeader.toLowerCase(Locale.ROOT))) {
                uniqueHeader = header + "_" + duplicateIndex++;
            }
            headers.add(uniqueHeader);
        }
        return headers;
    }

    private String findHeader(List<String> headers, String target) {
        for (String header : headers) {
            if (header.equalsIgnoreCase(target)) {
                return header;
            }
        }
        return null;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private record SearchCandidate(
            Map<String, String> row,
            int symbolMatchRank,
            int nameMatchRank,
            String symbol,
            String name
    ) {
    }

    private record IndexedRow(
            Map<String, String> row,
            String compactSymbol,
            String compactName,
            String searchText,
            String[] nameWords
    ) {
    }

    private record SearchIndex(
            List<IndexedRow> rows,
            IndexedRow[] rowsArray,
            Map<String, int[]> gramPostings
    ) {
    }

    private record CsvCache(
            long fileSize,
            long lastModified,
            String symbolHeader,
            String nameHeader,
            String industryHeader,
            String instrumentCodeHeader,
            String oldInscodesHeader,
            SearchIndex searchIndex,
            List<IndustrySummaryResponse> industrySummaries,
            Map<String, List<IndustrySymbolResponse>> industrySymbols
    ) {
        private static CsvCache empty(long fileSize, long lastModified) {
            IndexedRow[] rows = new IndexedRow[0];
            return new CsvCache(
                    fileSize,
                    lastModified,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new SearchIndex(List.of(), rows, Map.of()),
                    List.of(),
                    Map.of()
            );
        }
    }
}

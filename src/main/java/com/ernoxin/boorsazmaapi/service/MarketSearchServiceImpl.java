package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.exception.ResourceNotFoundException;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSearchServiceImpl implements MarketSearchService {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final String SYMBOL_COLUMN = "symbol";
    private static final String NAME_COLUMN = "name";
    private static final String OLD_INSCODES_COLUMN = "old_inscodes";

    @Value("${app.market-search.csv-path}")
    private String csvPath;

    private volatile CsvCache cache;

    @Override
    public List<Map<String, Object>> search(String query) {
        List<String> normalizedQueries = normalizeQueries(query);
        if (normalizedQueries.isEmpty()) {
            return List.of();
        }

        CsvCache csvCache = getCsvCache();
        return csvCache.rows().stream()
                .map(row -> new SearchCandidate(
                        row,
                        computeRelevanceScore(row, normalizedQueries, csvCache.symbolHeader(), csvCache.nameHeader()),
                        computeBestMatchRank(getColumnValue(row, csvCache.symbolHeader()), normalizedQueries),
                        computeBestMatchRank(getColumnValue(row, csvCache.nameHeader()), normalizedQueries),
                        normalize(getColumnValue(row, csvCache.symbolHeader())),
                        normalize(getColumnValue(row, csvCache.nameHeader()))
                ))
                .filter(candidate -> candidate.score() > 0)
                .sorted(
                        Comparator.comparingInt(SearchCandidate::symbolMatchRank)
                                .thenComparingInt(SearchCandidate::nameMatchRank)
                                .thenComparing(Comparator.comparingInt(SearchCandidate::score).reversed())
                                .thenComparingInt(candidate -> candidate.symbol().isBlank() ? Integer.MAX_VALUE : candidate.symbol().length())
                                .thenComparingInt(candidate -> candidate.name().isBlank() ? Integer.MAX_VALUE : candidate.name().length())
                                .thenComparing(SearchCandidate::symbol)
                                .thenComparing(SearchCandidate::name)
                )
                .map(candidate -> convertRow(candidate.row(), csvCache.oldInscodesHeader()))
                .toList();
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
                return new CsvCache(fileSize, lastModified, null, null, null, List.<Map<String, String>>of());
            }

            List<String> headers = sanitizeHeaders(parseCsvLine(headerLine));
            String symbolHeader = findHeader(headers, SYMBOL_COLUMN);
            String nameHeader = findHeader(headers, NAME_COLUMN);
            String oldInscodesHeader = findHeader(headers, OLD_INSCODES_COLUMN);

            List<Map<String, String>> rows = new ArrayList<>();
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
                rows.add(Collections.unmodifiableMap(row));
            }

            log.info("Market CSV loaded. path={} rows={}", csvPath, rows.size());
            return new CsvCache(fileSize, lastModified, symbolHeader, nameHeader, oldInscodesHeader, List.copyOf(rows));
        }
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

    private List<String> normalizeQueries(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.split(","))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .map(this::normalize)
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }

    private int computeRelevanceScore(Map<String, String> row,
                                      List<String> normalizedQueries,
                                      String symbolHeader,
                                      String nameHeader) {
        String symbol = normalize(getColumnValue(row, symbolHeader));
        String name = normalize(getColumnValue(row, nameHeader));
        String allValues = normalize(String.join(" ", row.values()));

        if (symbol.isBlank() && name.isBlank()) {
            return normalizedQueries.stream()
                    .mapToInt(query -> scoreMatch(allValues, query, 10, 8, 3))
                    .sum();
        }

        int score = 0;
        for (String query : normalizedQueries) {
            score += scoreMatch(symbol, query, 1200, 900, 350);
            score += scoreMatch(name, query, 600, 420, 170);
            score += scoreMatch(allValues, query, 8, 6, 2);
        }
        return score;
    }

    private int computeBestMatchRank(String rawValue, List<String> normalizedQueries) {
        String value = normalize(rawValue);
        if (value.isBlank()) {
            return Integer.MAX_VALUE;
        }

        int rank = Integer.MAX_VALUE;
        for (String query : normalizedQueries) {
            if (value.equals(query)) {
                rank = Math.min(rank, 0);
            } else if (value.startsWith(query)) {
                rank = Math.min(rank, 1);
            } else if (value.contains(query)) {
                rank = Math.min(rank, 2);
            }
        }
        return rank;
    }

    private int scoreMatch(String value,
                           String query,
                           int exactScore,
                           int prefixScore,
                           int containsScore) {
        if (value == null || value.isBlank() || query == null || query.isBlank()) {
            return 0;
        }
        if (value.equals(query)) {
            return exactScore;
        }
        if (value.startsWith(query)) {
            return prefixScore;
        }
        if (value.contains(query)) {
            return containsScore;
        }
        return 0;
    }

    private String getColumnValue(Map<String, String> row, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return row.getOrDefault(key, "");
    }

    private Map<String, Object> convertRow(Map<String, String> row, String oldInscodesHeader) {
        Map<String, Object> result = new LinkedHashMap<>();
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

    private String normalize(String value) {
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
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll("");
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private record SearchCandidate(
            Map<String, String> row,
            int score,
            int symbolMatchRank,
            int nameMatchRank,
            String symbol,
            String name
    ) {
    }

    private record CsvCache(
            long fileSize,
            long lastModified,
            String symbolHeader,
            String nameHeader,
            String oldInscodesHeader,
            List<Map<String, String>> rows
    ) {
    }
}

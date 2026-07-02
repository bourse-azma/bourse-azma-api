package com.ernoxin.bourseazmaapi.service.marketsearch;

import com.ernoxin.bourseazmaapi.dto.IndustrySummaryResponse;
import com.ernoxin.bourseazmaapi.dto.IndustrySymbolResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.ernoxin.bourseazmaapi.service.marketsearch.MarketSearchIndex.SearchIndex;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketCsvLoader {

    private static final String SYMBOL_COLUMN = "symbol";
    private static final String NAME_COLUMN = "name";
    private static final String INDUSTRY_COLUMN = "industry";
    private static final String INSTRUMENT_CODE_COLUMN = "instrumentCode";
    private static final String OLD_INSCODES_COLUMN = "old_inscodes";

    private final MarketSearchIndex marketSearchIndex;
    private final MarketSearchResultCache searchResultCache;

    @Value("${app.market-search.csv-path}")
    private String csvPath;

    private volatile CsvCache cache;

    public CsvCache getCsvCache() {
        Path resolvedPath = resolveCsvPath();
        try {
            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                throw new ResourceNotFoundException("فایل نمادها یافت نشد.");
            }

            long fileSize = Files.size(resolvedPath);
            long lastModified = Files.getLastModifiedTime(resolvedPath).toMillis();

            CsvCache current = cache;
            if (current != null && current.fileSize() == fileSize && current.lastModified() == lastModified) {
                return current;
            }

            synchronized (this) {
                CsvCache recheck = cache;
                if (recheck != null && recheck.fileSize() == fileSize && recheck.lastModified() == lastModified) {
                    return recheck;
                }

                CsvCache loaded = loadCsv(resolvedPath, fileSize, lastModified);
                cache = loaded;
                searchResultCache.clear();
                return loaded;
            }
        } catch (IOException ex) {
            log.error("Could not read market csv file. path={}", resolvedPath, ex);
            throw new IllegalStateException("خواندن فایل نمادها با خطا مواجه شد.");
        }
    }

    public void clearCache() {
        cache = null;
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

            SearchIndex searchIndex = marketSearchIndex.build(rawRows, symbolHeader, nameHeader);
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

    private String getColumnValue(Map<String, String> row, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return row.getOrDefault(key, "");
    }

    private List<String> sanitizeHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>(rawHeaders.size());
        Set<String> usedHeaders = new HashSet<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            String header = MarketSearchNormalizer.stripBom(rawHeaders.get(i)).trim();
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

    public record CsvCache(
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
            MarketSearchIndex.IndexedRow[] rows = new MarketSearchIndex.IndexedRow[0];
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

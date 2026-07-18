package com.ernoxin.bourseazmaapi.client;

import com.ernoxin.bourseazmaapi.config.TsetmcApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TsetmcMarketClient {

    private final RestTemplate tsetmcRestTemplate;
    private final TsetmcApiProperties properties;

    public Optional<JsonNode> getMarketOverview(int marketId) {
        String url = properties.baseUrl() + "/api/v1/overview/" + marketId;
        try {
            JsonNode root = tsetmcRestTemplate.getForObject(url, JsonNode.class);
            if (root == null || root.isMissingNode() || root.isNull()) {
                return Optional.empty();
            }
            JsonNode result = root.get("result");
            if (result == null || result.isMissingNode() || result.isNull()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch TSETMC market overview for market {}: {}", marketId, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonNode> getBestLimits(String instrumentCode) {
        return getInstrumentResource(instrumentCode, "best-limits");
    }

    public Optional<JsonNode> getClosingPrice(String instrumentCode) {
        return getInstrumentResource(instrumentCode, "closing-price");
    }

    public Optional<JsonNode> getClientType(String instrumentCode) {
        return getInstrumentResource(instrumentCode, "client-type/0/0");
    }

    private Optional<JsonNode> getInstrumentResource(String instrumentCode, String resourcePath) {
        String normalizedCode = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalizedCode.isEmpty()) {
            return Optional.empty();
        }

        String url = properties.baseUrl()
                + "/api/v1/instruments/"
                + normalizedCode
                + "/"
                + resourcePath;

        try {
            JsonNode root = tsetmcRestTemplate.getForObject(url, JsonNode.class);
            if (root == null || root.isMissingNode() || root.isNull()) {
                return Optional.empty();
            }
            JsonNode result = root.get("result");
            if (result == null || result.isMissingNode() || result.isNull()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch TSETMC {} for {}: {}", resourcePath, normalizedCode, ex.getMessage());
            return Optional.empty();
        }
    }
}

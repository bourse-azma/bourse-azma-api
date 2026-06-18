package com.ernoxin.boorsazmaapi.client;

import com.ernoxin.boorsazmaapi.config.TsetmcApiProperties;
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

    public Optional<JsonNode> getBestLimits(String instrumentCode) {
        String normalizedCode = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalizedCode.isEmpty()) {
            return Optional.empty();
        }

        String url = properties.baseUrl()
                + "/api/v1/instruments/"
                + normalizedCode
                + "/best-limits";

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
            log.warn("Failed to fetch TSETMC best limits for {}: {}", normalizedCode, ex.getMessage());
            return Optional.empty();
        }
    }
}

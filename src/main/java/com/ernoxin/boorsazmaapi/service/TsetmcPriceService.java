package com.ernoxin.boorsazmaapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TsetmcPriceService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.tsetmc-api.base-url:http://localhost:9000/tsetmc/api/v1}")
    private String tsetmcApiBaseUrl;

    public Optional<BigDecimal> getLivePrice(String instrumentCode) {
        if (instrumentCode == null || instrumentCode.isBlank()) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder
                .fromUriString(tsetmcApiBaseUrl)
                .pathSegment("instruments", instrumentCode, "closing-price")
                .toUriString();

        try {
            JsonNode payload = restTemplate.getForObject(url, JsonNode.class);
            JsonNode result = payload == null ? null : payload.path("result");
            if (result == null || result.isMissingNode() || result.isNull()) {
                return Optional.empty();
            }

            BigDecimal lastTradePrice = decimalOrNull(result.path("lastTradePrice"));
            if (lastTradePrice != null && lastTradePrice.signum() > 0) {
                return Optional.of(lastTradePrice);
            }

            BigDecimal closingPrice = decimalOrNull(result.path("closingPrice"));
            if (closingPrice != null && closingPrice.signum() > 0) {
                return Optional.of(closingPrice);
            }
        } catch (RestClientException | IllegalArgumentException ex) {
            log.debug("Could not fetch TSETMC live price for instrument {}", instrumentCode, ex);
        }

        return Optional.empty();
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }
}

package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketStateServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TsetmcMarketClient client;
    private MarketStateService service;

    @BeforeEach
    void setUp() {
        client = mock(TsetmcMarketClient.class);
        service = new MarketStateService(client);
    }

    @Test
    void reportsOpenWhenEitherKnownMarketIsOpen() throws Exception {
        when(client.getMarketOverview(1)).thenReturn(Optional.of(overview("باز")));
        when(client.getMarketOverview(2)).thenReturn(Optional.empty());

        assertThat(service.getSessionState()).isEqualTo(MarketSessionState.OPEN);
        assertThat(service.isMarketOpen()).isTrue();
    }

    @Test
    void reportsClosedOnlyWhenBothMarketSourcesAreKnownAndClosed() throws Exception {
        when(client.getMarketOverview(1)).thenReturn(Optional.of(overview("بسته")));
        when(client.getMarketOverview(2)).thenReturn(Optional.of(overview("بسته")));

        assertThat(service.getSessionState()).isEqualTo(MarketSessionState.CLOSED);
    }

    @Test
    void reportsUnknownInsteadOfClosedDuringAnApiOutage() throws Exception {
        when(client.getMarketOverview(1)).thenReturn(Optional.of(overview("بسته")));
        when(client.getMarketOverview(2)).thenReturn(Optional.empty());

        assertThat(service.getSessionState()).isEqualTo(MarketSessionState.UNKNOWN);
    }

    private JsonNode overview(String title) throws Exception {
        return objectMapper.readTree("{\"marketOverview\":{\"marketStateTitle\":\"" + title + "\"}}");
    }
}

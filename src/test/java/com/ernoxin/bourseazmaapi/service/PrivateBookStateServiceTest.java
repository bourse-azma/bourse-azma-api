package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateBookStateServiceTest {

    private MarketLiquidityService marketLiquidityService;
    private UserLiquidityConsumptionRepository consumptionRepository;
    private PrivateBookStateService service;

    @BeforeEach
    void setUp() {
        marketLiquidityService = mock(MarketLiquidityService.class);
        consumptionRepository = mock(UserLiquidityConsumptionRepository.class);
        service = new PrivateBookStateService(marketLiquidityService, consumptionRepository);

        when(marketLiquidityService.getAskLevels("CODE")).thenReturn(List.of(
                new MarketLiquidityLevel(1, new BigDecimal("100"), 1_000, 10)
        ));
    }

    @Test
    void subtractsSimulatedConsumptionOnlyFromTheUserWhoCreatedIt() {
        when(consumptionRepository.findAllByUserIdAndInstrumentCode(1L, "CODE"))
                .thenReturn(List.of(consumption(1L, 400)));
        when(consumptionRepository.findAllByUserIdAndInstrumentCode(2L, "CODE"))
                .thenReturn(List.of());

        List<ResidualBookLevel> firstUsersLevels = service.loadResidualAskLevels(1L, "CODE");
        List<ResidualBookLevel> secondUsersLevels = service.loadResidualAskLevels(2L, "CODE");

        assertThat(firstUsersLevels).singleElement()
                .extracting(ResidualBookLevel::residualVolume)
                .isEqualTo(600L);
        assertThat(secondUsersLevels).singleElement()
                .extracting(ResidualBookLevel::residualVolume)
                .isEqualTo(1_000L);
    }

    private UserLiquidityConsumption consumption(Long userId, long quantity) {
        UserLiquidityConsumption row = new UserLiquidityConsumption();
        row.setUserId(userId);
        row.setInstrumentCode("CODE");
        row.setBookSide(BookSide.ASK);
        row.setPrice(new BigDecimal("100.00"));
        row.setConsumedQuantity(quantity);
        row.setUpdatedAt(Instant.now());
        return row;
    }
}

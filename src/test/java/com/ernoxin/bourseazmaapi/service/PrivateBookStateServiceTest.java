package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateBookStateServiceTest {

    @Mock
    private MarketLiquidityService marketLiquidityService;
    @Mock
    private UserLiquidityConsumptionRepository consumptionRepository;

    private PrivateBookStateService service;

    @BeforeEach
    void setUp() {
        service = new PrivateBookStateService(marketLiquidityService, consumptionRepository);
    }

    @Test
    void residualVolumeSubtractsPriorConsumptionsPerUser() {
        when(marketLiquidityService.getAskLevels("INS")).thenReturn(List.of(
                new MarketLiquidityLevel(1, bd("100"), 1_000, 5),
                new MarketLiquidityLevel(2, bd("101"), 400, 2)
        ));
        UserLiquidityConsumption prior = new UserLiquidityConsumption();
        prior.setBookSide(BookSide.ASK);
        prior.setPrice(bd("100"));
        prior.setConsumedQuantity(300L);
        when(consumptionRepository.findAllByUserIdAndInstrumentCode(9L, "INS"))
                .thenReturn(List.of(prior));

        List<ResidualBookLevel> levels = service.loadResidualAskLevels(9L, "INS");

        assertThat(levels).hasSize(2);
        assertThat(levels.get(0).price()).isEqualByComparingTo("100");
        assertThat(levels.get(0).residualVolume()).isEqualTo(700);
        assertThat(levels.get(1).residualVolume()).isEqualTo(400);
    }

    @Test
    void fullyConsumedLevelDisappearsFromResidualBook() {
        when(marketLiquidityService.getBidLevels("INS")).thenReturn(List.of(
                new MarketLiquidityLevel(1, bd("99"), 50, 1)
        ));
        UserLiquidityConsumption prior = new UserLiquidityConsumption();
        prior.setBookSide(BookSide.BID);
        prior.setPrice(bd("99"));
        prior.setConsumedQuantity(50L);
        when(consumptionRepository.findAllByUserIdAndInstrumentCode(3L, "INS"))
                .thenReturn(List.of(prior));

        assertThat(service.loadResidualBidLevels(3L, "INS")).isEmpty();
    }

    @Test
    void consumeAccumulatesOnExistingRow() {
        UserLiquidityConsumption existing = new UserLiquidityConsumption();
        existing.setUserId(1L);
        existing.setInstrumentCode("INS");
        existing.setBookSide(BookSide.ASK);
        existing.setPrice(bd("100.00"));
        existing.setConsumedQuantity(10L);
        when(consumptionRepository.findByUserIdAndInstrumentCodeAndBookSideAndPrice(
                1L, "INS", BookSide.ASK, bd("100.00")))
                .thenReturn(Optional.of(existing));
        when(consumptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.consume(1L, "INS", BookSide.ASK, bd("100"), 7);

        ArgumentCaptor<UserLiquidityConsumption> captor = ArgumentCaptor.forClass(UserLiquidityConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertThat(captor.getValue().getConsumedQuantity()).isEqualTo(17L);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

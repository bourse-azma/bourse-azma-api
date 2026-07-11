package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.mapper.WalletMapper;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WalletServiceImplTest {

    @Test
    void subtractCannotConsumeBalanceReservedForBuyOrders() {
        UserRepository users = mock(UserRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        TradingOrderRepository orders = mock(TradingOrderRepository.class);
        User user = new User();
        user.setId(7L);
        user.setBalance(new BigDecimal("1000"));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(orders.sumReservedBuyValue(7L)).thenReturn(new BigDecimal("800"));
        WalletServiceImpl service = new WalletServiceImpl(
                users, transactions, mock(UserMapper.class), mock(WalletMapper.class), orders);
        WalletAdjustmentRequest request = new WalletAdjustmentRequest();
        request.setType("SUBTRACT");
        request.setValue(new BigDecimal("201"));

        assertThatThrownBy(() -> service.adjustBalance(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("بلوکه");
        verify(users, never()).save(any());
        verify(transactions, never()).save(any());
    }
}

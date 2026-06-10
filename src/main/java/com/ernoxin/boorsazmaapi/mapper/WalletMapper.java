package com.ernoxin.boorsazmaapi.mapper;

import com.ernoxin.boorsazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.boorsazmaapi.model.WalletTransaction;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    WalletTransactionResponse toDto(WalletTransaction entity);

    List<WalletTransactionResponse> toDtoList(List<WalletTransaction> entities);
}

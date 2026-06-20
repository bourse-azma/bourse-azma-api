package com.ernoxin.bourseazmaapi.mapper;

import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    WalletTransactionResponse toDto(WalletTransaction entity);

    List<WalletTransactionResponse> toDtoList(List<WalletTransaction> entities);
}

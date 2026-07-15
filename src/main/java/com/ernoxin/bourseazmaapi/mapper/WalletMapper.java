package com.ernoxin.bourseazmaapi.mapper;

import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    @Mapping(target = "performedByAdminId", source = "performedByAdmin.id")
    @Mapping(target = "performedByAdminName", expression = "java(adminName(entity))")
    WalletTransactionResponse toDto(WalletTransaction entity);

    List<WalletTransactionResponse> toDtoList(List<WalletTransaction> entities);

    default String adminName(WalletTransaction entity) {
        if (entity.getPerformedByAdmin() == null) return null;
        return entity.getPerformedByAdmin().getFirstName() + " " + entity.getPerformedByAdmin().getLastName();
    }
}

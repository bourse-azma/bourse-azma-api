package com.ernoxin.bourseazmaapi.mapper;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserSelfUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toDto(User entity);

    List<UserResponse> toDtoList(List<User> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    User toEntity(UserCreateRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "balance", ignore = true)
    void updateEntity(UserUpdateRequest dto, @MappingTarget User entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "balance", ignore = true)
    void updateEntity(UserSelfUpdateRequest dto, @MappingTarget User entity);
}

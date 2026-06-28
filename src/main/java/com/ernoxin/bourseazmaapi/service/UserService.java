package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserSelfUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;

import java.util.List;

public interface UserService {
    UserResponse create(UserCreateRequest request);

    UserResponse register(RegisterRequest request);

    UserResponse getById(Long id);

    UserResponse getCurrentUser();

    List<UserResponse> getAll();

    UserResponse update(UserUpdateRequest request);

    UserResponse updateCurrentUser(UserSelfUpdateRequest request);

    void delete(Long id);
}

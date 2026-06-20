package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;

import java.util.List;

public interface UserService {
    UserResponse create(UserCreateRequest request);

    UserResponse register(RegisterRequest request);

    UserResponse getById(Long id);

    List<UserResponse> getAll();

    UserResponse update(UserUpdateRequest request);

    void delete(Long id);
}

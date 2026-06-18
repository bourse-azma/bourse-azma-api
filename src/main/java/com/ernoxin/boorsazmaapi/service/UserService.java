package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.UserCreateRequest;
import com.ernoxin.boorsazmaapi.dto.UserResponse;
import com.ernoxin.boorsazmaapi.dto.UserUpdateRequest;
import com.ernoxin.boorsazmaapi.dto.auth.RegisterRequest;

import java.util.List;

public interface UserService {
    UserResponse create(UserCreateRequest request);

    UserResponse register(RegisterRequest request);

    UserResponse getById(Long id);

    List<UserResponse> getAll();

    UserResponse update(UserUpdateRequest request);

    void delete(Long id);
}

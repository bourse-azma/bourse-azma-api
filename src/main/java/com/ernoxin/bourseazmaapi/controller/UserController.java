package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserSelfUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", userService.create(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", userService.getCurrentUser());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable Long id) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> getAll() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", userService.getAll());
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateCurrentUser(@Valid @RequestBody UserSelfUpdateRequest request) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", userService.updateCurrentUser(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", userService.update(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", null);
    }
}

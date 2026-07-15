package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.admin.*;
import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.service.AdminDashboardService;
import com.ernoxin.bourseazmaapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;
    private final UserService userService;

    @GetMapping("/stats")
    public ApiResponse<AdminDashboardStatsResponse> stats() {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", adminDashboardService.stats());
    }

    @GetMapping("/users")
    public ApiResponse<PagedResponse<AdminUserSummaryResponse>> users(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean onlineOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد",
                adminDashboardService.users(search, onlineOnly, page, size));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<AdminUserDetailResponse> userDetail(@PathVariable Long userId) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", adminDashboardService.userDetail(userId));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.of(HttpStatus.CREATED, "کاربر با موفقیت ثبت شد.", userService.create(request));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<UserResponse> updateUser(@PathVariable Long userId,
                                                @Valid @RequestBody UserUpdateRequest request) {
        request.setId(userId);
        return ApiResponse.of(HttpStatus.OK, "اطلاعات کاربر بروزرسانی شد.", userService.update(request));
    }

    @PatchMapping("/users/{userId}/block")
    public ApiResponse<UserResponse> setBlocked(@PathVariable Long userId,
                                                @Valid @RequestBody AdminUserBlockRequest request) {
        return ApiResponse.of(HttpStatus.OK, request.blocked() ? "کاربر مسدود شد." : "مسدودی کاربر برداشته شد.",
                userService.setBlocked(userId, request.blocked(), request.reason()));
    }

    @PatchMapping("/users/{userId}/balance")
    public ApiResponse<AdminUserDetailResponse> updateBalance(
            @PathVariable Long userId,
            @Valid @RequestBody AdminBalanceUpdateRequest request) {
        return ApiResponse.of(HttpStatus.OK, "موجودی کاربر ویرایش و در سوابق ثبت شد.",
                adminDashboardService.updateBalance(userId, request));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        userService.delete(userId);
        return ApiResponse.of(HttpStatus.OK, "کاربر با موفقیت حذف شد.", null);
    }
}

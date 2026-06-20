package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserUpdateRequest {

    @NotNull(message = "شناسه کاربر نباید خالی باشد.")
    @Positive(message = "شناسه کاربر باید عددی مثبت باشد.")
    private Long id;

    @NotBlank(message = "نام نباید خالی باشد.")
    @Pattern(regexp = "^[آاأإئؤءبپتثجچحخدذرزژسشصضطظعغفقکكيگگلمنوهةیى\\s\\u200C]+$", message = "نام باید فقط با حروف فارسی وارد شود.")
    private String firstName;

    @NotBlank(message = "نام خانوادگی نباید خالی باشد.")
    @Pattern(regexp = "^[آاأإئؤءبپتثجچحخدذرزژسشصضطظعغفقکكيگگلمنوهةیى\\s\\u200C]+$", message = "نام خانوادگی باید فقط با حروف فارسی وارد شود.")
    private String lastName;

    @NotBlank(message = "نام کاربری نباید خالی باشد.")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$", message = "نام کاربری باید ۳ تا ۵۰ کاراکتر و شامل حروف انگلیسی، عدد یا . _ - باشد.")
    private String username;

    @Pattern(regexp = "^$|^\\d{10}$", message = "کد ملی باید دقیقا ۱۰ رقم باشد.")
    private String nationalCode;

    @Pattern(regexp = "^$|^\\+98\\d{10}$", message = "شماره موبایل باید با +98 شروع شود و ۱۰ رقم بعد از آن داشته باشد.")
    private String phoneNumber;

    @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "ایمیل واردشده معتبر نیست.")
    private String email;

    @Size(min = 8, max = 24, message = "رمز عبور باید بین ۸ تا ۲۴ کاراکتر باشد.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "رمز عبور باید حداقل شامل یک حرف و یک عدد باشد."
    )
    private String password;

    private String currentPassword;
}

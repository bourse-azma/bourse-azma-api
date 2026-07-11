package com.ernoxin.bourseazmaapi.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserCreateRequest {

    @NotBlank(message = "نام کاربری نباید خالی باشد.")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$", message = "نام کاربری باید ۳ تا ۵۰ کاراکتر و شامل حروف انگلیسی، عدد یا . _ - باشد.")
    private String username;

    @NotBlank(message = "نام نباید خالی باشد.")
    @Pattern(regexp = "^[آاأإئؤءبپتثجچحخدذرزژسشصضطظعغفقکكيگگلمنوهةیى\\s\\u200C]+$", message = "نام باید فقط با حروف فارسی وارد شود.")
    private String firstName;

    @NotBlank(message = "نام خانوادگی نباید خالی باشد.")
    @Pattern(regexp = "^[آاأإئؤءبپتثجچحخدذرزژسشصضطظعغفقکكيگگلمنوهةیى\\s\\u200C]+$", message = "نام خانوادگی باید فقط با حروف فارسی وارد شود.")
    private String lastName;

    @Pattern(regexp = "^$|^09\\d{9}$", message = "شماره موبایل باید با 09 شروع شود و ۱۱ رقم داشته باشد.")
    private String phoneNumber;

    @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "ایمیل واردشده معتبر نیست.")
    private String email;

    @NotBlank(message = "رمز عبور نباید خالی باشد.")
    @Size(min = 8, max = 24, message = "رمز عبور باید بین ۸ تا ۲۴ کاراکتر باشد.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "رمز عبور باید حداقل شامل یک حرف و یک عدد باشد."
    )
    private String password;

    @NotNull(message = "موجودی اولیه نباید خالی باشد.")
    @DecimalMin(value = "0", message = "موجودی اولیه نمی‌تواند منفی باشد.")
    private java.math.BigDecimal balance;
}

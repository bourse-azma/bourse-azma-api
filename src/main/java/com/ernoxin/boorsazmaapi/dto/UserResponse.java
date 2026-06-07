package com.ernoxin.boorsazmaapi.dto;

import com.ernoxin.boorsazmaapi.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserResponse {

    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String nationalCode;
    private String phoneNumber;
    private String email;
    private UserRole role;
    private java.math.BigDecimal balance;
}

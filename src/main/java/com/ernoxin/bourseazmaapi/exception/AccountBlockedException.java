package com.ernoxin.bourseazmaapi.exception;

public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException() {
        super("حساب کاربری شما مسدود شده است. برای پیگیری با پشتیبانی تماس بگیرید.");
    }
}

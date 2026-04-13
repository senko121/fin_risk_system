package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
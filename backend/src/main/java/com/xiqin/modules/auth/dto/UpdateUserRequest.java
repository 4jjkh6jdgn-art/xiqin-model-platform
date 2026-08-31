package com.xiqin.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {
    private String email;
    private String phone;
    private String avatarUrl;
    private Long roleId;
    private Integer status;
    private Long groupLeaderId;
    private String password; // for password reset
}

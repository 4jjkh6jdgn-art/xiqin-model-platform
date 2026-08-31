package com.xiqin.modules.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度3-32个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度6-64个字符")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;
    private String invitationCode;
}

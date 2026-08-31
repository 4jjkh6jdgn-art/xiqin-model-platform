package com.xiqin.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度应为 3-32 个字符")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @NotNull(message = "请选择角色")
    private Long roleId;
}

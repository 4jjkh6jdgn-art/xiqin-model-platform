package com.xiqin.modules.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BatchCreateUserRequest {
    @NotBlank(message = "账号前缀不能为空")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,20}", message = "前缀需以字母开头，可包含字母、数字、下划线和短横线")
    private String prefix;

    @Min(value = 0, message = "起始序号不能小于 0")
    @Max(value = 999999, message = "起始序号过大")
    private Integer startNumber = 1;

    @Min(value = 1, message = "至少生成 1 个用户")
    @Max(value = 100, message = "单次最多生成 100 个用户")
    private Integer count = 1;

    @Min(value = 1, message = "序号位数至少为 1")
    @Max(value = 6, message = "序号位数最多为 6")
    private Integer numberWidth = 3;

    @NotNull(message = "请选择角色")
    private Long roleId;
}

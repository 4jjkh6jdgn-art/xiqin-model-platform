package com.xiqin.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String avatar;
    private String roleName;
    private String roleCode;
    private Long roleId;
    private Integer status;
    private String statusText;
    private Long groupLeaderId;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private List<String> permissions;
}

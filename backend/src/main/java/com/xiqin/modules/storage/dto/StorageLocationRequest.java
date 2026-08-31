package com.xiqin.modules.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StorageLocationRequest {
    @NotBlank(message = "名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "请选择类型")
    @Pattern(regexp = "MINIO|LOCAL|SMB|FTP|SFTP", message = "不支持的存储类型")
    private String type;

    @NotBlank(message = "地址不能为空")
    @Size(max = 500)
    private String address;

    @Size(max = 500)
    private String mountPath;

    @Size(max = 120)
    private String username;

    @Size(max = 500)
    private String credentialSecret;
}

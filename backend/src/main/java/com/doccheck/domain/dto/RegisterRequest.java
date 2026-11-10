package com.doccheck.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。确认密码由前端校验，服务端只接收最终密码。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "用户名需为 4-32 位字母、数字或下划线")
    private String username;

    @Size(max = 32, message = "显示名不能超过 32 个字符")
    private String displayName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
    private String password;
}

package com.xiqin.config;

import com.xiqin.modules.auth.entity.Role;
import com.xiqin.modules.auth.entity.Permission;
import com.xiqin.modules.auth.entity.User;
import com.xiqin.modules.auth.repository.RoleRepository;
import com.xiqin.modules.auth.repository.PermissionRepository;
import com.xiqin.modules.auth.repository.UserRepository;
import com.xiqin.modules.auth.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin-username:admin}")
    private String adminUsername;

    @Value("${app.init.admin-password:admin@xiqin2024}")
    private String adminPassword;

    @Value("${app.init.admin-email:admin@xiqin.local}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        // Ensure system roles exist (for H2/embedded DB without Flyway)
        Role adminRole = roleRepository.findByCode("admin")
                .orElseGet(() -> {
                    Role role = Role.builder()
                            .name("系统管理员")
                            .code("admin")
                            .description("拥有所有权限")
                            .isSystem(true)
                            .build();
                    return roleRepository.save(role);
                });

        roleRepository.findByCode("leader")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("组长").code("leader")
                        .description("项目组长，可审批注册申请")
                        .isSystem(true).build()));

        roleRepository.findByCode("member")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("组员").code("member")
                        .description("普通组员")
                        .isSystem(true).build()));

        // Ensure admin user exists with correct password
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            User admin = User.builder()
                    .username(adminUsername)
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(adminRole)
                    .status(1)
                    .build();
            userRepository.save(admin);
            log.info("Created initial admin user: {}", adminUsername);
        }
    }
}

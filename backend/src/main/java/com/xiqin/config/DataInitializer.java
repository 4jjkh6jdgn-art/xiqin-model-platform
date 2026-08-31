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
        // Ensure admin user exists with correct password
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            Role adminRole = roleRepository.findByCode("admin")
                    .orElseThrow(() -> new RuntimeException("Admin role not found in DB"));
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

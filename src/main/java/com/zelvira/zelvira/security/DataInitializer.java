package com.zelvira.zelvira.security;

import com.zelvira.zelvira.entity.Role;
import com.zelvira.zelvira.entity.SeoConfig;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.LocationRepository;
import com.zelvira.zelvira.repository.RoleRepository;
import com.zelvira.zelvira.repository.SeoConfigRepository;
import com.zelvira.zelvira.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SeoConfigRepository seoConfigRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public DataInitializer(UserRepository userRepository,
            RoleRepository roleRepository,
            SeoConfigRepository seoConfigRepository,
            LocationRepository locationRepository,
            PasswordEncoder passwordEncoder,
            DataSource dataSource) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.seoConfigRepository = seoConfigRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
    }

    /** Password used only when the admin account is first created. */
    @Value("${app.admin.initial-password:password}")
    private String adminInitialPassword;

    @Override
    public void run(String... args) {
        log.info("Initializing security and master data...");

        // 1. Initialize Roles
        Role adminRole = createRoleIfNotFound("ROLE_ADMIN");
        Role userRole = createRoleIfNotFound("ROLE_USER");
        createRoleIfNotFound("ROLE_CUSTOMER_SERVICE");
        createRoleIfNotFound("ROLE_SHIPPING");

        // 2. Initialize Admin User (only set password on first creation, never overwrite existing password)
        userRepository.findByEmail("admin@zelvira.com").ifPresentOrElse(
                admin -> {
                    if (!Boolean.TRUE.equals(admin.getIsActive())) {
                        admin.setIsActive(true);
                        userRepository.save(admin);
                    }
                    log.info("Admin user already exists, leaving its password untouched: admin@zelvira.com");
                },
                () -> {
                    User admin = User.builder()
                            .email("admin@zelvira.com")
                            .password(passwordEncoder.encode(adminInitialPassword))
                            .fullName("System Administrator")
                            .isActive(true)
                            .roles(Set.of(adminRole, userRole))
                            .build();
                    userRepository.save(admin);
                    log.info("Created default admin user: admin@zelvira.com");
                });

        // 3. Initialize Global SEO
        if (seoConfigRepository.findByPageName("GLOBAL").isEmpty()) {
            SeoConfig globalSeo = SeoConfig.builder()
                    .pageName("GLOBAL")
                    .scriptCode("<!-- Global SEO Script -->")
                    .build();
            seoConfigRepository.save(globalSeo);
            log.info("Created default GLOBAL SEO configuration");
        }

        // 4. Seed Locations if empty
        seedLocationsIfEmpty();
    }

    private Role createRoleIfNotFound(String name) {
        Optional<Role> role = roleRepository.findByName(name);
        if (role.isEmpty()) {
            Role newRole = Role.builder().name(name).build();
            log.info("Creating role: {}", name);
            return roleRepository.save(newRole);
        }
        return role.get();
    }

    private void seedLocationsIfEmpty() {
        try {
            long count = locationRepository.count();
            if (count == 0) {
                ClassPathResource resource = new ClassPathResource("cmn_location.sql");
                if (resource.exists()) {
                    log.info("Locations table is empty. Seeding locations from cmn_location.sql...");
                    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
                    populator.setContinueOnError(true);
                    populator.execute(dataSource);
                    log.info("Locations seeding completed! Total locations loaded: {}", locationRepository.count());
                }
            }
        } catch (Exception e) {
            log.error("Failed to seed locations: {}", e.getMessage());
        }
    }
}

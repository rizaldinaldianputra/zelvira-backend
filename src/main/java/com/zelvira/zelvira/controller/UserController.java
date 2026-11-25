package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.entity.Role;
import com.zelvira.zelvira.repository.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final com.zelvira.zelvira.repository.SystemRoleRepository systemRoleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, RoleRepository roleRepository,
            com.zelvira.zelvira.repository.SystemRoleRepository systemRoleRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.systemRoleRepository = systemRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userRepository.findAll(PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().build();
        }

        java.util.Set<Role> roles = new java.util.HashSet<>();
        if (request.getRoleNames() != null) {
            for (String roleName : request.getRoleNames()) {
                roleRepository.findByName(roleName).ifPresent(roles::add);
            }
        }
        if (roles.isEmpty()) {
            roleRepository.findByName("ROLE_USER").ifPresent(roles::add);
        }

        com.zelvira.zelvira.entity.SystemRole systemRole = null;
        if (request.getSystemRoleId() != null) {
            systemRole = systemRoleRepository.findById(request.getSystemRoleId()).orElse(null);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .taskGroup(request.getTaskGroup())
                .systemRole(systemRole)
                .roles(roles)
                .isActive(true)
                .build();

        return ResponseEntity.ok(userRepository.save(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/roles-groups")
    public ResponseEntity<User> updateRolesAndGroups(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleGroupRequest request) {

        return userRepository.findById(id).map(user -> {
            user.setTaskGroup(request.getTaskGroup());
            if (request.getSystemRoleId() != null) {
                if (request.getSystemRoleId() > 0) {
                    systemRoleRepository.findById(request.getSystemRoleId()).ifPresent(user::setSystemRole);
                } else {
                    user.setSystemRole(null);
                }
            }
            if (request.getRoleNames() != null) {
                java.util.Set<Role> newRoles = new java.util.HashSet<>();
                for (String roleName : request.getRoleNames()) {
                    roleRepository.findByName(roleName).ifPresent(newRoles::add);
                }
                user.setRoles(newRoles);
            }
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {

        return userRepository.findById(id).map(user -> {
            if (request.getFullName() != null) {
                user.setFullName(request.getFullName());
            }
            if (request.getPhoneNumber() != null) {
                user.setPhoneNumber(request.getPhoneNumber());
            }
            if (request.getTaskGroup() != null) {
                user.setTaskGroup(request.getTaskGroup());
            }
            if (request.getSystemRoleId() != null) {
                if (request.getSystemRoleId() > 0) {
                    systemRoleRepository.findById(request.getSystemRoleId()).ifPresent(user::setSystemRole);
                } else {
                    user.setSystemRole(null);
                }
            }
            if (request.getIsActive() != null) {
                user.setIsActive(request.getIsActive());
            }
            if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            if (request.getRoleNames() != null) {
                java.util.Set<Role> newRoles = new java.util.HashSet<>();
                for (String roleName : request.getRoleNames()) {
                    roleRepository.findByName(roleName).ifPresent(newRoles::add);
                }
                user.setRoles(newRoles);
            }
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @lombok.Data
    public static class UpdateUserRequest {
        private String fullName;
        private String phoneNumber;
        private String taskGroup;
        private Long systemRoleId;
        private Boolean isActive;
        private String password;
        private List<String> roleNames;
    }

    @lombok.Data
    public static class CreateUserRequest {
        private String email;
        private String password;
        private String fullName;
        private String taskGroup;
        private Long systemRoleId;
        private List<String> roleNames;
    }

    @lombok.Data
    public static class UpdateUserRoleGroupRequest {
        private String taskGroup;
        private Long systemRoleId;
        private List<String> roleNames;
    }
}

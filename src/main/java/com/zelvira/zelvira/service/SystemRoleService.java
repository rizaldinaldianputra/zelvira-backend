package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.MenuDefinitionResponse;
import com.zelvira.zelvira.dto.RoleMenuPermissionDto;
import com.zelvira.zelvira.dto.SystemRoleRequest;
import com.zelvira.zelvira.dto.SystemRoleResponse;
import com.zelvira.zelvira.entity.RoleMenuPermission;
import com.zelvira.zelvira.entity.SystemRole;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.RoleMenuPermissionRepository;
import com.zelvira.zelvira.repository.SystemRoleRepository;
import com.zelvira.zelvira.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemRoleService {

    private final SystemRoleRepository systemRoleRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final UserRepository userRepository;

    public static final List<MenuDefinitionResponse> STANDARD_MENUS = List.of(
            // Overview
            MenuDefinitionResponse.builder().group("Overview").menuKey("dashboard").menuTitle("Dashboard").defaultPath("/admin").build(),
            
            // Sales & Products
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("order_tasks").menuTitle("Order Tasks").defaultPath("/admin/orders/tasks").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("orders").menuTitle("All Orders").defaultPath("/admin/orders").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("products").menuTitle("All Products").defaultPath("/admin/products").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("digital_products").menuTitle("Digital Products").defaultPath("/admin/products/digital").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("categories").menuTitle("Categories").defaultPath("/admin/categories").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("brands").menuTitle("Brands").defaultPath("/admin/brands").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("stock").menuTitle("Inventory & Stock").defaultPath("/admin/stock").build(),
            MenuDefinitionResponse.builder().group("Sales & Products").menuKey("reviews").menuTitle("Product Reviews").defaultPath("/admin/reviews").build(),

            // Ecommerce Configuration
            MenuDefinitionResponse.builder().group("Ecommerce Configuration").menuKey("tiktok_shop").menuTitle("TikTok Shop").defaultPath("/admin/integrations/tiktok").build(),
            MenuDefinitionResponse.builder().group("Ecommerce Configuration").menuKey("shopee").menuTitle("Shopee Integration").defaultPath("/admin/integrations/shopee").build(),
            MenuDefinitionResponse.builder().group("Ecommerce Configuration").menuKey("digiflazz").menuTitle("Digiflazz PPOB").defaultPath("/admin/integrations/digiflazz").build(),
            MenuDefinitionResponse.builder().group("Ecommerce Configuration").menuKey("integrations").menuTitle("All Integrations").defaultPath("/admin/integrations").build(),

            // Procurement & Supply
            MenuDefinitionResponse.builder().group("Procurement & Supply").menuKey("suppliers").menuTitle("Suppliers").defaultPath("/admin/suppliers").build(),
            MenuDefinitionResponse.builder().group("Procurement & Supply").menuKey("purchases").menuTitle("Purchase Orders").defaultPath("/admin/purchases").build(),
            MenuDefinitionResponse.builder().group("Procurement & Supply").menuKey("receipts").menuTitle("Goods Receipts").defaultPath("/admin/receipts").build(),

            // Users & Customers
            MenuDefinitionResponse.builder().group("Users & Customers").menuKey("users").menuTitle("All Users").defaultPath("/admin/users").build(),
            MenuDefinitionResponse.builder().group("Users & Customers").menuKey("roles").menuTitle("Role & Access").defaultPath("/admin/roles").build(),
            MenuDefinitionResponse.builder().group("Users & Customers").menuKey("customers").menuTitle("Customers Only").defaultPath("/admin/customers").build(),

            // Pusat Chat & CS (Omnichannel)
            MenuDefinitionResponse.builder().group("Pusat Chat & CS").menuKey("support").menuTitle("Customer Chat").defaultPath("/admin/support").build(),
            MenuDefinitionResponse.builder().group("Pusat Chat & CS").menuKey("tiktok_chat").menuTitle("TikTok Chat").defaultPath("/admin/chat/tiktok").build(),
            MenuDefinitionResponse.builder().group("Pusat Chat & CS").menuKey("shopee_chat").menuTitle("Shopee Chat").defaultPath("/admin/chat/shopee").build(),

            // Marketing & Content
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("vouchers").menuTitle("Vouchers & Discounts").defaultPath("/admin/vouchers").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("search").menuTitle("Search Trends").defaultPath("/admin/search").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("content_web").menuTitle("Website Content").defaultPath("/admin/content/web").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("content_mobile").menuTitle("Mobile App Content").defaultPath("/admin/content/mobile").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("news").menuTitle("News & Articles").defaultPath("/admin/news").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("faq").menuTitle("FAQ Management").defaultPath("/admin/support/faq").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("help").menuTitle("Help Center").defaultPath("/admin/support/help").build(),
            MenuDefinitionResponse.builder().group("Marketing & Content").menuKey("privacy").menuTitle("Privacy Policy").defaultPath("/admin/support/privacy").build(),

            // Analytics & Settings
            MenuDefinitionResponse.builder().group("Analytics & Settings").menuKey("settings").menuTitle("Admin Configuration").defaultPath("/admin/settings").build(),
            MenuDefinitionResponse.builder().group("Analytics & Settings").menuKey("organization").menuTitle("Organization Config").defaultPath("/admin/settings/organization").build(),
            MenuDefinitionResponse.builder().group("Analytics & Settings").menuKey("general_settings").menuTitle("App System Settings").defaultPath("/admin/settings/general").build(),
            MenuDefinitionResponse.builder().group("Analytics & Settings").menuKey("seo").menuTitle("SEO Settings").defaultPath("/admin/settings/seo").build()
    );

    public List<MenuDefinitionResponse> getMenuDefinitions() {
        return STANDARD_MENUS;
    }

    @Transactional(readOnly = true)
    public List<SystemRoleResponse> getAllRoles() {
        List<SystemRole> roles = systemRoleRepository.findAll();
        return roles.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SystemRoleResponse getRoleById(Long id) {
        SystemRole role = systemRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role tidak ditemukan dengan id: " + id));
        return mapToResponse(role);
    }

    @Transactional
    public SystemRoleResponse createRole(SystemRoleRequest request) {
        String cleanName = request.getName().trim().toUpperCase();
        if (systemRoleRepository.existsByName(cleanName)) {
            throw new IllegalArgumentException("Nama role '" + cleanName + "' sudah terdaftar.");
        }

        SystemRole role = SystemRole.builder()
                .name(cleanName)
                .description(request.getDescription())
                .isSystem(false)
                .permissions(new ArrayList<>())
                .build();

        if (request.getPermissions() != null) {
            for (RoleMenuPermissionDto permDto : request.getPermissions()) {
                RoleMenuPermission perm = RoleMenuPermission.builder()
                        .role(role)
                        .menuKey(permDto.getMenuKey())
                        .menuTitle(permDto.getMenuTitle())
                        .canView(Boolean.TRUE.equals(permDto.getCanView()))
                        .canRead(Boolean.TRUE.equals(permDto.getCanRead()))
                        .canWrite(Boolean.TRUE.equals(permDto.getCanWrite()))
                        .build();
                role.getPermissions().add(perm);
            }
        }

        SystemRole saved = systemRoleRepository.save(role);
        log.info("Created new SystemRole: {} with {} permissions", saved.getName(), saved.getPermissions().size());
        return mapToResponse(saved);
    }

    @Transactional
    public SystemRoleResponse updateRole(Long id, SystemRoleRequest request) {
        SystemRole role = systemRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role tidak ditemukan dengan id: " + id));

        String cleanName = request.getName().trim().toUpperCase();
        if (systemRoleRepository.existsByNameAndIdNot(cleanName, id)) {
            throw new IllegalArgumentException("Nama role '" + cleanName + "' sudah digunakan oleh role lain.");
        }

        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            role.setName(cleanName);
        }
        role.setDescription(request.getDescription());

        // Replace permissions
        role.getPermissions().clear();
        if (request.getPermissions() != null) {
            for (RoleMenuPermissionDto permDto : request.getPermissions()) {
                RoleMenuPermission perm = RoleMenuPermission.builder()
                        .role(role)
                        .menuKey(permDto.getMenuKey())
                        .menuTitle(permDto.getMenuTitle())
                        .canView(Boolean.TRUE.equals(permDto.getCanView()))
                        .canRead(Boolean.TRUE.equals(permDto.getCanRead()))
                        .canWrite(Boolean.TRUE.equals(permDto.getCanWrite()))
                        .build();
                role.getPermissions().add(perm);
            }
        }

        SystemRole updated = systemRoleRepository.save(role);
        log.info("Updated SystemRole: {}", updated.getName());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteRole(Long id) {
        SystemRole role = systemRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role tidak ditemukan dengan id: " + id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalStateException("Role sistem bawaan tidak dapat dihapus.");
        }

        long assignedUsers = userRepository.countBySystemRoleId(id);
        if (assignedUsers > 0) {
            throw new IllegalStateException("Role tidak dapat dihapus karena masih digunakan oleh " + assignedUsers + " pengguna.");
        }

        systemRoleRepository.delete(role);
        log.info("Deleted SystemRole: {}", role.getName());
    }

    public SystemRoleResponse mapToResponse(SystemRole role) {
        int userCount = (int) userRepository.countBySystemRoleId(role.getId());

        List<RoleMenuPermissionDto> permissions = role.getPermissions().stream()
                .map(p -> RoleMenuPermissionDto.builder()
                        .id(p.getId())
                        .menuKey(p.getMenuKey())
                        .menuTitle(p.getMenuTitle())
                        .canView(p.getCanView())
                        .canRead(p.getCanRead())
                        .canWrite(p.getCanWrite())
                        .build())
                .collect(Collectors.toList());

        return SystemRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .userCount(userCount)
                .permissions(permissions)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

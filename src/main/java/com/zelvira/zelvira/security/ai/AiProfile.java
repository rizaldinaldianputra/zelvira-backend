package com.zelvira.zelvira.security.ai;

import java.util.EnumSet;
import java.util.Set;

public enum AiProfile {
    HERMES_CEO("hermes-ceo", EnumSet.of(
            AiPermission.READ_PRODUCTS,
            AiPermission.READ_CATEGORIES,
            AiPermission.READ_BRAND,
            AiPermission.READ_DASHBOARD,
            AiPermission.READ_SALES,
            AiPermission.READ_ORDERS
    )),
    HERMES_CONTENT("hermes-content", EnumSet.of(
            AiPermission.READ_PRODUCTS,
            AiPermission.READ_IMAGES,
            AiPermission.READ_CATEGORIES,
            AiPermission.READ_BRAND
    )),
    HERMES_MARKETING("hermes-marketing", EnumSet.of(
            AiPermission.READ_PRODUCTS,
            AiPermission.READ_PRICING,
            AiPermission.READ_CATEGORIES,
            AiPermission.READ_BRAND,
            AiPermission.READ_SALES
    )),
    HERMES_SEO("hermes-seo", EnumSet.of(
            AiPermission.READ_PRODUCTS,
            AiPermission.READ_CATEGORIES,
            AiPermission.READ_BRAND
    )),
    HERMES_ADMIN("hermes-admin", EnumSet.of(
            AiPermission.READ_PRODUCTS,
            AiPermission.READ_CATEGORIES,
            AiPermission.READ_BRAND,
            AiPermission.READ_DASHBOARD,
            AiPermission.READ_ORDERS,
            AiPermission.VALIDATE_DATA
    ));

    private final String id;
    private final Set<AiPermission> permissions;

    AiProfile(String id, Set<AiPermission> permissions) {
        this.id = id;
        this.permissions = permissions;
    }

    public String getId() {
        return id;
    }

    public Set<AiPermission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(AiPermission permission) {
        return permissions.contains(permission);
    }

    public static AiProfile fromId(String id) {
        if (id == null) return null;
        for (AiProfile p : values()) {
            if (p.id.equalsIgnoreCase(id.trim())) {
                return p;
            }
        }
        return null;
    }
}

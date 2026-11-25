package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "system_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "permissions")
@EqualsAndHashCode(exclude = "permissions")
public class SystemRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<RoleMenuPermission> permissions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addPermission(RoleMenuPermission perm) {
        permissions.add(perm);
        perm.setRole(this);
    }

    public void removePermission(RoleMenuPermission perm) {
        permissions.remove(perm);
        perm.setRole(null);
    }
}

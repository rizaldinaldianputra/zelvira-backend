package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_menu_permissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"role_id", "menu_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "role")
@EqualsAndHashCode(exclude = "role")
public class RoleMenuPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    @JsonIgnore
    private SystemRole role;

    @Column(name = "menu_key", nullable = false, length = 100)
    private String menuKey;

    @Column(name = "menu_title", nullable = false, length = 100)
    private String menuTitle;

    @Column(name = "can_view", nullable = false)
    @Builder.Default
    private Boolean canView = false;

    @Column(name = "can_read", nullable = false)
    @Builder.Default
    private Boolean canRead = false;

    @Column(name = "can_write", nullable = false)
    @Builder.Default
    private Boolean canWrite = false;
}

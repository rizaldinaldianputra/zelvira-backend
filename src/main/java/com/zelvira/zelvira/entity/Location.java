package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "create_by", length = 50)
    private String createBy;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(length = 20)
    private String status;

    @Column(name = "update_by", length = 50)
    private String updateBy;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(length = 50)
    private String code;

    @Column(name = "code_location", length = 50)
    private String codeLocation;

    @Column(name = "\"level\"")
    private Integer level;

    @Column(length = 255)
    private String location;

    @Column(length = 255)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    public Location() {
        super();
    }

    public Location(Long id, String createBy, LocalDateTime createDate, String status, String updateBy,
            LocalDateTime updateDate, String code, String codeLocation, Integer level,
            String location, String name, Long parentId) {
        super();
        this.id = id;
        this.createBy = createBy;
        this.createDate = createDate;
        this.status = status;
        this.updateBy = updateBy;
        this.updateDate = updateDate;
        this.code = code;
        this.codeLocation = codeLocation;
        this.level = level;
        this.location = location;
        this.name = name;
        this.parentId = parentId;
    }

    public static LocationBuilder builder() {
        return new LocationBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCodeLocation() {
        return codeLocation;
    }

    public void setCodeLocation(String codeLocation) {
        this.codeLocation = codeLocation;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public static class LocationBuilder {
        private Long id;
        private String createBy;
        private LocalDateTime createDate;
        private String status;
        private String updateBy;
        private LocalDateTime updateDate;
        private String code;
        private String codeLocation;
        private Integer level;
        private String location;
        private String name;
        private Long parentId;

        LocationBuilder() {
        }

        public LocationBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public LocationBuilder createBy(String createBy) {
            this.createBy = createBy;
            return this;
        }

        public LocationBuilder createDate(LocalDateTime createDate) {
            this.createDate = createDate;
            return this;
        }

        public LocationBuilder status(String status) {
            this.status = status;
            return this;
        }

        public LocationBuilder updateBy(String updateBy) {
            this.updateBy = updateBy;
            return this;
        }

        public LocationBuilder updateDate(LocalDateTime updateDate) {
            this.updateDate = updateDate;
            return this;
        }

        public LocationBuilder code(String code) {
            this.code = code;
            return this;
        }

        public LocationBuilder codeLocation(String codeLocation) {
            this.codeLocation = codeLocation;
            return this;
        }

        public LocationBuilder level(Integer level) {
            this.level = level;
            return this;
        }

        public LocationBuilder location(String location) {
            this.location = location;
            return this;
        }

        public LocationBuilder name(String name) {
            this.name = name;
            return this;
        }

        public LocationBuilder parentId(Long parentId) {
            this.parentId = parentId;
            return this;
        }

        public Location build() {
            return new Location(id, createBy, createDate, status, updateBy, updateDate,
                    code, codeLocation, level, location, name, parentId);
        }
    }
}

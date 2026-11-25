package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByLevelOrderByNameAsc(Integer level);

    List<Location> findByParentIdOrderByNameAsc(Long parentId);
}

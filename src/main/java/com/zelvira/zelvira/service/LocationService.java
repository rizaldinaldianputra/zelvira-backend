package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.Location;
import com.zelvira.zelvira.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<Location> getProvinces() {
        return locationRepository.findByLevelOrderByNameAsc(2);
    }

    public List<Location> getCities(Long provinceId) {
        return locationRepository.findByParentIdOrderByNameAsc(provinceId);
    }

    public List<Location> getDistricts(Long cityId) {
        return locationRepository.findByParentIdOrderByNameAsc(cityId);
    }

    public List<Location> getSubdistricts(Long districtId) {
        return locationRepository.findByParentIdOrderByNameAsc(districtId);
    }

    public Location getLocationById(Long id) {
        return locationRepository.findById(id).orElse(null);
    }
}

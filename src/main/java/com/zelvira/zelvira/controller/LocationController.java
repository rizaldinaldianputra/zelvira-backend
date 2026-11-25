package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.Location;
import com.zelvira.zelvira.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/provinces")
    public ResponseEntity<List<Location>> getProvinces() {
        return ResponseEntity.ok(locationService.getProvinces());
    }

    @GetMapping("/cities/{provinceId}")
    public ResponseEntity<List<Location>> getCities(@PathVariable Long provinceId) {
        return ResponseEntity.ok(locationService.getCities(provinceId));
    }

    @GetMapping("/districts/{cityId}")
    public ResponseEntity<List<Location>> getDistricts(@PathVariable Long cityId) {
        return ResponseEntity.ok(locationService.getDistricts(cityId));
    }

    @GetMapping("/subdistricts/{districtId}")
    public ResponseEntity<List<Location>> getSubdistricts(@PathVariable Long districtId) {
        return ResponseEntity.ok(locationService.getSubdistricts(districtId));
    }
}

package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ShippingCourierResponse;
import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.service.ShippingService;
import com.zelvira.zelvira.entity.Location;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shipping")
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<Object>>> getProvinces() {
        return ResponseEntity.ok(ApiResponse.success("Provinces fetched", shippingService.getProvinces()));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<Object>>> getCities(@RequestParam String provinceId) {
        return ResponseEntity.ok(ApiResponse.success("Cities fetched", shippingService.getCities(provinceId)));
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<Object>>> getDistricts(@RequestParam String cityId) {
        return ResponseEntity.ok(ApiResponse.success("Districts fetched", shippingService.getDistricts(cityId)));
    }

    @GetMapping("/sub-districts")
    public ResponseEntity<ApiResponse<List<Object>>> getSubdistricts(@RequestParam String districtId) {
        return ResponseEntity
                .ok(ApiResponse.success("Subdistricts fetched", shippingService.getSubdistricts(districtId)));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncLocations() {
        return ResponseEntity.ok(ApiResponse.success("Sinkronisasi data wilayah berhasil",
                shippingService.syncLocationsFromRajaOngkir()));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLocationStats() {
        return ResponseEntity.ok(ApiResponse.success("Location stats fetched", shippingService.getLocationStats()));
    }

    @GetMapping("/all-provinces")
    public ResponseEntity<ApiResponse<List<Location>>> getAllProvinces() {
        return ResponseEntity.ok(ApiResponse.success("All provinces fetched", shippingService.getAllProvinces()));
    }

    @GetMapping("/all-cities")
    public ResponseEntity<ApiResponse<List<Location>>> getAllCities() {
        return ResponseEntity.ok(ApiResponse.success("All cities fetched", shippingService.getAllCities()));
    }

    @PostMapping("/cost")
    public ResponseEntity<ApiResponse<List<ShippingCourierResponse>>> calculateCost(
            @RequestBody Map<String, Object> payload) {
        String origin = payload.get("origin").toString();
        String destination = payload.get("destination").toString();
        int weight = Integer.parseInt(payload.get("weight").toString());
        String courier = payload.get("courier").toString();

        return ResponseEntity.ok(ApiResponse.success("Shipping cost calculated",
                shippingService.calculateCost(origin, destination, weight, courier)));
    }

    @DeleteMapping("/clear-bad-data")
    public ResponseEntity<ApiResponse<String>> clearBadData() {
        shippingService.clearBadData();
        return ResponseEntity.ok(ApiResponse.success("Data cleared", "OK"));
    }

    @PostMapping("/track")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trackWaybill(
            @RequestParam String awb,
            @RequestParam String courier,
            @RequestParam(required = false) String phone) {
        return ResponseEntity.ok(ApiResponse.success("Tracking info fetched",
                shippingService.trackWaybill(awb, courier, phone)));
    }

    @GetMapping("/search/domestic")
    public ResponseEntity<ApiResponse<List<Object>>> searchDomesticDestination(@RequestParam String search) {
        return ResponseEntity.ok(ApiResponse.success("Destinations fetched",
                shippingService.searchDomesticDestination(search)));
    }

    @GetMapping("/search/international")
    public ResponseEntity<ApiResponse<List<Object>>> searchInternationalDestination(@RequestParam String search) {
        return ResponseEntity.ok(ApiResponse.success("Destinations fetched",
                shippingService.searchInternationalDestination(search)));
    }
}

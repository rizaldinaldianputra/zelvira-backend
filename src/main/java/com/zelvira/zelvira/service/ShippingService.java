package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.Location;
import com.zelvira.zelvira.repository.LocationRepository;
import com.zelvira.zelvira.repository.AddressRepository;
import com.zelvira.zelvira.dto.ShippingCourierResponse;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ShippingService {

    private final LocationRepository locationRepository;
    private final AddressRepository addressRepository;

    public ShippingService(LocationRepository locationRepository, AddressRepository addressRepository) {
        this.locationRepository = locationRepository;
        this.addressRepository = addressRepository;
    }

    @Value("${app.rajaongkir.api-key}")
    private String apiKey;

    // Base URL should ideally be https://rajaongkir.komerce.id/api/v1
    @Value("${app.rajaongkir.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // Level constants: 1=Negara, 2=Provinsi, 3=Kab/Kota, 4=Kecamatan, 5=Kelurahan
    private static final int LEVEL_PROVINCE = 2;
    private static final int LEVEL_CITY = 3;
    private static final int LEVEL_DISTRICT = 4;
    private static final int LEVEL_SUBDISTRICT = 5;

    private String getApiBaseUrl() {
        if (baseUrl.contains("/calculate/")) {
            return baseUrl.substring(0, baseUrl.indexOf("/calculate"));
        }
        return baseUrl;
    }

    public List<Object> getProvinces() {
        List<Location> list = locationRepository.findByLevelOrderByNameAsc(LEVEL_PROVINCE);
        List<Object> result = new ArrayList<>();
        for (Location loc : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("province_id", loc.getId().toString());
            map.put("province", loc.getName());
            map.put("code", loc.getCode());
            result.add(map);
        }
        return result;
    }

    public List<Object> getCities(String provinceId) {
        List<Location> list = locationRepository.findByParentIdOrderByNameAsc(Long.parseLong(provinceId));
        List<Object> result = new ArrayList<>();
        for (Location loc : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("city_id", loc.getId().toString());
            map.put("province_id", provinceId);
            // Detect kota vs kabupaten by name prefix
            String cityName = loc.getName();
            String type = "Kabupaten";
            if (cityName.startsWith("Kota ")) {
                type = "Kota";
                cityName = cityName.substring(5);
            } else if (cityName.startsWith("Kab. ")) {
                cityName = cityName.substring(5);
            }
            map.put("city_name", cityName);
            map.put("type", type);
            map.put("postal_code", loc.getCode() != null ? loc.getCode() : "");
            result.add(map);
        }
        return result;
    }

    public List<Object> getDistricts(String cityId) {
        List<Location> list = locationRepository.findByParentIdOrderByNameAsc(Long.parseLong(cityId));
        List<Object> result = new ArrayList<>();
        for (Location loc : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("district_id", loc.getId().toString());
            map.put("city_id", cityId);
            // Clean "- Kota" suffix if present
            String name = loc.getName().replaceAll(" - Kota$", "").replaceAll(" - Kab$", "").trim();
            map.put("district_name", name);
            map.put("code_location", loc.getCodeLocation() != null ? loc.getCodeLocation() : "");
            result.add(map);
        }
        return result;
    }

    public List<Object> getSubdistricts(String districtId) {
        List<Location> list = locationRepository.findByParentIdOrderByNameAsc(Long.parseLong(districtId));
        List<Object> result = new ArrayList<>();
        for (Location loc : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("subdistrict_id", loc.getId().toString());
            map.put("district_id", districtId);
            map.put("subdistrict_name", loc.getName());
            map.put("code_location", loc.getCodeLocation() != null ? loc.getCodeLocation() : "");
            result.add(map);
        }
        return result;
    }

    public Map<String, Object> getLocationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_records", locationRepository.count());
        stats.put("total_provinces", locationRepository.findByLevelOrderByNameAsc(LEVEL_PROVINCE).size());
        stats.put("total_cities", locationRepository.findByLevelOrderByNameAsc(LEVEL_CITY).size());
        stats.put("total_districts", locationRepository.findByLevelOrderByNameAsc(LEVEL_DISTRICT).size());
        stats.put("total_subdistricts", locationRepository.findByLevelOrderByNameAsc(LEVEL_SUBDISTRICT).size());
        return stats;
    }

    public List<Location> getAllProvinces() {
        return locationRepository.findByLevelOrderByNameAsc(LEVEL_PROVINCE);
    }

    public List<Location> getAllCities() {
        return locationRepository.findByLevelOrderByNameAsc(LEVEL_CITY);
    }

    public Map<String, Object> syncLocationsFromRajaOngkir() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message",
                "Data wilayah sudah tersedia dari tabel locations (seeded dari SQL). Sync tidak diperlukan.");
        response.put("total_provinces", locationRepository.findByLevelOrderByNameAsc(LEVEL_PROVINCE).size());
        response.put("total_cities", locationRepository.findByLevelOrderByNameAsc(LEVEL_CITY).size());
        return response;
    }

    public Map<String, Object> loadSeedDataIfEmpty() {
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Data wilayah dimuat dari tabel locations. Total: " + locationRepository.count());
        return res;
    }

    @Transactional
    public void clearBadData() {
        addressRepository.deleteAll();
    }

    public List<ShippingCourierResponse> calculateCost(String origin, String destination,
            int weight, String courier) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Key", apiKey);
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            String body = "origin=" + origin +
                    "&destination=" + destination +
                    "&weight=" + weight +
                    "&courier=" + courier +
                    "&price=lowest";

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            String costUrl = getApiBaseUrl() + "/calculate/district/domestic-cost";

            ResponseEntity<String> response = restTemplate.postForEntity(costUrl, entity, String.class);
            JSONObject jsonResponse = new JSONObject(response.getBody());

            List<ShippingCourierResponse> result = new ArrayList<>();
            if (jsonResponse.has("data")) {
                JSONArray data = jsonResponse.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject obj = data.getJSONObject(i);
                    result.add(ShippingCourierResponse.builder()
                            .code(obj.optString("code"))
                            .name(obj.optString("name"))
                            .service(obj.optString("service"))
                            .description(obj.optString("description"))
                            .cost(obj.optDouble("cost", 0.0))
                            .etd(obj.optString("etd"))
                            .build());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to calculate shipping cost from {} to {}: {}", origin, destination, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Object> fetchFromKomerce(String endpoint) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = getApiBaseUrl() + endpoint;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JSONObject jsonResponse = new JSONObject(response.getBody());

            if (jsonResponse.has("data")) {
                Object data = jsonResponse.get("data");
                if (data instanceof JSONArray) {
                    return ((JSONArray) data).toList();
                } else if (data instanceof JSONObject) {
                    List<Object> list = new ArrayList<>();
                    list.add(((JSONObject) data).toMap());
                    return list;
                }
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to fetch from Komerce {}: {}", endpoint, e.getMessage());
            return new ArrayList<>();
        }
    }

    public Map<String, Object> trackWaybill(String awbNumber, String courier) {
        return trackWaybill(awbNumber, courier, null);
    }

    public Map<String, Object> trackWaybill(String awbNumber, String courier, String phone) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String courierCode = courier != null ? courier.trim().toLowerCase() : "jne";
            if (courierCode.contains("jne")) courierCode = "jne";
            else if (courierCode.contains("sicepat")) courierCode = "sicepat";
            else if (courierCode.contains("j&t") || courierCode.contains("jnt")) courierCode = "jnt";
            else if (courierCode.contains("pos")) courierCode = "pos";
            else if (courierCode.contains("tiki")) courierCode = "tiki";
            else if (courierCode.contains("anteraja")) courierCode = "anteraja";

            StringBuilder urlBuilder = new StringBuilder(getApiBaseUrl())
                    .append("/track/waybill?awb=").append(awbNumber.trim())
                    .append("&courier=").append(courierCode);

            if (phone != null && !phone.trim().isEmpty()) {
                String cleanPhone = phone.replaceAll("\\D", "");
                String last5 = cleanPhone.length() >= 5 ? cleanPhone.substring(cleanPhone.length() - 5) : cleanPhone;
                if (!last5.isEmpty()) {
                    urlBuilder.append("&last_phone_number=").append(last5);
                }
            }

            ResponseEntity<String> response = restTemplate.exchange(urlBuilder.toString(), HttpMethod.POST, entity, String.class);
            return new JSONObject(response.getBody()).toMap();
        } catch (Exception e) {
            log.error("Failed to track waybill {}: {}", awbNumber, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    public List<Object> searchDomesticDestination(String searchLocation) {
        return fetchFromKomerce("/destination/domestic-destination?search=" + searchLocation + "&limit=100&offset=0");
    }

    public List<Object> searchInternationalDestination(String searchRegion) {
        return fetchFromKomerce(
                "/destination/international-destination?search=" + searchRegion + "&limit=100&offset=0");
    }
}

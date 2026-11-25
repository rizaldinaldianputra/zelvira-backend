package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.MobileAppSettingDTO;
import com.zelvira.zelvira.entity.MobileAppSetting;
import com.zelvira.zelvira.service.MobileAppSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/mobile")
public class MobileAppSettingController {

    private final MobileAppSettingService service;

    public MobileAppSettingController(MobileAppSettingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MobileAppSetting> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PostMapping
    public ResponseEntity<MobileAppSetting> updateSettings(@RequestBody MobileAppSettingDTO dto) {
        return ResponseEntity.ok(service.updateSettings(dto));
    }
}

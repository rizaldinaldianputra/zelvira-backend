package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.SeoConfigRequest;
import com.zelvira.zelvira.dto.SeoConfigResponse;
import com.zelvira.zelvira.entity.SeoConfig;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.SeoConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeoConfigService {

    private final SeoConfigRepository seoConfigRepository;

    public SeoConfigService(SeoConfigRepository seoConfigRepository) {
        this.seoConfigRepository = seoConfigRepository;
    }

    public List<SeoConfigResponse> getAll() {
        return seoConfigRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SeoConfigResponse getById(Long id) {
        return seoConfigRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("SEO config not found with id: " + id));
    }

    public SeoConfigResponse getByIdOrPageName(String identifier) {
        // Try parsing as numeric ID first
        try {
            Long id = Long.parseLong(identifier);
            return getById(id);
        } catch (NumberFormatException ignored) {
            // Identifier is a string page name
        }
        return getByPageName(identifier);
    }

    public SeoConfigResponse getByPageName(String pageName) {
        return seoConfigRepository.findByPageName(pageName)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("SEO config not found for page: " + pageName));
    }

    @Transactional
    public SeoConfigResponse save(SeoConfigRequest request) {
        String cleanPageName = request.getPageName().trim().toUpperCase();
        SeoConfig seoConfig = seoConfigRepository.findByPageName(cleanPageName)
                .orElse(new SeoConfig());

        populateEntity(seoConfig, request);
        seoConfig.setPageName(cleanPageName);

        return mapToResponse(seoConfigRepository.save(seoConfig));
    }

    @Transactional
    public SeoConfigResponse update(Long id, SeoConfigRequest request) {
        SeoConfig seoConfig = seoConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SEO config not found with id: " + id));

        populateEntity(seoConfig, request);
        if (request.getPageName() != null && !request.getPageName().trim().isEmpty()) {
            seoConfig.setPageName(request.getPageName().trim().toUpperCase());
        }

        return mapToResponse(seoConfigRepository.save(seoConfig));
    }

    private void populateEntity(SeoConfig seoConfig, SeoConfigRequest request) {
        seoConfig.setScriptCode(request.getScriptCode());
        if (request.getTargetType() != null) {
            seoConfig.setTargetType(request.getTargetType().toUpperCase());
        } else if (seoConfig.getTargetType() == null) {
            seoConfig.setTargetType("HEAD");
        }
        seoConfig.setMetaTitle(request.getMetaTitle());
        seoConfig.setMetaDescription(request.getMetaDescription());
        seoConfig.setMetaKeywords(request.getMetaKeywords());
        if (request.getIsActive() != null) {
            seoConfig.setIsActive(request.getIsActive());
        } else if (seoConfig.getIsActive() == null) {
            seoConfig.setIsActive(true);
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!seoConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("SEO config not found with id: " + id);
        }
        seoConfigRepository.deleteById(id);
    }

    private SeoConfigResponse mapToResponse(SeoConfig seoConfig) {
        return SeoConfigResponse.builder()
                .id(seoConfig.getId())
                .pageName(seoConfig.getPageName())
                .targetType(seoConfig.getTargetType() != null ? seoConfig.getTargetType() : "HEAD")
                .metaTitle(seoConfig.getMetaTitle())
                .metaDescription(seoConfig.getMetaDescription())
                .metaKeywords(seoConfig.getMetaKeywords())
                .scriptCode(seoConfig.getScriptCode())
                .isActive(seoConfig.getIsActive() != null ? seoConfig.getIsActive() : true)
                .build();
    }
}

package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.ContentSectionRequest;
import com.zelvira.zelvira.dto.ContentSectionResponse;
import com.zelvira.zelvira.entity.ContentSection;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ContentSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContentSectionService {

    private final ContentSectionRepository sectionRepository;
    private final ContentService contentService;

    public ContentSectionService(ContentSectionRepository sectionRepository, ContentService contentService) {
        this.sectionRepository = sectionRepository;
        this.contentService = contentService;
    }

    @Transactional
    public ContentSectionResponse createSection(ContentSectionRequest request) {
        ContentSection section = ContentSection.builder()
                .platform(request.getPlatform() != null ? request.getPlatform() : "WEB")
                .type(request.getType())
                .title(request.getTitle())
                .subtitle(request.getSubtitle())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        return mapToResponse(sectionRepository.save(section));
    }

    public List<ContentSectionResponse> getSectionsByPlatform(String platform) {
        return sectionRepository.findByPlatformOrderByDisplayOrderAsc(platform)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ContentSectionResponse getSectionById(Long id) {
        ContentSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + id));
        return mapToResponse(section);
    }

    @Transactional
    public ContentSectionResponse updateSection(Long id, ContentSectionRequest request) {
        ContentSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found with id: " + id));

        section.setPlatform(request.getPlatform() != null ? request.getPlatform() : section.getPlatform());
        section.setType(request.getType() != null ? request.getType() : section.getType());
        section.setTitle(request.getTitle());
        section.setSubtitle(request.getSubtitle());
        section.setDisplayOrder(
                request.getDisplayOrder() != null ? request.getDisplayOrder() : section.getDisplayOrder());
        section.setIsActive(request.getIsActive() != null ? request.getIsActive() : section.getIsActive());

        return mapToResponse(sectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(Long id) {
        if (!sectionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Section not found with id: " + id);
        }
        sectionRepository.deleteById(id);
    }

    public ContentSectionResponse mapToResponse(ContentSection section) {
        return ContentSectionResponse.builder()
                .id(section.getId())
                .platform(section.getPlatform())
                .type(section.getType())
                .title(section.getTitle())
                .subtitle(section.getSubtitle())
                .displayOrder(section.getDisplayOrder())
                .isActive(section.getIsActive())
                .items(section.getItems() != null ? section.getItems().stream()
                        .map(contentService::mapToItemResponse)
                        .collect(Collectors.toList()) : null)
                .build();
    }
}

package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.OrganizationRequest;
import com.zelvira.zelvira.entity.Organization;
import com.zelvira.zelvira.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization getOrganization() {
        return organizationRepository.findFirstByOrderByIdAsc()
                .orElse(Organization.builder()
                        .name("Zelvira")
                        .address("Jl. Contoh No. 123")
                        .email("info@zelvira.com")
                        .build());
    }

    @Transactional
    public Organization updateOrganization(OrganizationRequest request) {
        Organization organization = organizationRepository.findFirstByOrderByIdAsc()
                .orElse(new Organization());

        organization.setName(request.getName());
        organization.setAddress(request.getAddress());
        organization.setPhoneNumber(request.getPhoneNumber());
        organization.setEmail(request.getEmail());
        organization.setInstagramUrl(request.getInstagramUrl());
        organization.setFacebookUrl(request.getFacebookUrl());
        organization.setTwitterUrl(request.getTwitterUrl());
        organization.setYoutubeUrl(request.getYoutubeUrl());
        organization.setTiktokUrl(request.getTiktokUrl());
        organization.setPlaystoreUrl(request.getPlaystoreUrl());
        organization.setAppstoreUrl(request.getAppstoreUrl());
        organization.setLatitude(request.getLatitude());
        organization.setLongitude(request.getLongitude());
        organization.setLogoUrl(request.getLogoUrl());

        return organizationRepository.save(organization);
    }
}

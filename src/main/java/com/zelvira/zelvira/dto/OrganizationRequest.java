package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRequest {
    private String name;
    private String address;
    private String phoneNumber;
    private String email;
    private String instagramUrl;
    private String facebookUrl;
    private String twitterUrl;
    private String youtubeUrl;
    private String tiktokUrl;
    private String playstoreUrl;
    private String appstoreUrl;
    private Double latitude;
    private Double longitude;
    private String logoUrl;
}

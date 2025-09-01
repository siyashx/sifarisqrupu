package com.codesupreme.sifarisqrupu.dto.banner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerDto {

    private Long id;
    private Long userId;
    private String description;
    private Integer adTime;
    private String type;
    private String imageUrl;
    private String link;
    private String expiryDate;
    private Boolean isDisable;
    private String createdAt;
}


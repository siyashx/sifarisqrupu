package com.codesupreme.sifarisqrupu.dto.elan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElanDto {

    private Long id;
    private Long userId;
    private String title;
    private String description;
    private List<String> images;
    private Double price;
    private Boolean isDisable;
    private String createdAt;

}

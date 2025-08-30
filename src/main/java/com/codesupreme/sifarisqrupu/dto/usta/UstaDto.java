package com.codesupreme.sifarisqrupu.dto.usta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UstaDto {

    private Long id;
    private String name;
    private String phone;
    private String hours;
    private List<String> location;
    private Boolean isDisable;
    private String createdAt;

}

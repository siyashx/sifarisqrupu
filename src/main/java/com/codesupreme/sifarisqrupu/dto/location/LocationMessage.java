package com.codesupreme.sifarisqrupu.dto.location;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocationMessage {
    private Long userId;
    private double latitude;
    private double longitude;
}

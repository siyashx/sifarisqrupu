package com.codesupreme.sifarisqrupu.model.banner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "banners")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String description;
    private String link;
    private String expiryDate;
    private Boolean isDisable;
    private String createdAt;
}


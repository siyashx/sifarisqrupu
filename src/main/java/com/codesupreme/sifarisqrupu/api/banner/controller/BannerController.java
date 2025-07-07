package com.codesupreme.sifarisqrupu.api.banner.controller;

import com.codesupreme.sifarisqrupu.dto.banner.BannerDto;
import com.codesupreme.sifarisqrupu.service.impl.banner.BannerServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v5/banner")
public class BannerController {

    private final BannerServiceImpl service;

    public BannerController(BannerServiceImpl service) {
        this.service = service;
    }

    // Bütün bannerləri əldə et
    @GetMapping
    public ResponseEntity<List<BannerDto>> getAllBanners() {
        List<BannerDto> all = service.getAllBanners();
        return ResponseEntity.ok(all);
    }

    // ID-ə görə banner əldə et
    @GetMapping("/{bannerId}")
    public ResponseEntity<BannerDto> getBannerById(@PathVariable("bannerId") Long id) {
        BannerDto banner = service.getBannerById(id);
        if (banner != null) {
            return ResponseEntity.ok(banner);
        }
        return ResponseEntity.notFound().build();
    }

    // Yeni banner əlavə et
    @PostMapping
    public ResponseEntity<BannerDto> saveBanner(@RequestBody BannerDto dto) {
        BannerDto created = service.saveBanner(dto);
        return ResponseEntity.ok(created);
    }

    // Bannerı yenilə
    @PutMapping("/{bannerId}")
    public ResponseEntity<?> updateBanner(
            @PathVariable("bannerId") Long id,
            @RequestBody BannerDto bannerDto) {
        BannerDto updatedBanner = service.updateBanner(id, bannerDto);
        if (updatedBanner != null) {
            return ResponseEntity.ok(updatedBanner);
        }
        return ResponseEntity.notFound().build();
    }

    // Bannerı sil
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<String> deleteBanner(@PathVariable("bannerId") Long id) {
        Boolean deleted = service.deleteBanner(id);
        if (deleted) {
            return ResponseEntity.ok("Banner deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }
}


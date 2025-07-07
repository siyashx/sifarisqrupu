package com.codesupreme.sifarisqrupu.service.inter.banner;

import com.codesupreme.sifarisqrupu.dto.banner.BannerDto;

import java.util.List;

public interface BannerServiceInter {

    // Bütün bannnerləri əldə et
    List<BannerDto> getAllBanners();

    // ID-yə görə banner əldə et
    BannerDto getBannerById(Long id);

    // Yeni banner əlavə et
    BannerDto saveBanner(BannerDto dto);

    // Mövcud bannerı yenilə
    BannerDto updateBanner(Long bannerId, BannerDto bannerDto);

    // Bannerı sil
    Boolean deleteBanner(Long id);
}


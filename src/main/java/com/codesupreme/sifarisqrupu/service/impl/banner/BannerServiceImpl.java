package com.codesupreme.sifarisqrupu.service.impl.banner;

import com.codesupreme.sifarisqrupu.dao.banner.BannerRepository;
import com.codesupreme.sifarisqrupu.dto.banner.BannerDto;
import com.codesupreme.sifarisqrupu.model.banner.Banner;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BannerServiceImpl {

    private final BannerRepository bannerRepository;
    private final ModelMapper modelMapper;

    public BannerServiceImpl(BannerRepository bannerRepository, ModelMapper modelMapper) {
        this.bannerRepository = bannerRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<BannerDto> getAllBanners() {
        List<Banner> list = bannerRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, BannerDto.class))
                .toList();
    }

    //ById
    public BannerDto getBannerById(Long id) {
        Optional<Banner> optional = bannerRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, BannerDto.class)).orElse(null);
    }

    //Save
    public BannerDto saveBanner(BannerDto dto) {
        Banner banner = modelMapper.map(dto, Banner.class);
        banner = bannerRepository.save(banner);
        return modelMapper.map(banner, BannerDto.class);
    }

    //Update
    public BannerDto updateBanner(Long bannerId, BannerDto bannerDto) {
        Optional<Banner> optional = bannerRepository.findById(bannerId);
        if (optional.isPresent()) {
            Banner banner = optional.get();

            if (bannerDto.getDescription() != null) {
                banner.setDescription(bannerDto.getDescription());
            }

            if (bannerDto.getLink() != null) {
                banner.setLink(bannerDto.getLink());
            }

            if (bannerDto.getExpiryDate() != null) {
                banner.setExpiryDate(bannerDto.getExpiryDate());
            }

            if (bannerDto.getIsDisable() != null) {
                banner.setIsDisable(bannerDto.getIsDisable());
            }

            if (bannerDto.getCreatedAt() != null) {
                banner.setCreatedAt(bannerDto.getCreatedAt());
            }

            banner = bannerRepository.save(banner);

            return modelMapper.map(banner, BannerDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteBanner(Long id) {
        Optional<Banner> optional = bannerRepository.findById(id);
        if (optional.isPresent()) {
            Banner banner = optional.get();
            bannerRepository.delete(banner);
            return true;
        }
        return false;
    }
}

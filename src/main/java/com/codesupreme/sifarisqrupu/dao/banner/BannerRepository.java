package com.codesupreme.sifarisqrupu.dao.banner;

import com.codesupreme.sifarisqrupu.model.banner.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {
    // Burada əlavə sorğular əlavə edə bilərsiniz, məsələn:
    // List<Banner> findByUserId(Long userId);
}


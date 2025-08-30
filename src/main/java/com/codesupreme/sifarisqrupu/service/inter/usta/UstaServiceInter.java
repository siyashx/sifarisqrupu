package com.codesupreme.sifarisqrupu.service.inter.usta;

import com.codesupreme.sifarisqrupu.dto.usta.UstaDto;

import java.util.List;

public interface UstaServiceInter {

    // Bütün ustalari əldə et
    List<UstaDto> getAllUstas();

    // ID-yə görə usta əldə et
    UstaDto getUstaById(Long id);

    // Yeni usta əlavə et
    UstaDto saveUsta(UstaDto dto);

    // Mövcud ustanı yenilə
    UstaDto updateUsta(Long ustaId, UstaDto ustaDto);

    // Ustanı sil
    Boolean deleteUsta(Long id);
}

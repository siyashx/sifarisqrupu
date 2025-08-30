package com.codesupreme.sifarisqrupu.service.inter.elan;

import com.codesupreme.sifarisqrupu.dto.elan.ElanDto;

import java.util.List;

public interface ElanServiceInter {

    // Bütün elanlari əldə et
    List<ElanDto> getAllElans();

    // ID-yə görə elan əldə et
    ElanDto getElanById(Long id);

    // Yeni elan əlavə et
    ElanDto saveElan(ElanDto dto);

    // Mövcud elanı yenilə
    ElanDto updateElan(Long elanId, ElanDto elanDto);

    // Elanı sil
    Boolean deleteElan(Long id);
    
}

package com.codesupreme.sifarisqrupu.service.impl.elan;

import com.codesupreme.sifarisqrupu.dao.elan.ElanRepository;
import com.codesupreme.sifarisqrupu.dto.elan.ElanDto;
import com.codesupreme.sifarisqrupu.model.elan.Elan;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ElanServiceImpl {

    private final ElanRepository elanRepository;
    private final ModelMapper modelMapper;

    public ElanServiceImpl(ElanRepository elanRepository, ModelMapper modelMapper) {
        this.elanRepository = elanRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<ElanDto> getAllElans() {
        List<Elan> list = elanRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, ElanDto.class))
                .toList();
    }

    //ById
    public ElanDto getElanById(Long id) {
        Optional<Elan> optional = elanRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, ElanDto.class)).orElse(null);
    }

    //Save
    public ElanDto saveElan(ElanDto dto) {
        Elan elan = modelMapper.map(dto, Elan.class);
        elan = elanRepository.save(elan);
        return modelMapper.map(elan, ElanDto.class);
    }

    //Update
    public ElanDto updateElan(Long elanId, ElanDto elanDto) {
        Optional<Elan> optional = elanRepository.findById(elanId);
        if (optional.isPresent()) {
            Elan elan = optional.get();

            if (elanDto.getDescription() != null) {
                elan.setDescription(elanDto.getDescription());
            }

            if (elanDto.getUserId() != null) {
                elan.setUserId(elanDto.getUserId());
            }

            if (elanDto.getTitle() != null) {
                elan.setTitle(elanDto.getTitle());
            }

            if (elanDto.getDescription() != null) {
                elan.setDescription(elanDto.getDescription());
            }

            if (elanDto.getImages() != null) {
                elan.setImages(elanDto.getImages());
            }

            if (elanDto.getPrice() != null) {
                elan.setPrice(elanDto.getPrice());
            }

            if (elanDto.getIsDisable() != null) {
                elan.setIsDisable(elanDto.getIsDisable());
            }

            if (elanDto.getCreatedAt() != null) {
                elan.setCreatedAt(elanDto.getCreatedAt());
            }

            elan = elanRepository.save(elan);

            return modelMapper.map(elan, ElanDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteElan(Long id) {
        Optional<Elan> optional = elanRepository.findById(id);
        if (optional.isPresent()) {
            Elan elan = optional.get();
            elanRepository.delete(elan);
            return true;
        }
        return false;
    }
}

package com.codesupreme.sifarisqrupu.service.impl.usta;

import com.codesupreme.sifarisqrupu.dao.usta.UstaRepository;
import com.codesupreme.sifarisqrupu.dto.usta.UstaDto;
import com.codesupreme.sifarisqrupu.model.usta.Usta;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UstaServiceImpl {

    private final UstaRepository ustaRepository;
    private final ModelMapper modelMapper;

    public UstaServiceImpl(UstaRepository ustaRepository, ModelMapper modelMapper) {
        this.ustaRepository = ustaRepository;
        this.modelMapper = modelMapper;
    }

    //ALL
    public List<UstaDto> getAllUstas() {
        List<Usta> list = ustaRepository.findAll();
        return list.stream()
                .map(det -> modelMapper.map(det, UstaDto.class))
                .toList();
    }

    //ById
    public UstaDto getUstaById(Long id) {
        Optional<Usta> optional = ustaRepository.findById(id);
        return optional.map(det -> modelMapper.map(det, UstaDto.class)).orElse(null);
    }

    //Save
    public UstaDto saveUsta(UstaDto dto) {
        Usta usta = modelMapper.map(dto, Usta.class);
        usta = ustaRepository.save(usta);
        return modelMapper.map(usta, UstaDto.class);
    }

    //Update
    public UstaDto updateUsta(Long ustaId, UstaDto ustaDto) {
        Optional<Usta> optional = ustaRepository.findById(ustaId);
        if (optional.isPresent()) {
            Usta usta = optional.get();

            if (ustaDto.getName() != null) {
                usta.setName(ustaDto.getName());
            }

            if (ustaDto.getPhone() != null) {
                usta.setPhone(ustaDto.getPhone());
            }

            if (ustaDto.getHours() != null) {
                usta.setHours(ustaDto.getHours());
            }

            if (ustaDto.getLocation() != null) {
                usta.setLocation(ustaDto.getLocation());
            }


            if (ustaDto.getIsDisable() != null) {
                usta.setIsDisable(ustaDto.getIsDisable());
            }

            if (ustaDto.getCreatedAt() != null) {
                usta.setCreatedAt(ustaDto.getCreatedAt());
            }

            usta = ustaRepository.save(usta);

            return modelMapper.map(usta, UstaDto.class);
        }
        return null;
    }

    //Delete
    public Boolean deleteUsta(Long id) {
        Optional<Usta> optional = ustaRepository.findById(id);
        if (optional.isPresent()) {
            Usta usta = optional.get();
            ustaRepository.delete(usta);
            return true;
        }
        return false;
    }
}

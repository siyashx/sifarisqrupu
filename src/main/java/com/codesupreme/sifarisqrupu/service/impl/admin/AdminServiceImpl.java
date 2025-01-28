package com.codesupreme.sifarisqrupu.service.impl.admin;

import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.dto.admin.AdminDto;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import com.codesupreme.sifarisqrupu.service.inter.admin.AdminServiceInter;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdminServiceImpl implements AdminServiceInter {

    private final AdminRepository adminRepository;

    private final ModelMapper modelMapper;

    public AdminServiceImpl(AdminRepository adminRepository, ModelMapper modelMapper) {
        this.adminRepository = adminRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public AdminDto getAdminById(Long id) {
        Admin entity = adminRepository.findById(id).orElse(null);
        if (entity != null) {
            return modelMapper.map(entity, AdminDto.class);
        }
        return null;
    }

    @Override
    public AdminDto updateAdmin(Long id, AdminDto adminDto) {
        Optional<Admin> adminOptional = adminRepository.findById(id);

        if (adminOptional.isPresent()) {
            Admin admin = adminOptional.get();

            if (adminDto.getOneSignal() != null) {
                admin.setOneSignal(adminDto.getOneSignal());
            }

            if (adminDto.getName() != null) {
                admin.setName(adminDto.getName());
            }

            if (adminDto.getPhoneNumber() != null) {
                admin.setPhoneNumber(adminDto.getPhoneNumber());
            }

            if (adminDto.getNotificationHistory() != null) {
                admin.setNotificationHistory(adminDto.getNotificationHistory());
            }

            if (adminDto.getIsMutedNotifications() != null) {
                admin.setIsMutedNotifications(adminDto.getIsMutedNotifications());
            }

            if (adminDto.getPassword() != null) {
                admin.setPassword(adminDto.getPassword());
            }

            if (adminDto.getIsRealAdmin() != null) {
                admin.setIsRealAdmin(adminDto.getIsRealAdmin());
            }

            if (adminDto.getIsDisable() != null) {
                admin.setIsDisable(adminDto.getIsDisable());
            }

            admin = adminRepository.save(admin);
            return modelMapper.map(admin, AdminDto.class);
        }
        return null;
    }

    @Override
    public ResponseEntity<AdminDto> createAdmin(AdminDto adminDto) {
        Admin adminEntity = modelMapper.map(adminDto, Admin.class);
        Admin savedAdmin = adminRepository.save(adminEntity);
        return ResponseEntity.ok(modelMapper.map(savedAdmin, AdminDto.class));
    }

    @Override
    public Boolean isAdminPhoneNumberTaken(String phoneNumber) {
        return adminRepository.findAdminByPhoneNumber(phoneNumber) != null;
    }

    @Override
    public AdminDto findAdminByPhoneNumber(String phoneNumber) {
        Admin admin = adminRepository.findAdminByPhoneNumber(phoneNumber);
        if (admin != null) {
            return modelMapper.map(admin, AdminDto.class);
        }
        return null;
    }
}

package com.codesupreme.sifarisqrupu.service.inter.admin;

import com.codesupreme.sifarisqrupu.dto.admin.AdminDto;
import com.codesupreme.sifarisqrupu.dto.courier.CourierDto;
import org.springframework.http.ResponseEntity;

public interface AdminServiceInter {

    AdminDto getAdminById(Long id);
    AdminDto updateAdmin(Long id, AdminDto adminDto);
    ResponseEntity<AdminDto> createAdmin(AdminDto adminDto);
    Boolean isAdminPhoneNumberTaken(String phoneNumber);
    AdminDto findAdminByPhoneNumber(String phoneNumber);
}

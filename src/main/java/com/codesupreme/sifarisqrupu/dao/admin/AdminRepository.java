package com.codesupreme.sifarisqrupu.dao.admin;

import com.codesupreme.sifarisqrupu.model.admin.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Admin findAdminByPhoneNumber(String phoneNumber);

}

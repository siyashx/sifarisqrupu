package com.codesupreme.sifarisqrupu.adminusers;

import com.codesupreme.sifarisqrupu.model.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminUserDirectory extends Repository<User, Long> {
    @Query("select new com.codesupreme.sifarisqrupu.adminusers.AdminUserRow(u.id,u.name,u.phoneNumber,u.userType,u.identifyPhoto,u.courierStatus,u.isSub,u.expiryDate,u.isSubMotoTaksi,u.motoTaksiExpiryDate) from User u where u.id in :ids")
    List<AdminUserRow> cards(@Param("ids") Collection<Long> ids);
}

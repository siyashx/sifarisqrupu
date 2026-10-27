package com.codesupreme.sifarisqrupu.dao.user;

import com.codesupreme.sifarisqrupu.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByCourierStatus(String courierStatus);
    @Query("select u.id from User u where u.isDisable is null or u.isDisable = false")
    List<Long> findNotificationRecipientIds();
    User findUserByPhoneNumber(String phoneNumber);
    User findByLocationUrl(String locationUrl);

    @Query("select (count(u) > 0) from User u where u.phoneId = :phoneId and u.isDisable = true")
    boolean isDeviceBlocked(@Param("phoneId") String phoneId);

    @Query("select u from User u where function('regexp_replace', u.phoneNumber, '[^0-9]', '') in :phones order by u.id")
    List<User> findByPhoneDigits(@Param("phones") List<String> phones);
}

package com.codesupreme.sifarisqrupu.dao.user;

import com.codesupreme.sifarisqrupu.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findUserByPhoneNumber(String phoneNumber);
    User findByLocationUrl(String locationUrl);
}

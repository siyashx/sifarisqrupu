package com.codesupreme.sifarisqrupu.dao.courier;

import com.codesupreme.sifarisqrupu.model.courier.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    Courier findCourierByPhoneNumber(String phoneNumber);
}

package com.codesupreme.sifarisqrupu.service.inter.courier;

import com.codesupreme.sifarisqrupu.dto.courier.CourierDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface CourierServiceInter {

    List<CourierDto> getAllCouriers();
    CourierDto getCourierById(Long id);
    ResponseEntity<CourierDto> createCourier(CourierDto courierDto);
    CourierDto updateCourier(Long id, CourierDto courierDto);
    void deleteCourier(Long id);
    Boolean isCourierPhoneNumberTaken(String phoneNumber);
    CourierDto findCourierByPhoneNumber(String phoneNumber);
}

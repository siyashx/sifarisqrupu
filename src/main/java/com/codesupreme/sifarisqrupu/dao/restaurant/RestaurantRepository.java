package com.codesupreme.sifarisqrupu.dao.restaurant;

import com.codesupreme.sifarisqrupu.model.restaurant.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Restaurant findRestaurantByPhoneNumber(String phoneNumber);
}

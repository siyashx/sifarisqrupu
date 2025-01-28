package com.codesupreme.sifarisqrupu.service.impl.restaurant;

import com.codesupreme.sifarisqrupu.dao.restaurant.RestaurantRepository;
import com.codesupreme.sifarisqrupu.dto.restaurant.RestaurantDto;
import com.codesupreme.sifarisqrupu.model.restaurant.Restaurant;
import com.codesupreme.sifarisqrupu.service.inter.restaurant.RestaurantServiceInter;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RestaurantServiceImpl implements RestaurantServiceInter {

    private final RestaurantRepository restaurantRepository;
    private final ModelMapper modelMapper;

    public RestaurantServiceImpl(RestaurantRepository restaurantRepository, ModelMapper modelMapper) {
        this.restaurantRepository = restaurantRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public List<RestaurantDto> getAllRestaurants() {
        List<RestaurantDto> listDto = new ArrayList<>();

        List<Restaurant> listEntity = restaurantRepository.findAll();

        for (Restaurant entity : listEntity) {
            RestaurantDto dto = modelMapper.map(entity, RestaurantDto.class);
            listDto.add(dto);
        }
        return listDto;
    }

    @Override
    public RestaurantDto getRestaurantById(Long id) {
        Restaurant entity = restaurantRepository.findById(id).orElse(null);
        if (entity != null) {
            return modelMapper.map(entity, RestaurantDto.class);
        }
        return null;
    }

    @Override
    public ResponseEntity<RestaurantDto> createRestaurant(RestaurantDto restaurantDto) {
        Restaurant restaurantEntity = modelMapper.map(restaurantDto, Restaurant.class);
        if (isRestaurantPhoneNumberTaken(restaurantEntity.getPhoneNumber())) {
            return ResponseEntity.badRequest().build();
        }
        restaurantEntity.setIsDisable(false);
        Restaurant savedRestaurant = restaurantRepository.save(restaurantEntity);
        return ResponseEntity.ok(modelMapper.map(savedRestaurant, RestaurantDto.class));

    }

    @Override
    public RestaurantDto updateRestaurant(Long id, RestaurantDto restaurantDto) {
        Optional<Restaurant> restaurantOptional = restaurantRepository.findById(id);
        if (restaurantOptional.isPresent()) {
            Restaurant restaurant = restaurantOptional.get();

            if (restaurantDto.getOneSignal() != null) {
                restaurant.setOneSignal(restaurantDto.getOneSignal());
            }
            if (restaurantDto.getName() != null) {
                restaurant.setName(restaurantDto.getName());
            }

            if (restaurantDto.getPhoneNumber() != null) {
                restaurant.setPhoneNumber(restaurantDto.getPhoneNumber());
            }

            if (restaurantDto.getNotificationHistory() != null) {
                restaurant.setNotificationHistory(restaurantDto.getNotificationHistory());
            }

            if (restaurantDto.getIsMutedNotifications() != null) {
                restaurant.setIsMutedNotifications(restaurantDto.getIsMutedNotifications());
            }

            if (restaurantDto.getPassword() != null) {
                restaurant.setPassword(restaurantDto.getPassword());
            }

            if (restaurantDto.getOrderCount() != null) {
                restaurant.setOrderCount(restaurantDto.getOrderCount());
            }

            if (restaurantDto.getOnline() != null) {
                restaurant.setOnline(restaurantDto.getOnline());
            }

            if (restaurantDto.getExpiryDate() != null) {
                restaurant.setExpiryDate(restaurantDto.getExpiryDate());
            }

            if (restaurantDto.getCreatedDate() != null) {
                restaurant.setCreatedDate(restaurantDto.getCreatedDate());
            }

            if (restaurantDto.getIsDisable() != null) {
                restaurant.setIsDisable(restaurantDto.getIsDisable());
            }

            restaurant = restaurantRepository.save(restaurant);
            return modelMapper.map(restaurant, RestaurantDto.class);


        }
        return null;
    }

    @Override
    public void deleteRestaurantById(Long id) {
        restaurantRepository.deleteById(id);
    }

    @Override
    public Boolean isRestaurantPhoneNumberTaken(String phoneNumber) {
        return restaurantRepository.findRestaurantByPhoneNumber(phoneNumber) != null;
    }

    @Override
    public RestaurantDto findRestaurantByPhoneNumber(String phoneNumber) {
        Restaurant restaurant = restaurantRepository.findRestaurantByPhoneNumber(phoneNumber);
        if (restaurant != null) {
            return modelMapper.map(restaurant, RestaurantDto.class);
        }
        return null;
    }

}

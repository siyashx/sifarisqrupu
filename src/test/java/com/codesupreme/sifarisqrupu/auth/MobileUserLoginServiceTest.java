package com.codesupreme.sifarisqrupu.auth;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MobileUserLoginServiceTest {
    private UserRepository users;
    private MobileUserLoginService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new MobileUserLoginService(users, new ModelMapper());
        user = User.builder().id(7L).phoneNumber("0551234567").password("secret1").isDisable(false).build();
    }

    @Test void canonicalLoginUsesOnlyOneAccountAndRetainsRoleData() {
        user.setUserType("moto_courier");
        when(users.findUserByPhoneNumber("0551234567")).thenReturn(user);
        UserDto result = service.login("+994 55 123 45 67", "secret1", "device");
        assertEquals(7L, result.getId());
        assertEquals("moto_courier", result.getUserType());
        verify(users).isDeviceBlocked("device");
        verify(users).findUserByPhoneNumber("0551234567");
        verifyNoMoreInteractions(users);
    }

    @Test void deviceBanRejectsBeforeLookingUpAccount() {
        when(users.isDeviceBlocked("device")).thenReturn(true);
        var error = assertThrows(MobileUserLoginService.Rejected.class, () -> service.login("0551234567", "secret1", "device"));
        assertEquals("DEVICE_BLOCKED", error.code());
        verify(users, never()).findUserByPhoneNumber(anyString());
    }

    @Test void accountBanAndWrongPasswordNeverReturnAccount() {
        when(users.findUserByPhoneNumber("0551234567")).thenReturn(user);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(MobileUserLoginService.Rejected.class, () -> service.login("0551234567", "wrong", "device")).status());
        user.setIsDisable(true);
        assertEquals("ACCOUNT_DISABLED", assertThrows(MobileUserLoginService.Rejected.class, () -> service.login("0551234567", "secret1", "device")).code());
    }

    @Test void legacyPhoneLookupReturnsOnlyMatchingCandidates() {
        user.setPhoneNumber("+994 55 123 45 67");
        when(users.findByPhoneDigits(anyList())).thenReturn(List.of(user));
        assertEquals(7L, service.login("0551234567", "secret1", "device").getId());
        verify(users).findByPhoneDigits(List.of("0551234567", "994551234567", "00994551234567"));
        verify(users, never()).findAll();
    }

    @Test void invalidInputNeverQueriesDatabase() {
        assertThrows(MobileUserLoginService.Rejected.class, () -> service.login("abc", "secret1", "device"));
        assertThrows(MobileUserLoginService.Rejected.class, () -> service.login("0551234567", "secret1", ""));
        verifyNoInteractions(users);
    }

    @Test void normalizationPreservesLocalAndRemovesHiddenWhitespace() {
        for (String phone : List.of("0551234567", "055\u00a0123\u200b4567", "994551234567", "00994 (55) 123-45-67")) {
            assertEquals("0551234567", MobileUserLoginService.normalizePhone(phone));
        }
    }
}

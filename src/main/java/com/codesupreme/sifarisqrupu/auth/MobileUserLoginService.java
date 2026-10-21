package com.codesupreme.sifarisqrupu.auth;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class MobileUserLoginService {
    private final UserRepository users;
    private final ModelMapper mapper;

    public MobileUserLoginService(UserRepository users, ModelMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserDto login(String phone, String password, String phoneId) {
        String normalized = normalizePhone(phone);
        if (!normalized.matches("0[1-9][0-9]{8}") || password == null || password.isEmpty()
                || phoneId == null || phoneId.isBlank()) {
            throw new Rejected(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Telefon nömrəsi, şifrə və cihaz məlumatı tələb olunur.");
        }
        // Enforce the same device-wide ban as the old mobile all-users check.
        if (users.isDeviceBlocked(phoneId)) {
            throw new Rejected(HttpStatus.FORBIDDEN, "DEVICE_BLOCKED", "Bu cihaz bloklanıb.");
        }
        User user = users.findUserByPhoneNumber(normalized);
        if (user == null) {
            String international = "994" + normalized.substring(1);
            // MariaDB's REGEXP_REPLACE normalizes legacy spaces, +994, NBSP and punctuation
            // in the database. Only matching accounts are returned, never the user list.
            List<User> candidates = users.findByPhoneDigits(List.of(normalized, international, "00" + international));
            user = candidates.stream()
                    .filter(candidate -> Objects.equals(candidate.getPassword(), password))
                    .findFirst().orElse(null);
        }
        if (user == null || !Objects.equals(user.getPassword(), password)) {
            throw new Rejected(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Telefon nömrəsi və ya şifrə yanlışdır.");
        }
        if (Boolean.TRUE.equals(user.getIsDisable())) {
            throw new Rejected(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Hesabınız bloklanmış və ya deaktiv edilmişdir.");
        }
        return mapper.map(user, UserDto.class);
    }

    static String normalizePhone(String value) {
        String phone = value == null ? "" : value.replaceAll("[\\s\\u00a0\\u200b-\\u200f\\u202a-\\u202e\\u2060\\ufeff()\\-]", "");
        if (phone.startsWith("+994")) return "0" + phone.substring(4);
        if (phone.startsWith("00994")) return "0" + phone.substring(5);
        if (phone.startsWith("994")) return "0" + phone.substring(3);
        return phone;
    }

    public static class Rejected extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        Rejected(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public HttpStatus status() { return status; }
        public String code() { return code; }
    }
}

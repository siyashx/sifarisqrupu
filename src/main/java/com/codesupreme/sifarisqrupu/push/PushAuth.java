package com.codesupreme.sifarisqrupu.push;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class PushAuth {
    private final UserRepository users;
    public PushAuth(UserRepository users) { this.users=users; }
    public User requireUser(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            String decoded=new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int colon=decoded.indexOf(':');
            if (colon<1) throw new IllegalArgumentException();
            User user=users.findById(Long.parseLong(decoded.substring(0,colon))).orElseThrow();
            String password=decoded.substring(colon+1);
            if (password.isEmpty() || user.getPassword()==null || Boolean.TRUE.equals(user.getIsDisable()) ||
                !MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), user.getPassword().getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException();
            }
            return user;
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Push authentication required");
        }
    }
}

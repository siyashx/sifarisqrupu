package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
@Service
public class AdminPushAuth {
    private final AdminRepository admins;
    public AdminPushAuth(AdminRepository admins) {this.admins=admins;}
    public Admin requireAdmin(String authorization) {
        try {
            if(authorization==null || !authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            String decoded=new String(Base64.getDecoder().decode(authorization.substring(6)),StandardCharsets.UTF_8);
            int colon=decoded.indexOf(':'); if(colon<1) throw new IllegalArgumentException();
            Admin admin=admins.findById(Long.parseLong(decoded.substring(0,colon))).orElseThrow();
            String password=decoded.substring(colon+1);
            if(password.isBlank() || admin.getPassword()==null || Boolean.TRUE.equals(admin.getIsDisable()) ||
                !MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),admin.getPassword().getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            return admin;
        } catch(RuntimeException e) {throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Admin authentication required");}
    }
}

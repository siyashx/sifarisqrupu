package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
@Service
public class AdminPushAuth {
    private final AdminRepository admins;
    private final String appKeyHash;
    public AdminPushAuth(AdminRepository admins,
        @Value("${push.admin.app-key-sha256:}") String appKeyHash) {
        this.admins=admins;this.appKeyHash=appKeyHash;
    }
    public Admin requireAdmin(String authorization) {
        try {
            // This credential is accepted only by the admin-push controller, for admin 1.
            if(authorization!=null && authorization.startsWith("Bearer ")) {
                String key=authorization.substring(7);
                if(!appKeyHash.matches("[a-fA-F0-9]{64}") || !key.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException();
                byte[] digest;
                try {digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));}
                catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
                if(!MessageDigest.isEqual(digest,HexFormat.of().parseHex(appKeyHash))) throw new IllegalArgumentException();
                Admin admin=admins.findById(1L).orElseThrow();
                if(Boolean.TRUE.equals(admin.getIsDisable())) throw new IllegalArgumentException();
                return admin;
            }
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

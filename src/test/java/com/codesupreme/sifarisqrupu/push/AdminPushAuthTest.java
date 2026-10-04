package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.admin.AdminRepository;
import com.codesupreme.sifarisqrupu.model.admin.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminPushAuthTest {
    String header(String value) {return "Basic "+Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    @Test void adminCredentialsAreCheckedAgainstAdminTable() {
        var repo=mock(AdminRepository.class);var admin=Admin.builder().id(1L).password("secret:with-colon").isDisable(false).build();
        when(repo.findById(1L)).thenReturn(Optional.of(admin));var auth=new AdminPushAuth(repo, "");
        assertSame(admin,auth.requireAdmin(header("1:secret:with-colon")));
        assertThrows(ResponseStatusException.class,()->auth.requireAdmin(header("1:wrong")));
        assertThrows(ResponseStatusException.class,()->auth.requireAdmin(null));
        admin.setIsDisable(true);assertThrows(ResponseStatusException.class,()->auth.requireAdmin(header("1:secret:with-colon")));
    }
    @Test void mobileAppCodeCannotClaimAdminNamespace() {assertThrows(IllegalArgumentException.class,()->FcmPushService.app("ADMIN"));}
    @Test void appKeyIsBoundToAdminOneAndRejectsDisabledAccount() throws Exception {
        var repo=mock(AdminRepository.class);
        var admin=Admin.builder().id(1L).isDisable(false).build();
        when(repo.findById(1L)).thenReturn(Optional.of(admin));
        String key="a".repeat(43);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        var auth=new AdminPushAuth(repo,hash);
        assertSame(admin,auth.requireAdmin("Bearer "+key));
        assertThrows(ResponseStatusException.class,()->auth.requireAdmin("Bearer "+"b".repeat(43)));
        assertThrows(ResponseStatusException.class,()->auth.requireAdmin("Bearer 1"));
        verify(repo,times(1)).findById(1L);
        admin.setIsDisable(true);
        assertThrows(ResponseStatusException.class,()->auth.requireAdmin("Bearer "+key));
    }
    @Test void missingAppKeyConfigFailsClosedAndUnknownAdminFails() throws Exception {
        var repo=mock(AdminRepository.class);
        assertThrows(ResponseStatusException.class,()->new AdminPushAuth(repo, "").requireAdmin("Bearer "+"a".repeat(43)));
        verifyNoInteractions(repo);
        String key="a".repeat(43);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        assertThrows(ResponseStatusException.class,()->new AdminPushAuth(repo,hash).requireAdmin("Bearer "+key));
    }
}

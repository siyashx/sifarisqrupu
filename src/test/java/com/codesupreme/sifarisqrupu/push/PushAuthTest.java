package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PushAuthTest {
    String basic(String value) {return "Basic "+Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    @Test void verifiesPasswordNotJustUserId() {
        var repo=mock(UserRepository.class);var user=User.builder().id(7L).password("real:password").build();
        when(repo.findById(7L)).thenReturn(Optional.of(user));var auth=new PushAuth(repo);
        assertSame(user,auth.requireUser(basic("7:real:password")));
        assertThrows(ResponseStatusException.class,()->auth.requireUser(basic("7:wrong")));
        assertThrows(ResponseStatusException.class,()->auth.requireUser(null));
    }
    @Test void disabledAndEmptyPasswordAccountsCannotRegister() {
        var repo=mock(UserRepository.class);var user=User.builder().id(7L).password("pw").isDisable(true).build();
        when(repo.findById(7L)).thenReturn(Optional.of(user));var auth=new PushAuth(repo);
        assertThrows(ResponseStatusException.class,()->auth.requireUser(basic("7:pw")));
        user.setIsDisable(false);user.setPassword("");
        assertThrows(ResponseStatusException.class,()->auth.requireUser(basic("7:")));
    }
}

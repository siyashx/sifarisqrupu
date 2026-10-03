package com.codesupreme.sifarisqrupu.push;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.dto.user.UserDto;
import com.codesupreme.sifarisqrupu.service.impl.user.UserServiceImpl;
import org.modelmapper.ModelMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class AdminDocumentHookTest {
    @Test void documentSubmissionNotifiesButPollingAndUnrelatedUpdatesDoNot() {
        var repo=mock(UserRepository.class);var pushes=mock(AdminVerificationPushService.class);
        var user=User.builder().id(9L).courierStatus("decline").identifyPhoto(List.of()).build();
        when(repo.findById(9L)).thenReturn(Optional.of(user));when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new UserServiceImpl(repo,new ModelMapper(),pushes);
        var documents=new UserDto();documents.setIdentifyPhoto(List.of("selfie","front","back"));documents.setCourierStatus("active");
        service.updateUser(9L,documents);verify(pushes).notifyAdmin(9L);
        service.updateUser(9L,documents);var ordinary=new UserDto();ordinary.setName("Updated name");service.updateUser(9L,ordinary);
        verify(pushes,times(1)).notifyAdmin(9L);
    }
}

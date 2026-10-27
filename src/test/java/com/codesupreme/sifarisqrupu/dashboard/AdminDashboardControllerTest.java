package com.codesupreme.sifarisqrupu.dashboard;
import com.codesupreme.sifarisqrupu.push.AdminPushAuth;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.advertising.AdRequestRepository;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminDashboardControllerTest {
 @Test void onlyPendingRequestCountsAreReturnedAfterAdminAuthentication(){
  var auth=mock(AdminPushAuth.class);var users=mock(UserRepository.class);var ads=mock(AdRequestRepository.class);
  when(users.countByCourierStatus("active")).thenReturn(3L);when(ads.countByKindAndStatus("CHAT","PENDING")).thenReturn(2L);when(ads.countByKindAndStatus("SHOP","PENDING")).thenReturn(1L);
  var controller=new AdminDashboardController(auth,users,ads);assertEquals(Map.of("couriers",3L,"chatAds",2L,"listingAds",1L),controller.counts("admin"));verify(auth).requireAdmin("admin");
  verify(users,never()).findAll();verify(ads,never()).findAll();
  when(auth.requireAdmin("invalid")).thenThrow(new IllegalArgumentException());assertThrows(IllegalArgumentException.class,()->controller.counts("invalid"));
 }
}

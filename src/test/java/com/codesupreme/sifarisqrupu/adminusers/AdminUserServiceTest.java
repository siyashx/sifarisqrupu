package com.codesupreme.sifarisqrupu.adminusers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class AdminUserServiceTest {
    NamedParameterJdbcTemplate jdbc;
    AdminUserDirectory directory;
    AdminUserService service;
    @BeforeEach void setup() {
        jdbc=mock(NamedParameterJdbcTemplate.class); directory=mock(AdminUserDirectory.class);
        service=new AdminUserService(jdbc,directory);
        when(jdbc.queryForMap(anyString(),anyMap())).thenReturn(Map.of("total",6558L,"customers",4000L));
    }
    AdminUserRow row(long id) { return new AdminUserRow(id,"User "+id,"0551234567","moto_courier",List.of("https://example.com/photo"),"active",true,new Date(),false,null); }
    @Test void pagesOnlyFetchTheRequestedCardsAndKeepDatabaseOrder() {
        when(jdbc.queryForList(anyString(),anyMap(),eq(Long.class))).thenReturn(List.of(9L,8L,7L));
        when(directory.cards(List.of(9L,8L))).thenReturn(List.of(row(8),row(9)));
        var result=service.list("","courier","none","all",0,2);
        assertEquals(List.of(9L,8L),result.items().stream().map(AdminUserRow::id).toList());
        assertTrue(result.hasMore()); assertEquals(2558,result.total());
        assertEquals(Map.of("courier",2558L,"customer",4000L),result.counts());
        verify(directory).cards(List.of(9L,8L));
        verify(jdbc).queryForList(contains("user_type IS NULL"),argThat((Map<String,?> p)->p.get("limit").equals(3)&&p.get("offset").equals(0L)),eq(Long.class));
    }
    @Test void pendingRequestsAreFilteredBeforePagingAndDoNotFetchAllUsers() {
        when(jdbc.queryForList(anyString(),anyMap(),eq(Long.class))).thenReturn(List.of(7L));
        when(directory.cards(List.of(7L))).thenReturn(List.of(row(7)));
        var result=service.list("","all","none","active",0,40);
        assertFalse(result.hasMore()); assertEquals(1,result.items().size());
        verify(jdbc).queryForList(contains("courier_status='active'"),argThat((Map<String,?> p)->p.get("limit").equals(41)),eq(Long.class));
    }
    @Test void emptyPagesSkipEntityReadsAndSearchIsBoundAndEscapesWildcards() {
        when(jdbc.queryForList(anyString(),anyMap(),eq(Long.class))).thenReturn(List.of());
        service.list("  A%_!  ","customer","none","all",2,40);
        verifyNoInteractions(directory);
        verify(jdbc).queryForList(contains("user_type='customer'"),argThat((Map<String,?> p)->p.get("pattern").equals("%a!%!_!!%")&&p.get("offset").equals(80L)),eq(Long.class));
    }
    @Test void subscriptionSortIsGlobalAndUsesADeterministicTieBreaker() {
        when(jdbc.queryForList(anyString(),anyMap(),eq(Long.class))).thenReturn(List.of());
        service.list("","all","most","all",0,40);
        service.list("","all","least","all",0,40);
        verify(jdbc).queryForList(contains(AdminUserService.SCORE+" DESC, id DESC"),anyMap(),eq(Long.class));
        verify(jdbc).queryForList(contains(AdminUserService.SCORE+" ASC, id DESC"),anyMap(),eq(Long.class));
    }
    @Test void invalidAndUnboundedQueriesAreRejectedBeforeDatabaseAccess() {
        for (String type: List.of("bad","courier' OR 1=1")) assertThrows(ResponseStatusException.class,()->service.list("",type,"none","all",0,40));
        assertThrows(ResponseStatusException.class,()->service.list("","all","bad","all",0,40));
        assertThrows(ResponseStatusException.class,()->service.list("","all","none","bad",0,40));
        assertThrows(ResponseStatusException.class,()->service.list("","all","none","all",-1,40));
        assertThrows(ResponseStatusException.class,()->service.list("","all","none","all",0,101));
        assertThrows(ResponseStatusException.class,()->service.list("a".repeat(101),"all","none","all",0,40));
        verifyNoInteractions(jdbc,directory);
    }
}

package com.codesupreme.sifarisqrupu.adminusers;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AdminUserDirectory directory;
    public AdminUserService(NamedParameterJdbcTemplate jdbc, AdminUserDirectory directory) {
        this.jdbc = jdbc; this.directory = directory;
    }

    public record Page(List<AdminUserRow> items, int page, boolean hasMore, long total, Map<String,Long> counts) {}
    private static final String MOTO_EXPIRY = "STR_TO_DATE(REPLACE(LEFT(moto_taksi_expiry_date,19),'T',' '),'%Y-%m-%d %H:%i:%s')";
    private static final String SUB_ACTIVE = "(is_sub=1 AND expiry_date>UTC_TIMESTAMP())";
    private static final String MOTO_ACTIVE = "(is_sub_moto_taksi=1 AND " + MOTO_EXPIRY + ">UTC_TIMESTAMP())";
    static final String SCORE = "(CASE WHEN " + SUB_ACTIVE + " THEN 200000+TIMESTAMPDIFF(DAY,UTC_TIMESTAMP(),expiry_date) ELSE 0 END + CASE WHEN " + MOTO_ACTIVE + " THEN 100000+TIMESTAMPDIFF(DAY,UTC_TIMESTAMP()," + MOTO_EXPIRY + ") ELSE 0 END)";

    @Transactional(readOnly = true)
    public Page list(String query, String type, String sort, String status, int page, int size) {
        if (!Set.of("all","courier","customer").contains(type) ||
            !Set.of("none","most","least").contains(sort) ||
            !Set.of("all","active").contains(status) || page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Yanlış siyahı seçimi.");
        }
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Axtarış çox uzundur.");
        String pattern = "%" + q.replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        String where = " WHERE (:q='' OR LOWER(COALESCE(name,'')) LIKE :pattern ESCAPE '!' OR COALESCE(phone_number,'') LIKE :pattern ESCAPE '!')";
        if (status.equals("active")) where += " AND courier_status='active'";
        Map<String,Object> params = new HashMap<>(Map.of("q",q,"pattern",pattern,"limit",size+1,"offset",(long)page*size));
        Map<String,Object> counts = jdbc.queryForMap("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN user_type='customer' THEN 1 ELSE 0 END),0) AS customers FROM user" + where, params);
        long all = ((Number)counts.get("total")).longValue();
        long customers = ((Number)counts.get("customers")).longValue();
        if (type.equals("customer")) where += " AND user_type='customer'";
        if (type.equals("courier")) where += " AND (user_type IS NULL OR user_type<>'customer')";
        String order = sort.equals("none") ? "id DESC" : SCORE + (sort.equals("most") ? " DESC" : " ASC") + ", id DESC";
        List<Long> ids = jdbc.queryForList("SELECT id FROM user" + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",params,Long.class);
        boolean more = ids.size()>size;
        if (more) ids = new ArrayList<>(ids.subList(0,size));
        Map<Long,AdminUserRow> rows = new HashMap<>();
        if (!ids.isEmpty()) for (AdminUserRow row : directory.cards(ids)) rows.put(row.id(),row);
        List<AdminUserRow> items = ids.stream().map(rows::get).filter(Objects::nonNull).toList();
        return new Page(items,page,more,type.equals("all")?all:type.equals("customer")?customers:all-customers,
            Map.of("courier",all-customers,"customer",customers));
    }
}

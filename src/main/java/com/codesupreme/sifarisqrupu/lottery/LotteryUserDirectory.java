package com.codesupreme.sifarisqrupu.lottery;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface LotteryUserDirectory extends org.springframework.data.repository.Repository<User,Long> {
    @Query("select u from User u where (u.isDisable is null or u.isDisable=false) and (:q='' or lower(coalesce(u.name,'')) like concat('%',:q,'%') or coalesce(u.phoneNumber,'') like concat('%',:q,'%')) order by u.id desc")
    Page<User> search(@Param("q") String q,Pageable page);
}

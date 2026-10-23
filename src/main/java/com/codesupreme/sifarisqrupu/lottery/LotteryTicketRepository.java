package com.codesupreme.sifarisqrupu.lottery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface LotteryTicketRepository extends JpaRepository<LotteryTicket,Long> {
    List<LotteryTicket> findByLotteryIdAndRequestIdOrderByNumber(Long lotteryId,String requestId);
    List<LotteryTicket> findByLotteryIdAndUserIdAndRevokedFalseOrderByNumber(Long lotteryId,Long userId);
    Optional<LotteryTicket> findByLotteryIdAndNumber(Long lotteryId,int number);
    Optional<LotteryTicket> findFirstByLotteryIdOrderByNumberDesc(Long lotteryId);
    List<LotteryTicket> findByLotteryIdAndRevokedFalseOrderByNumber(Long lotteryId);
    Page<LotteryTicket> findByLotteryIdAndRevokedFalseOrderByNumber(Long lotteryId,Pageable page);
}

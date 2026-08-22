package com.codesupreme.sifarisqrupu.service.impl.order;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiCourierPushService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MotoTaxiOrderDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MotoTaxiOrderDispatchService.class);
    private static final String NO_COURIER = "no_courier";
    private static final Set<String> ACTIVE_STATUSES = Set.of("to_customer", "on_the_way");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final MotoTaxiCourierPushService pushService;
    private final TransactionTemplate transactionTemplate;
    private final long searchTimeoutMillis;
    private final long offerTimeoutMillis;
    private final Object dispatchMutex = new Object();

    public MotoTaxiOrderDispatchService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            ModelMapper modelMapper,
            MotoTaxiCourierPushService pushService,
            PlatformTransactionManager transactionManager,
            @Value("${mototaxi.dispatch.search-timeout-seconds:300}") long searchTimeoutSeconds,
            @Value("${mototaxi.dispatch.offer-timeout-seconds:60}") long offerTimeoutSeconds
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.pushService = pushService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.searchTimeoutMillis = Math.max(1, searchTimeoutSeconds) * 1000L;
        this.offerTimeoutMillis = Math.max(1, offerTimeoutSeconds) * 1000L;
    }

    /**
     * Called after a new order is created and by the scheduler. It reserves at most
     * one courier for the order, nearest to fromAddress, and sends the push only
     * after the database transaction has committed.
     */
    public void processOrder(Long orderId) {
        if (orderId == null) {
            return;
        }

        DispatchChange change;
        synchronized (dispatchMutex) {
            change = transactionTemplate.execute(status -> reserveNextCourier(orderId));
        }
        if (change == null) {
            return;
        }

        if (change.stopTarget() != null) {
            PushTarget oldTarget = change.stopTarget();
            pushService.sendOrderStop(
                    oldTarget.courierId(),
                    oldTarget.subscriptionId(),
                    orderId,
                    change.stopEvent(),
                    change.stopStatus(),
                    false
            );
        }

        if (change.offerTarget() != null) {
            PushTarget next = change.offerTarget();
            pushService.sendNewOrderOffer(
                    next.courierId(),
                    next.subscriptionId(),
                    orderId,
                    change.orderType()
            );
        }
    }

    public void processAllOpenOrders() {
        List<Long> ids = orderRepository.findOpenDispatchOrderIds(NO_COURIER);

        for (Long id : ids) {
            try {
                processOrder(id);
            } catch (Exception error) {
                log.error("MotoTaksi dispatch failed for order {}: {}", id, error.getMessage());
            }
        }
    }

    public OrderDto acceptOrder(Long orderId, Long courierId) {
        requireCourierId(courierId);

        AcceptResult outcome = transactionTemplate.execute(status -> {
            Date now = new Date();
            Order order = lockOrder(orderId);
            normalizeSearchDeadline(order, now);

            if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus())) {
                return AcceptResult.error(HttpStatus.CONFLICT, "Sifariş artıq mövcud deyil", false);
            }

            if (isSearchExpired(order, now)) {
                expireCurrentOffer(order);
                disableExpiredOrder(order);
                orderRepository.save(order);
                return AcceptResult.error(HttpStatus.GONE, "Sifariş üçün axtarış vaxtı bitib", false);
            }

            if (!Objects.equals(order.getOfferedCourierId(), courierId)) {
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Bu sifariş hazırda başqa kuryerə təklif olunub",
                        false
                );
            }

            if (order.getOfferExpiresAt() == null || !order.getOfferExpiresAt().after(now)) {
                expireCurrentOffer(order);
                orderRepository.save(order);
                return AcceptResult.error(HttpStatus.GONE, "Sifariş təklifinin vaxtı bitib", true);
            }

            User courier = userRepository.findById(courierId)
                    .orElse(null);
            if (courier == null) {
                return AcceptResult.error(HttpStatus.NOT_FOUND, "Kuryer tapılmadı", false);
            }

            Set<Long> busyCourierIds = new HashSet<>(
                    orderRepository.findActiveCourierIds(ACTIVE_STATUSES)
            );
            if (!isCourierEligible(courier, busyCourierIds)) {
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Kuryer hazırda sifarişi qəbul edə bilmir",
                        false
                );
            }

            order.setCourierId(courierId);
            order.setStatus("to_customer");
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            orderRepository.save(order);

            courier.setCurrentlyDelivering(true);
            userRepository.save(courier);

            return AcceptResult.success(modelMapper.map(order, OrderDto.class));
        });

        if (outcome == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sifariş qəbul edilə bilmədi");
        }

        if (outcome.order() != null) {
            return outcome.order();
        }

        if (outcome.redispatch()) {
            processOrder(orderId);
        }

        throw new ResponseStatusException(outcome.status(), outcome.message());
    }

    public OrderDto declineOffer(Long orderId, Long courierId) {
        requireCourierId(courierId);

        DeclineResult result = transactionTemplate.execute(status -> {
            Order order = lockOrder(orderId);

            if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus())) {
                throw conflict("Sifariş artıq mövcud deyil");
            }

            if (!Objects.equals(order.getOfferedCourierId(), courierId)) {
                throw conflict("Bu sifariş artıq sizə təklif olunmur");
            }

            User courier = userRepository.findById(courierId).orElse(null);
            PushTarget stopTarget = pushTarget(courier);

            addCancelledCourier(order, courierId);
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            orderRepository.save(order);

            return new DeclineResult(modelMapper.map(order, OrderDto.class), stopTarget);
        });

        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sifarişdən imtina edilə bilmədi");
        }

        if (result.stopTarget() != null) {
            pushService.sendOrderStop(
                    result.stopTarget().courierId(),
                    result.stopTarget().subscriptionId(),
                    orderId,
                    "offer_declined",
                    NO_COURIER,
                    false
            );
        }

        // Do not wait for the scheduler. The next nearest courier is selected now.
        processOrder(orderId);
        return getOrderDto(orderId, result.order());
    }

    /**
     * An already accepted courier cancels the ride. Search restarts with a fresh
     * five-minute deadline and the cancelling courier is excluded.
     */
    public OrderDto cancelAcceptedOrder(Long orderId, Long courierId) {
        requireCourierId(courierId);

        OrderDto result = transactionTemplate.execute(status -> {
            Date now = new Date();
            Order order = lockOrder(orderId);

            if (Boolean.TRUE.equals(order.getIsDisable()) || !ACTIVE_STATUSES.contains(order.getStatus())) {
                throw conflict("Aktiv sifariş tapılmadı");
            }

            if (!Objects.equals(order.getCourierId(), courierId)) {
                throw conflict("Sifariş bu kuryerə aid deyil");
            }

            addCancelledCourier(order, courierId);
            order.setCourierId(null);
            order.setStatus(NO_COURIER);
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            order.setSearchExpiresAt(new Date(now.getTime() + searchTimeoutMillis));
            order.setIsDisable(false);
            orderRepository.save(order);

            userRepository.findById(courierId).ifPresent(courier -> {
                courier.setCurrentlyDelivering(false);
                userRepository.save(courier);
            });

            return modelMapper.map(order, OrderDto.class);
        });

        processOrder(orderId);
        return getOrderDto(orderId, result);
    }

    /**
     * Used by both the new cancel endpoint and legacy PUT {isDisable:true}.
     * Stops a pending offer or an assigned courier and prevents further dispatch.
     */
    public OrderDto cancelOrder(Long orderId) {
        CancelResult result = transactionTemplate.execute(status -> {
            Order order = lockOrder(orderId);
            Long targetCourierId = order.getCourierId() != null
                    ? order.getCourierId()
                    : order.getOfferedCourierId();
            boolean accepted = order.getCourierId() != null && ACTIVE_STATUSES.contains(order.getStatus());

            User target = targetCourierId == null
                    ? null
                    : userRepository.findById(targetCourierId).orElse(null);

            order.setIsDisable(true);
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            orderRepository.save(order);

            if (accepted && target != null) {
                target.setCurrentlyDelivering(false);
                userRepository.save(target);
            }

            return new CancelResult(
                    modelMapper.map(order, OrderDto.class),
                    pushTarget(target),
                    accepted
            );
        });

        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sifariş ləğv edilə bilmədi");
        }

        if (result.target() != null) {
            pushService.sendOrderStop(
                    result.target().courierId(),
                    result.target().subscriptionId(),
                    orderId,
                    "customer_cancelled",
                    "cancelled",
                    result.accepted()
            );
        }

        return result.order();
    }

    private DispatchChange reserveNextCourier(Long orderId) {
        Date now = new Date();
        Order order = lockOrder(orderId);

        if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus()) || order.getCourierId() != null) {
            return DispatchChange.none(order.getOrderType());
        }

        normalizeSearchDeadline(order, now);

        PushTarget stopTarget = null;
        String stopEvent = "offer_expired";
        String stopStatus = NO_COURIER;

        if (isSearchExpired(order, now)) {
            if (order.getOfferedCourierId() != null) {
                User oldCourier = userRepository.findById(order.getOfferedCourierId()).orElse(null);
                stopTarget = pushTarget(oldCourier);
                addCancelledCourier(order, order.getOfferedCourierId());
            }
            disableExpiredOrder(order);
            orderRepository.save(order);
            return new DispatchChange(order.getOrderType(), null, stopTarget, "search_expired", "cancelled");
        }

        if (order.getOfferedCourierId() != null) {
            Long currentOfferId = order.getOfferedCourierId();
            User offeredCourier = userRepository.findById(currentOfferId).orElse(null);
            Set<Long> activeCourierIds = new HashSet<>(orderRepository.findActiveCourierIds(ACTIVE_STATUSES));

            boolean offerStillTimed = order.getOfferExpiresAt() != null && order.getOfferExpiresAt().after(now);
            boolean courierStillEligible = offeredCourier != null && isCourierEligible(offeredCourier, activeCourierIds);

            if (offerStillTimed && courierStillEligible) {
                return DispatchChange.none(order.getOrderType());
            }

            stopTarget = pushTarget(offeredCourier);
            stopEvent = offerStillTimed ? "offer_unavailable" : "offer_expired";
            addCancelledCourier(order, currentOfferId);
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            orderRepository.save(order);
        }

        GeoPoint pickup = parsePickup(order.getFromAddress());
        if (pickup == null) {
            // Invalid pickup can never be dispatched. Disable instead of leaving a
            // permanent no_courier order in the scheduler.
            order.setIsDisable(true);
            orderRepository.save(order);
            return new DispatchChange(order.getOrderType(), null, stopTarget, "order_unavailable", "cancelled");
        }

        Set<Long> excluded = new HashSet<>();
        if (order.getCancelledCourierIds() != null) {
            excluded.addAll(order.getCancelledCourierIds());
        }
        excluded.addAll(orderRepository.findActiveCourierIds(ACTIVE_STATUSES));
        excluded.addAll(orderRepository.findCurrentlyOfferedCourierIds(NO_COURIER, now));

        List<CourierCandidate> candidates = userRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(user -> isCourierEligible(user, excluded))
                .map(user -> candidate(user, pickup))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CourierCandidate::distanceKm))
                .toList();

        if (candidates.isEmpty()) {
            // Keep the order open. Scheduler retries every few seconds, so a courier
            // who comes online during the five-minute window can receive it.
            orderRepository.save(order);
            return new DispatchChange(order.getOrderType(), null, stopTarget, stopEvent, stopStatus);
        }

        CourierCandidate selected = candidates.get(0);
        order.setOfferedCourierId(selected.user().getId());
        order.setOfferExpiresAt(new Date(now.getTime() + offerTimeoutMillis));
        orderRepository.save(order);

        return new DispatchChange(
                order.getOrderType(),
                pushTarget(selected.user()),
                stopTarget,
                stopEvent,
                stopStatus
        );
    }

    private boolean isCourierEligible(User user, Set<Long> busyOrExcludedCourierIds) {
        if (user == null || user.getId() == null) {
            return false;
        }
        if (busyOrExcludedCourierIds.contains(user.getId())) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getIsDisable())) {
            return false;
        }
        if (!Boolean.TRUE.equals(user.getOnline())) {
            return false;
        }
        if (!"accept".equalsIgnoreCase(trim(user.getCourierStatus()))) {
            return false;
        }
        if ("customer".equalsIgnoreCase(trim(user.getUserType()))) {
            return false;
        }
        return parseCourierLocation(user) != null;
    }

    private CourierCandidate candidate(User user, GeoPoint pickup) {
        GeoPoint courier = parseCourierLocation(user);
        if (courier == null) {
            return null;
        }
        return new CourierCandidate(user, haversineKm(pickup, courier));
    }

    private GeoPoint parseCourierLocation(User user) {
        GeoPoint point = parseLatLng(user.getCurrentLocation(), 0, 1);
        return point != null ? point : parseLatLng(user.getLocation(), 0, 1);
    }

    private GeoPoint parsePickup(List<String> fromAddress) {
        return parseLatLng(fromAddress, 1, 2);
    }

    private GeoPoint parseLatLng(List<String> values, int latIndex, int lngIndex) {
        if (values == null || values.size() <= Math.max(latIndex, lngIndex)) {
            return null;
        }
        try {
            double lat = Double.parseDouble(values.get(latIndex).trim());
            double lng = Double.parseDouble(values.get(lngIndex).trim());
            if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return null;
            }
            return new GeoPoint(lat, lng);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double haversineKm(GeoPoint a, GeoPoint b) {
        final double earthRadiusKm = 6371.0;
        double latDelta = Math.toRadians(b.latitude() - a.latitude());
        double lngDelta = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());

        double value = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(lngDelta / 2) * Math.sin(lngDelta / 2);

        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private void normalizeSearchDeadline(Order order, Date now) {
        if (order.getSearchExpiresAt() != null) {
            return;
        }
        Date base = order.getCreatedAt() != null ? order.getCreatedAt() : now;
        order.setSearchExpiresAt(new Date(base.getTime() + searchTimeoutMillis));
    }

    private boolean isSearchExpired(Order order, Date now) {
        return order.getSearchExpiresAt() != null && !order.getSearchExpiresAt().after(now);
    }

    private void disableExpiredOrder(Order order) {
        order.setIsDisable(true);
        order.setOfferedCourierId(null);
        order.setOfferExpiresAt(null);
    }

    private void expireCurrentOffer(Order order) {
        if (order.getOfferedCourierId() != null) {
            addCancelledCourier(order, order.getOfferedCourierId());
        }
        order.setOfferedCourierId(null);
        order.setOfferExpiresAt(null);
    }

    private void addCancelledCourier(Order order, Long courierId) {
        if (courierId == null) {
            return;
        }
        List<Long> cancelled = order.getCancelledCourierIds() == null
                ? new ArrayList<>()
                : new ArrayList<>(order.getCancelledCourierIds());
        if (!cancelled.contains(courierId)) {
            cancelled.add(courierId);
        }
        order.setCancelledCourierIds(cancelled);
    }

    private PushTarget pushTarget(User courier) {
        if (courier == null || courier.getId() == null) {
            return null;
        }
        return new PushTarget(courier.getId(), courier.getOneSignal());
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sifariş tapılmadı"));
    }

    private void requireCourierId(Long courierId) {
        if (courierId == null || courierId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courierId tələb olunur");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private OrderDto getOrderDto(Long orderId, OrderDto fallback) {
        return orderRepository.findById(orderId)
                .map(order -> modelMapper.map(order, OrderDto.class))
                .orElse(fallback);
    }

    private record GeoPoint(double latitude, double longitude) {}
    private record CourierCandidate(User user, double distanceKm) {}
    private record PushTarget(Long courierId, String subscriptionId) {}
    private record DispatchChange(
            String orderType,
            PushTarget offerTarget,
            PushTarget stopTarget,
            String stopEvent,
            String stopStatus
    ) {
        static DispatchChange none(String orderType) {
            return new DispatchChange(orderType, null, null, "order_unavailable", NO_COURIER);
        }
    }
    private record AcceptResult(
            OrderDto order,
            HttpStatus status,
            String message,
            boolean redispatch
    ) {
        static AcceptResult success(OrderDto order) {
            return new AcceptResult(order, null, null, false);
        }

        static AcceptResult error(HttpStatus status, String message, boolean redispatch) {
            return new AcceptResult(null, status, message, redispatch);
        }
    }
    private record DeclineResult(OrderDto order, PushTarget stopTarget) {}
    private record CancelResult(OrderDto order, PushTarget target, boolean accepted) {}
}

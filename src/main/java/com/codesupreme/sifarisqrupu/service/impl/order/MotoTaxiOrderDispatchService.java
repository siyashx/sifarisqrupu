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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final long fanoutIntervalMillis;
    private final Object dispatchMutex = new Object();

    public MotoTaxiOrderDispatchService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            ModelMapper modelMapper,
            MotoTaxiCourierPushService pushService,
            PlatformTransactionManager transactionManager,
            @Value("${mototaxi.dispatch.search-timeout-seconds:300}") long searchTimeoutSeconds,
            @Value("${mototaxi.dispatch.offer-timeout-seconds:60}") long offerTimeoutSeconds,
            @Value("${mototaxi.dispatch.fanout-interval-seconds:3}") long fanoutIntervalSeconds
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.pushService = pushService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.searchTimeoutMillis = Math.max(1, searchTimeoutSeconds) * 1000L;
        this.offerTimeoutMillis = Math.max(1, offerTimeoutSeconds) * 1000L;
        this.fanoutIntervalMillis = Math.max(1, fanoutIntervalSeconds) * 1000L;
    }

    /**
     * Multi-courier fan-out dispatch:
     * - the nearest eligible courier receives the first offer immediately;
     * - every fan-out interval another nearest courier can receive the same order;
     * - every courier keeps its own offer until its individual expiry;
     * - the first courier that accepts wins atomically and all other offers stop.
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

        sendStops(
                change.stopTargets(),
                orderId,
                change.stopEvent(),
                change.stopStatus(),
                false
        );

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
            normalizeLegacyOfferState(order, now);

            if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus())) {
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Sifariş artıq mövcud deyil",
                        false,
                        List.of(),
                        "order_unavailable",
                        NO_COURIER
                );
            }

            if (isSearchExpired(order, now)) {
                List<PushTarget> stopTargets = collectActiveOfferTargets(order, null);
                markAllActiveOffersCancelled(order);
                disableExpiredOrder(order);
                orderRepository.save(order);
                return AcceptResult.error(
                        HttpStatus.GONE,
                        "Sifariş üçün axtarış vaxtı bitib",
                        false,
                        stopTargets,
                        "search_expired",
                        "cancelled"
                );
            }

            Date courierOfferExpiry = activeOffers(order).get(courierId);
            if (courierOfferExpiry == null) {
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Bu sifariş hazırda sizə təklif olunmur",
                        false,
                        List.of(),
                        "order_unavailable",
                        NO_COURIER
                );
            }

            if (!courierOfferExpiry.after(now)) {
                User courier = userRepository.findById(courierId).orElse(null);
                PushTarget stopTarget = pushTarget(courier);
                expireCourierOffer(order, courierId);
                orderRepository.save(order);
                return AcceptResult.error(
                        HttpStatus.GONE,
                        "Sifariş təklifinin vaxtı bitib",
                        true,
                        stopTarget == null ? List.of() : List.of(stopTarget),
                        "offer_expired",
                        NO_COURIER
                );
            }

            User courier = userRepository.findById(courierId).orElse(null);
            if (courier == null) {
                return AcceptResult.error(
                        HttpStatus.NOT_FOUND,
                        "Kuryer tapılmadı",
                        false,
                        List.of(),
                        "order_unavailable",
                        NO_COURIER
                );
            }

            Set<Long> busyCourierIds = new HashSet<>(orderRepository.findActiveCourierIds(ACTIVE_STATUSES));
            if (!isCourierEligible(courier, busyCourierIds)) {
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Kuryer hazırda sifarişi qəbul edə bilmir",
                        false,
                        List.of(),
                        "order_unavailable",
                        NO_COURIER
                );
            }

            List<PushTarget> otherOfferTargets = collectActiveOfferTargets(order, courierId);

            order.setCourierId(courierId);
            order.setStatus("to_customer");
            clearOfferState(order);
            orderRepository.save(order);

            courier.setCurrentlyDelivering(true);
            userRepository.save(courier);

            return AcceptResult.success(
                    modelMapper.map(order, OrderDto.class),
                    otherOfferTargets,
                    "order_taken",
                    "to_customer"
            );
        });

        if (outcome == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sifariş qəbul edilə bilmədi");
        }

        sendStops(
                outcome.stopTargets(),
                orderId,
                outcome.stopEvent(),
                outcome.stopStatus(),
                false
        );

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
            Date now = new Date();
            Order order = lockOrder(orderId);
            normalizeLegacyOfferState(order, now);

            if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus())) {
                throw conflict("Sifariş artıq mövcud deyil");
            }

            if (!activeOffers(order).containsKey(courierId)) {
                throw conflict("Bu sifariş artıq sizə təklif olunmur");
            }

            User courier = userRepository.findById(courierId).orElse(null);
            PushTarget stopTarget = pushTarget(courier);

            activeOffers(order).remove(courierId);
            addCancelledCourier(order, courierId);
            syncLegacyOffer(order);
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

        // The 3-second fan-out clock remains authoritative. processOrder can
        // dispatch immediately only when that interval has already elapsed.
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
            clearOfferState(order);
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
     * Stops every pending offer or an assigned courier and prevents further dispatch.
     */
    public OrderDto cancelOrder(Long orderId) {
        CancelResult result = transactionTemplate.execute(status -> {
            Date now = new Date();
            Order order = lockOrder(orderId);
            normalizeLegacyOfferState(order, now);

            boolean accepted = order.getCourierId() != null && ACTIVE_STATUSES.contains(order.getStatus());
            User acceptedCourier = accepted
                    ? userRepository.findById(order.getCourierId()).orElse(null)
                    : null;

            List<PushTarget> pendingTargets = collectActiveOfferTargets(order, null);

            order.setIsDisable(true);
            clearOfferState(order);
            orderRepository.save(order);

            if (acceptedCourier != null) {
                acceptedCourier.setCurrentlyDelivering(false);
                userRepository.save(acceptedCourier);
            }

            return new CancelResult(
                    modelMapper.map(order, OrderDto.class),
                    pendingTargets,
                    pushTarget(acceptedCourier),
                    accepted
            );
        });

        if (result == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sifariş ləğv edilə bilmədi");
        }

        sendStops(
                result.pendingTargets(),
                orderId,
                "customer_cancelled",
                "cancelled",
                false
        );

        if (result.acceptedTarget() != null) {
            pushService.sendOrderStop(
                    result.acceptedTarget().courierId(),
                    result.acceptedTarget().subscriptionId(),
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
        normalizeLegacyOfferState(order, now);

        List<PushTarget> stopTargets = pruneExpiredOrUnavailableOffers(order, now);

        if (isSearchExpired(order, now)) {
            for (Long courierId : new ArrayList<>(activeOffers(order).keySet())) {
                addCancelledCourier(order, courierId);
            }
            stopTargets = mergeTargets(stopTargets, collectActiveOfferTargets(order, null));
            disableExpiredOrder(order);
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    null,
                    stopTargets,
                    "search_expired",
                    "cancelled"
            );
        }

        GeoPoint pickup = parsePickup(order.getFromAddress());
        if (pickup == null) {
            stopTargets = mergeTargets(stopTargets, collectActiveOfferTargets(order, null));
            markAllActiveOffersCancelled(order);
            order.setIsDisable(true);
            clearOfferState(order);
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    null,
                    stopTargets,
                    "order_unavailable",
                    "cancelled"
            );
        }

        Date lastOfferAt = order.getLastOfferAt();
        if (lastOfferAt != null && now.getTime() - lastOfferAt.getTime() < fanoutIntervalMillis) {
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    null,
                    stopTargets,
                    "offer_expired",
                    NO_COURIER
            );
        }

        Set<Long> excluded = new HashSet<>();
        if (order.getCancelledCourierIds() != null) {
            excluded.addAll(order.getCancelledCourierIds());
        }
        excluded.addAll(orderRepository.findActiveCourierIds(ACTIVE_STATUSES));
        excluded.addAll(orderRepository.findCurrentlyOfferedCourierIds(now));

        List<CourierCandidate> candidates = userRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(user -> isCourierEligible(user, excluded))
                .map(user -> candidate(user, pickup))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CourierCandidate::distanceKm))
                .toList();

        if (candidates.isEmpty()) {
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    null,
                    stopTargets,
                    "offer_expired",
                    NO_COURIER
            );
        }

        CourierCandidate selected = candidates.get(0);
        Date expiresAt = new Date(now.getTime() + offerTimeoutMillis);

        activeOffers(order).put(selected.user().getId(), expiresAt);
        order.setLastOfferAt(now);

        // Legacy projection for older app versions. New Flutter builds use the
        // per-courier activeOfferExpirations map.
        order.setOfferedCourierId(selected.user().getId());
        order.setOfferExpiresAt(expiresAt);
        orderRepository.save(order);

        return new DispatchChange(
                order.getOrderType(),
                pushTarget(selected.user()),
                stopTargets,
                "offer_expired",
                NO_COURIER
        );
    }

    private List<PushTarget> pruneExpiredOrUnavailableOffers(Order order, Date now) {
        Map<Long, Date> offers = activeOffers(order);
        if (offers.isEmpty()) {
            return List.of();
        }

        Set<Long> busyCourierIds = new HashSet<>(orderRepository.findActiveCourierIds(ACTIVE_STATUSES));
        List<PushTarget> stopTargets = new ArrayList<>();
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, Date> entry : offers.entrySet()) {
            Long courierId = entry.getKey();
            Date expiresAt = entry.getValue();
            User courier = courierId == null ? null : userRepository.findById(courierId).orElse(null);

            boolean expired = expiresAt == null || !expiresAt.after(now);
            boolean unavailable = courier == null || !isCourierEligible(courier, busyCourierIds);

            if (expired || unavailable) {
                toRemove.add(courierId);
                addCancelledCourier(order, courierId);
                PushTarget target = pushTarget(courier);
                if (target != null) {
                    stopTargets.add(target);
                }
            }
        }

        for (Long courierId : toRemove) {
            offers.remove(courierId);
        }

        syncLegacyOffer(order);
        return stopTargets;
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

    private Map<Long, Date> activeOffers(Order order) {
        Map<Long, Date> offers = order.getActiveOfferExpirations();
        if (offers == null) {
            offers = new LinkedHashMap<>();
            order.setActiveOfferExpirations(offers);
        }
        return offers;
    }

    private void normalizeLegacyOfferState(Order order, Date now) {
        Map<Long, Date> offers = activeOffers(order);
        Long legacyCourierId = order.getOfferedCourierId();
        Date legacyExpiry = order.getOfferExpiresAt();

        if (offers.isEmpty()
                && legacyCourierId != null
                && legacyExpiry != null
                && legacyExpiry.after(now)) {
            offers.put(legacyCourierId, legacyExpiry);
            if (order.getLastOfferAt() == null) {
                order.setLastOfferAt(now);
            }
        }

        syncLegacyOffer(order);
    }

    private void syncLegacyOffer(Order order) {
        Map.Entry<Long, Date> latest = activeOffers(order).entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (latest == null) {
            order.setOfferedCourierId(null);
            order.setOfferExpiresAt(null);
            return;
        }

        order.setOfferedCourierId(latest.getKey());
        order.setOfferExpiresAt(latest.getValue());
    }

    private void expireCourierOffer(Order order, Long courierId) {
        activeOffers(order).remove(courierId);
        addCancelledCourier(order, courierId);
        syncLegacyOffer(order);
    }

    private void markAllActiveOffersCancelled(Order order) {
        for (Long courierId : new ArrayList<>(activeOffers(order).keySet())) {
            addCancelledCourier(order, courierId);
        }
    }

    private void disableExpiredOrder(Order order) {
        order.setIsDisable(true);
        clearOfferState(order);
    }

    private void clearOfferState(Order order) {
        activeOffers(order).clear();
        order.setOfferedCourierId(null);
        order.setOfferExpiresAt(null);
        order.setLastOfferAt(null);
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

    private List<PushTarget> collectActiveOfferTargets(Order order, Long excludeCourierId) {
        List<PushTarget> targets = new ArrayList<>();
        for (Long courierId : activeOffers(order).keySet()) {
            if (courierId == null || Objects.equals(courierId, excludeCourierId)) {
                continue;
            }
            userRepository.findById(courierId)
                    .map(this::pushTarget)
                    .filter(Objects::nonNull)
                    .ifPresent(targets::add);
        }
        return targets;
    }

    private List<PushTarget> mergeTargets(List<PushTarget> first, List<PushTarget> second) {
        Map<Long, PushTarget> merged = new LinkedHashMap<>();
        for (PushTarget target : first) {
            if (target != null) {
                merged.put(target.courierId(), target);
            }
        }
        for (PushTarget target : second) {
            if (target != null) {
                merged.put(target.courierId(), target);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void sendStops(
            List<PushTarget> targets,
            Long orderId,
            String event,
            String status,
            boolean showCustomerCancelledMessage
    ) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (PushTarget target : targets) {
            pushService.sendOrderStop(
                    target.courierId(),
                    target.subscriptionId(),
                    orderId,
                    event,
                    status,
                    showCustomerCancelledMessage
            );
        }
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
            List<PushTarget> stopTargets,
            String stopEvent,
            String stopStatus
    ) {
        static DispatchChange none(String orderType) {
            return new DispatchChange(orderType, null, List.of(), "order_unavailable", NO_COURIER);
        }
    }

    private record AcceptResult(
            OrderDto order,
            HttpStatus status,
            String message,
            boolean redispatch,
            List<PushTarget> stopTargets,
            String stopEvent,
            String stopStatus
    ) {
        static AcceptResult success(
                OrderDto order,
                List<PushTarget> stopTargets,
                String stopEvent,
                String stopStatus
        ) {
            return new AcceptResult(order, null, null, false, stopTargets, stopEvent, stopStatus);
        }

        static AcceptResult error(
                HttpStatus status,
                String message,
                boolean redispatch,
                List<PushTarget> stopTargets,
                String stopEvent,
                String stopStatus
        ) {
            return new AcceptResult(null, status, message, redispatch, stopTargets, stopEvent, stopStatus);
        }
    }

    private record DeclineResult(OrderDto order, PushTarget stopTarget) {}

    private record CancelResult(
            OrderDto order,
            List<PushTarget> pendingTargets,
            PushTarget acceptedTarget,
            boolean accepted
    ) {}
}

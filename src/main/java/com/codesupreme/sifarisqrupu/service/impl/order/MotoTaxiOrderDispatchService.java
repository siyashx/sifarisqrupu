package com.codesupreme.sifarisqrupu.service.impl.order;

import com.codesupreme.sifarisqrupu.dao.order.OrderRepository;
import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.dto.order.OrderDto;
import com.codesupreme.sifarisqrupu.model.order.Order;
import com.codesupreme.sifarisqrupu.model.user.User;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiCourierPushService;
import com.codesupreme.sifarisqrupu.service.impl.mototaxi.MotoTaxiPricingService;
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
    private final MotoTaxiPricingService pricingService;
    private final TransactionTemplate transactionTemplate;
    private final long searchTimeoutMillis;
    private final long offerTimeoutMillis;
    private final Object dispatchMutex = new Object();

    public MotoTaxiOrderDispatchService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            ModelMapper modelMapper,
            MotoTaxiCourierPushService pushService,
            MotoTaxiPricingService pricingService,
            PlatformTransactionManager transactionManager,
            @Value("${mototaxi.dispatch.search-timeout-seconds:300}") long searchTimeoutSeconds,
            @Value("${mototaxi.dispatch.offer-timeout-seconds:60}") long offerTimeoutSeconds
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.pushService = pushService;
        this.pricingService = pricingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.searchTimeoutMillis = Math.max(1, searchTimeoutSeconds) * 1000L;
        this.offerTimeoutMillis = Math.max(1, offerTimeoutSeconds) * 1000L;
    }

    /**
     * Radius-based broadcast dispatch:
     * - pickup -> courier distance is measured as a straight-line Haversine distance;
     * - the maximum radius is read from the singleton mototaxi_pricing DB row;
     * - every currently eligible courier inside that radius receives the same order at once;
     * - the first courier that accepts wins atomically and every other active offer stops.
     */
    public void processOrder(Long orderId) {
        if (orderId == null) {
            return;
        }

        DispatchChange change;
        synchronized (dispatchMutex) {
            change = transactionTemplate.execute(status -> reserveEligibleCouriers(orderId));
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

        for (PushTarget target : change.offerTargets()) {
            pushService.sendNewOrderOffer(
                    target.courierId(),
                    target.subscriptionId(),
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

            GeoPoint pickup = parsePickup(order.getFromAddress());
            double dispatchRadiusKm = pricingService.getDispatchRadiusKm();
            if (pickup == null || !isCourierWithinRadius(courier, pickup, dispatchRadiusKm)) {
                PushTarget stopTarget = pushTarget(courier);
                expireCourierOffer(order, courierId);
                orderRepository.save(order);
                return AcceptResult.error(
                        HttpStatus.CONFLICT,
                        "Sifariş kuryerin cari məsafə limitindən kənardadır",
                        false,
                        stopTarget == null ? List.of() : List.of(stopTarget),
                        "order_out_of_radius",
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

    public List<OrderDto> getVisibleOrdersForCourier(Long courierId) {
        requireCourierId(courierId);

        List<OrderDto> result = transactionTemplate.execute(status -> {
            Date now = new Date();
            User courier = userRepository.findById(courierId).orElse(null);
            if (courier == null || Boolean.TRUE.equals(courier.getIsDisable())) {
                return List.<OrderDto>of();
            }

            double dispatchRadiusKm = pricingService.getDispatchRadiusKm();
            GeoPoint courierPoint = parseCourierLocation(courier);

            return orderRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(order -> !Boolean.TRUE.equals(order.getIsDisable()))
                    .filter(order -> {
                        if (Objects.equals(order.getCourierId(), courierId)
                                && ACTIVE_STATUSES.contains(order.getStatus())) {
                            return true;
                        }

                        if (!NO_COURIER.equals(order.getStatus())
                                || order.getCourierId() != null
                                || courierPoint == null
                                || wasCancelledBy(order, courierId)
                                || !hasActiveOfferForCourier(order, courierId, now)) {
                            return false;
                        }

                        GeoPoint pickup = parsePickup(order.getFromAddress());
                        return pickup != null
                                && haversineKm(pickup, courierPoint) <= dispatchRadiusKm;
                    })
                    .map(order -> modelMapper.map(order, OrderDto.class))
                    .toList();
        });

        return result == null ? List.of() : result;
    }

    private boolean hasActiveOfferForCourier(Order order, Long courierId, Date now) {
        Date expiry = order.getActiveOfferExpirations() == null
                ? null
                : order.getActiveOfferExpirations().get(courierId);

        if (expiry == null && Objects.equals(order.getOfferedCourierId(), courierId)) {
            expiry = order.getOfferExpiresAt();
        }

        return expiry != null && expiry.after(now);
    }

    private boolean wasCancelledBy(Order order, Long courierId) {
        return order.getCancelledCourierIds() != null
                && order.getCancelledCourierIds().contains(courierId);
    }

    private DispatchChange reserveEligibleCouriers(Long orderId) {
        Date now = new Date();
        Order order = lockOrder(orderId);

        if (Boolean.TRUE.equals(order.getIsDisable()) || !NO_COURIER.equals(order.getStatus()) || order.getCourierId() != null) {
            return DispatchChange.none(order.getOrderType());
        }

        normalizeSearchDeadline(order, now);
        normalizeLegacyOfferState(order, now);

        GeoPoint pickup = parsePickup(order.getFromAddress());
        if (pickup == null) {
            List<PushTarget> stopTargets = collectActiveOfferTargets(order, null);
            markAllActiveOffersCancelled(order);
            order.setIsDisable(true);
            clearOfferState(order);
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    List.of(),
                    stopTargets,
                    "order_unavailable",
                    "cancelled"
            );
        }

        double dispatchRadiusKm = pricingService.getDispatchRadiusKm();
        List<PushTarget> stopTargets = pruneExpiredOrUnavailableOffers(
                order,
                now,
                pickup,
                dispatchRadiusKm
        );

        if (isSearchExpired(order, now)) {
            for (Long courierId : new ArrayList<>(activeOffers(order).keySet())) {
                addCancelledCourier(order, courierId);
            }
            stopTargets = mergeTargets(stopTargets, collectActiveOfferTargets(order, null));
            disableExpiredOrder(order);
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    List.of(),
                    stopTargets,
                    "search_expired",
                    "cancelled"
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
                .filter(candidate -> candidate.distanceKm() <= dispatchRadiusKm)
                .toList();

        if (candidates.isEmpty()) {
            orderRepository.save(order);
            return new DispatchChange(
                    order.getOrderType(),
                    List.of(),
                    stopTargets,
                    "offer_expired",
                    NO_COURIER
            );
        }

        Date expiresAt = new Date(now.getTime() + offerTimeoutMillis);
        List<PushTarget> offerTargets = new ArrayList<>();

        // All candidates are reserved in the same locked transaction. No courier
        // gets a timing advantage from list ordering; every offer has one expiry.
        for (CourierCandidate candidate : candidates) {
            Long courierId = candidate.user().getId();
            activeOffers(order).put(courierId, expiresAt);

            PushTarget target = pushTarget(candidate.user());
            if (target != null) {
                offerTargets.add(target);
            }
        }

        order.setLastOfferAt(now);
        syncLegacyOffer(order);
        orderRepository.save(order);

        return new DispatchChange(
                order.getOrderType(),
                offerTargets,
                stopTargets,
                "offer_expired",
                NO_COURIER
        );
    }

    private List<PushTarget> pruneExpiredOrUnavailableOffers(
            Order order,
            Date now,
            GeoPoint pickup,
            double dispatchRadiusKm
    ) {
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
            boolean unavailable = courier == null
                    || !isCourierEligible(courier, busyCourierIds)
                    || !isCourierWithinRadius(courier, pickup, dispatchRadiusKm);

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

    private boolean isCourierWithinRadius(User user, GeoPoint pickup, double radiusKm) {
        CourierCandidate candidate = candidate(user, pickup);
        return candidate != null
                && Double.isFinite(radiusKm)
                && radiusKm > 0
                && candidate.distanceKm() <= radiusKm;
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
            List<PushTarget> offerTargets,
            List<PushTarget> stopTargets,
            String stopEvent,
            String stopStatus
    ) {
        static DispatchChange none(String orderType) {
            return new DispatchChange(orderType, List.of(), List.of(), "order_unavailable", NO_COURIER);
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

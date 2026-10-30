package com.cineverse.booking;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.BookingDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.EventStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.*;
import com.cineverse.service.AuditService;
import com.cineverse.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Quantity-based inventory for events, sports, plays and activities. Tickets move
 * capacity -> held (on booking) -> sold (on payment), all under row locks on the category rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBookingService {
    private final EventSessionRepository sessionRepository;
    private final EventTicketCategoryRepository categoryRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final PricingService pricingService;
    private final BookingMapper mapper;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public BookingResponse create(Long userId, CreateEventBookingRequest req) {
        Instant now = Instant.now(clock);
        EventSession session = sessionRepository.findById(req.sessionId()).orElseThrow(() -> ApiException.notFound("Event session"));
        if (session.getEvent().getStatus() != EventStatus.PUBLISHED || !"SCHEDULED".equals(session.getStatus())
                || !session.getStartTime().isAfter(now)) {
            throw new ApiException(ErrorCode.SHOW_NOT_BOOKABLE, "This event is no longer open for booking");
        }

        Map<Long, Integer> wanted = new TreeMap<>();
        req.tickets().forEach(t -> wanted.merge(t.categoryId(), t.quantity(), Integer::sum));
        List<EventTicketCategory> categories = categoryRepository.lockByIds(wanted.keySet());
        if (categories.size() != wanted.size() || categories.stream().anyMatch(c -> !c.getSession().getId().equals(session.getId()))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid ticket category for this session");
        }

        Booking b = new Booking();
        b.setReference(bookingService.nextReference());
        b.setUser(userRepository.getReferenceById(userId));
        b.setBookingType(BookingType.EVENT);
        b.setEventSession(session);
        b.setStatus(BookingStatus.PENDING);
        b.setExpiresAt(now.plus(props.eventHold().duration()));

        for (EventTicketCategory c : categories) {
            int qty = wanted.get(c.getId());
            if (qty > c.getMaxPerBooking()) {
                throw new ApiException(ErrorCode.SEAT_LIMIT_EXCEEDED, "Maximum " + c.getMaxPerBooking() + " " + c.getName() + " tickets per booking");
            }
            if (c.remaining() < qty) {
                throw new ApiException(ErrorCode.INVENTORY_UNAVAILABLE,
                        c.remaining() == 0 ? c.getName() + " is sold out" : "Only " + c.remaining() + " " + c.getName() + " tickets left");
            }
            c.setHeld(c.getHeld() + qty);
            BookingItem item = new BookingItem();
            item.setBooking(b);
            item.setTicketCategoryId(c.getId());
            item.setCategoryName(c.getName());
            item.setUnitPrice(c.getPrice());
            item.setQuantity(qty);
            b.getItems().add(item);
        }
        pricingService.reprice(b);
        bookingRepository.save(b);
        auditService.record(userId, "USER_CREATED_BOOKING", "BOOKING", b.getId(), Map.of("reference", b.getReference(), "sessionId", session.getId()));
        log.info("event_booking_created bookingId={} sessionId={} tickets={}", b.getId(), session.getId(), wanted);
        return mapper.toResponse(b, false);
    }

    /** held -> sold. Returns false if the hold lapsed and the tickets were resold meanwhile. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean commitInventory(Booking b, boolean holdStillActive) {
        List<EventTicketCategory> cats = categoryRepository.lockByIds(b.getItems().stream().map(BookingItem::getTicketCategoryId).toList());
        Map<Long, EventTicketCategory> byId = new HashMap<>();
        cats.forEach(c -> byId.put(c.getId(), c));
        if (!holdStillActive) {
            for (BookingItem i : b.getItems()) {
                if (byId.get(i.getTicketCategoryId()).remaining() < i.getQuantity()) return false;
            }
        }
        for (BookingItem i : b.getItems()) {
            EventTicketCategory c = byId.get(i.getTicketCategoryId());
            if (holdStillActive) c.setHeld(Math.max(0, c.getHeld() - i.getQuantity()));
            c.setSold(c.getSold() + i.getQuantity());
        }
        return true;
    }

    /** Releases a hold (expired/abandoned booking) or returns sold tickets (cancellation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseInventory(Booking b, boolean wasSold) {
        List<EventTicketCategory> cats = categoryRepository.lockByIds(b.getItems().stream().map(BookingItem::getTicketCategoryId).toList());
        Map<Long, EventTicketCategory> byId = new HashMap<>();
        cats.forEach(c -> byId.put(c.getId(), c));
        for (BookingItem i : b.getItems()) {
            EventTicketCategory c = byId.get(i.getTicketCategoryId());
            if (wasSold) c.setSold(Math.max(0, c.getSold() - i.getQuantity()));
            else c.setHeld(Math.max(0, c.getHeld() - i.getQuantity()));
        }
    }
}

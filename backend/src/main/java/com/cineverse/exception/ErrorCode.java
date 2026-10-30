package com.cineverse.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token is invalid or expired"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is disabled"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Show not found"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    SEAT_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "One or more seats do not exist for this show"),
    SEAT_ALREADY_BOOKED(HttpStatus.CONFLICT, "One or more selected seats are no longer available"),
    SEAT_LOCKED(HttpStatus.CONFLICT, "One or more selected seats are being booked by another customer"),
    SEAT_LOCK_EXPIRED(HttpStatus.CONFLICT, "Your seat reservation has expired"),
    SEAT_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Too many seats selected"),
    SHOW_NOT_BOOKABLE(HttpStatus.UNPROCESSABLE_ENTITY, "This show is no longer open for booking"),
    SHOW_OVERLAP(HttpStatus.CONFLICT, "The screen already has a show during this time"),
    INVENTORY_UNAVAILABLE(HttpStatus.CONFLICT, "Not enough tickets remaining in this category"),
    BOOKING_EXPIRED(HttpStatus.CONFLICT, "This booking has expired"),
    BOOKING_STATE_INVALID(HttpStatus.CONFLICT, "Booking is not in a valid state for this operation"),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "Payment failed"),
    PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Payment method is not available right now"),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "Payment could not be verified"),
    PAY_AT_VENUE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Pay at venue is not available for this booking"),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "Webhook signature verification failed"),
    COUPON_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon is not valid"),
    COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Coupon has expired"),
    CANCELLATION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "This booking can no longer be cancelled"),
    TICKET_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Ticket is not valid"),
    REVIEW_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "You can review a movie after watching it"),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "You have already reviewed this movie"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "A request with this Idempotency-Key is already being processed"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key was already used with a different request"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with current state"),
    MEDIA_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Poster and trailer lookup is not available right now"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please slow down"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }

    public String defaultMessage() { return defaultMessage; }
}

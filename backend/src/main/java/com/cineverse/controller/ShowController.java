package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.dto.ShowDtos.*;
import com.cineverse.seat.SeatLockService;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Shows & Seats")
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController {
    private final ShowService showService;
    private final SeatLockService seatLockService;

    @Operation(summary = "Show details with live seat layout, availability and pricing")
    @GetMapping("/{showId}")
    public ApiResponse<ShowResponse> show(@PathVariable Long showId) {
        return ApiResponse.ok(showService.get(showId, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "Lightweight seat status refresh")
    @GetMapping("/{showId}/seats")
    public ApiResponse<SeatAvailabilityResponse> seats(@PathVariable Long showId) {
        return ApiResponse.ok(showService.availability(showId, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "Temporarily reserve seats (5 minutes). 409 SEAT_LOCKED / SEAT_ALREADY_BOOKED if taken.")
    @PostMapping("/{showId}/seat-lock")
    public ApiResponse<SeatLockResponse> lock(@PathVariable Long showId, @Valid @RequestBody SeatLockRequest req) {
        return ApiResponse.ok(seatLockService.lockSeats(showId, SecurityUtils.requireUser().id(), req.seatIds()), "Seats reserved");
    }

    @DeleteMapping("/{showId}/seat-lock/{lockId}")
    public ApiResponse<Void> release(@PathVariable Long showId, @PathVariable UUID lockId) {
        seatLockService.releaseByUser(showId, lockId, SecurityUtils.requireUser().id());
        return ApiResponse.ok(null, "Seats released");
    }
}

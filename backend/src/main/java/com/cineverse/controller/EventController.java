package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.EventDtos.*;
import com.cineverse.entity.enums.EventVertical;
import com.cineverse.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Events, Sports, Plays & Activities")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @GetMapping("/events")
    public ApiResponse<PageResponse<EventResponse>> events(@RequestParam(required = false) String city, @RequestParam(required = false) String category,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(required = false) String q, @RequestParam(required = false) EventVertical vertical,
                                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return list(vertical, city, category, date, q, page, size);
    }

    @GetMapping("/sports")
    public ApiResponse<PageResponse<EventResponse>> sports(@RequestParam(required = false) String city, @RequestParam(required = false) String category,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return list(EventVertical.SPORTS, city, category, date, null, page, size);
    }

    @GetMapping("/plays")
    public ApiResponse<PageResponse<EventResponse>> plays(@RequestParam(required = false) String city,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return list(EventVertical.PLAY, city, null, date, null, page, size);
    }

    @GetMapping("/activities")
    public ApiResponse<PageResponse<EventResponse>> activities(@RequestParam(required = false) String city, @RequestParam(required = false) String category,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return list(EventVertical.ACTIVITY, city, category, date, null, page, size);
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventService.detail(id));
    }

    private ApiResponse<PageResponse<EventResponse>> list(EventVertical v, String city, String category, LocalDate date, String q, int page, int size) {
        return ApiResponse.ok(PageResponse.of(eventService.list(v, city, category, date, q, page, size)));
    }
}

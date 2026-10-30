# CineVerse architecture notes

## Request path

```
Browser ─► Next.js page (client components + TanStack Query)
        ─► services/index.ts (the only place API URLs exist; axios with bearer token + single-flight refresh)
        ─► Spring Security filter chain
             RequestLoggingFilter (request id, method, path, status, duration, user id — no bodies)
             RateLimitFilter (per IP; tighter on /auth)
             JwtAuthenticationFilter (stateless; roles from the token)
             URL authorization rules (public GET catalog, /admin → ADMIN|PARTNER, /staff → VENUE_STAFF|ADMIN)
        ─► Controller (validation, Idempotency-Key handling, no business logic)
        ─► Service (@Transactional boundaries, row locks, state machines)
        ─► Repository (Spring Data JPA) ─► PostgreSQL
```

## State machines

Payment and booking state are deliberately separate.

**Seat** `AVAILABLE → LOCKED (lock_id) → BOOKED`; `LOCKED → AVAILABLE` on expiry or release; `BOOKED → AVAILABLE` on
cancellation; `BLOCKED` is for disabled seats.

**Seat lock** `ACTIVE → CONSUMED` (paid) | `EXPIRED` (cleanup job) | `RELEASED` (customer changed seats or cancelled).
Pay-at-venue converts an ACTIVE lock to a longer hold (`hold_type = PAY_AT_VENUE`).

**Booking** `PENDING → PAYMENT_PENDING → CONFIRMED → (REFUND_PENDING → REFUNDED | CANCELLED)`;
`PENDING/PAYMENT_PENDING → EXPIRED | CANCELLED`. If a payment fails, PAYMENT_PENDING goes back to PENDING so the
customer can retry while the hold lasts.

**Payment** `INITIATED → PENDING → SUCCESS | FAILED | CANCELLED`; `SUCCESS → PARTIALLY_REFUNDED | REFUNDED`.
Starting a new attempt marks older unfinished attempts CANCELLED. If money still arrives for one of those, it is
honoured, and refunded if the booking was already confirmed by another payment.

**Refund** `REQUESTED → PROCESSING → COMPLETED | FAILED` (retried up to 5 times; admins can retry or settle manually).

**Ticket** `VALID → USED` (gate scan, once) | `CANCELLED` | `EXPIRED`.

## Concurrency design

* Seat locking takes `PESSIMISTIC_WRITE` row locks on exactly the requested `show_seats`, ordered by primary key so
  that overlapping requests queue instead of deadlocking. Under READ COMMITTED, a waiting transaction re-reads the
  row after the winner commits, sees `LOCKED`, and fails with 409.
* Payment confirmation locks the payment row, then the booking row, then the seat-lock row, then the seats. The
  lock-expiry job uses the same order (booking before lock) to avoid deadlocks.
* Event inventory locks the `event_ticket_categories` rows and moves quantities `capacity → held → sold`. A
  `CHECK (sold + held <= capacity)` constraint backstops it.
* Show scheduling checks for overlaps in code, and a PostgreSQL `EXCLUDE USING gist` constraint makes a
  concurrent double insert impossible.
* Webhooks run `INSERT … ON CONFLICT DO NOTHING` on `(provider, event_id)` inside the processing transaction, so
  a duplicate delivery is a no-op and a failed delivery can be retried by the gateway.
* `Idempotency-Key` records are claimed in their own transaction. A replay returns the stored response, a
  concurrent replay gets 409, and the same key with a different body gets 422.

## Pricing

```
tickets     = Σ seat price snapshot (show base price + seat-type premium) | Σ event tier price × qty
food        = Σ menu price snapshot × qty
fee         = min(perTicket × tickets, cap)          (cineverse.pricing.*)
tax         = fee × taxRateOnFee                      (GST on the convenience fee; configurable)
discount    = coupon(tickets + food), capped
grandTotal  = tickets + food + fee + tax − discount
```

Prices are recomputed on every change and again at payment initiation. The amount charged is always
`booking.grandTotal` from the database.

## Caching

Caffeine caches cities, genres, event categories, food menus and movie detail (10 minutes, evicted on admin
edits). Seat maps, bookings and payments are never cached.

## Observability

Every API request produces one log line with the request id (also returned as `X-Request-Id`), method, path,
status, duration and user id. Business events are logged as key=value lines (`seat_lock`, `seat_unlock`,
`booking_created`, `payment_created`, `payment_success`, `payment_failed`, `refund_requested`,
`refund_completed`, `webhook_processed`). The `prod` profile emits ECS JSON logs. Security-relevant and
money-relevant actions are also written to `audit_logs`.

## Extending

* **Another gateway**: implement `PaymentProvider`, annotate it `@Component`, and add it to `PaymentRouter`'s
  preference table.
* **Queues**: `NotificationService.emailAfterCommit` and the scheduled jobs are the seams for RabbitMQ or Kafka.
  Publish the same events after commit instead of calling services directly.
* **Redis**: move `RateLimitFilter` counters and the Caffeine caches there. Seat locks should stay in
  PostgreSQL, because the lock rows are part of the booking's audit trail.
* **Seated events**: model the venue layout as a `cinema_screens`-style layout and create a `show` per session.
  The seat engine is agnostic to what is on stage.

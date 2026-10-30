# CineVerse

A full-stack movie, event and live-experience ticketing platform: city-aware discovery, real-time seat maps with
server-side seat locking, food & beverages, coupons, multi-gateway payments (Stripe, Razorpay, PhonePe, UPI apps,
pay-at-venue, and a mock gateway for development), QR tickets with gate validation, cancellations and refunds,
notifications, and an admin/partner console with a visual seat-layout designer and analytics.

The movie catalog is real: 2026 Telugu, Hindi, Tamil, Malayalam and Kannada releases plus 250 all-time classics,
shown at 47 real cinemas across 9 Indian cities. Posters, backdrops and official YouTube trailers are fetched from
TMDB at runtime. Cinema seat layouts, showtimes, events, venues and the demo accounts are illustrative.

---

## Architecture

```
Browser (Next.js 16 · React 19 · TypeScript · Tailwind v4 · TanStack Query · Framer Motion · R3F)
   │  REST/JSON  (Bearer access token in memory · refresh token in HttpOnly cookie)
   ▼
Spring Boot 3.5 (Java 21)
   Spring Security (JWT, RBAC, rate limiting, secure headers)
   Controller ─► Service ─► @Transactional ─► Repository (Spring Data JPA / Hibernate)
   Scheduled jobs: seat-lock cleanup · booking expiry · refunds · reminders
   PaymentProvider ─► Mock | Stripe | Razorpay | PhonePe  ◄── signed, idempotent webhooks
   ▼
PostgreSQL 16 (Flyway migrations · row locks · exclusion constraint for show overlaps)
```

The backend is the only source of truth for seat availability, prices, discounts, payment success, booking and
refund status, and authorization. The frontend never sends a price; it sends seat labels, food item ids and a
coupon code, and the server recomputes everything.

Payment flow: `Next.js → Spring Boot (create order) → gateway → webhook → Spring Boot (verify signature, dedupe event id) → DB → CONFIRMED booking + QR ticket`.

More detail in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Repository layout

```
cineverse/
├── backend/                 Spring Boot API (Maven)
│   └── src/main/java/com/cineverse/
│       ├── config/          security, CORS, cache, OpenAPI, scheduling, demo-data seeder
│       ├── security/        JWT service/filter, rate limiting, request-id logging
│       ├── controller/      public REST controllers (thin)
│       ├── service/         catalog, auth, pricing, coupons, search, reviews, idempotency, audit
│       ├── seat/            SeatLockService (SELECT … FOR UPDATE) + cleanup job
│       ├── booking/         booking lifecycle, event inventory, cancellation, tickets/QR, expiry job
│       ├── payment/         PaymentProvider interface, router, PaymentService, RefundService, providers/
│       ├── notification/    in-app notifications, EmailService (console / SMTP)
│       ├── admin/           admin & partner APIs, seat configurator, analytics
│       ├── entity/ repository/ dto/ mapper/ exception/ common/
│   └── src/main/resources/db/migration   V1…V9 schema + reference data
│   └── src/main/resources/db/demo        V100 demo catalog (dev profile only)
├── frontend/                Next.js App Router app
│   ├── app/                 routes (customer, booking funnel, admin, staff)
│   ├── components/          ui/, layout/, movies/, booking/, events/, admin/
│   ├── features/            auth, location, query providers, booking helpers
│   ├── services/            the single API layer (authApi, movieApi, showApi, seatApi, bookingApi, paymentApi, …)
│   ├── lib/ types/ schemas/ hooks/
│   ├── tests/ (Vitest)  e2e/ (Playwright)
│   └── scripts/generate-media.mjs   draws the original demo posters/backdrops/food art
├── database/init/           creates the test database for docker compose
├── docs/                    architecture & API notes
├── postman/                 CineVerse.postman_collection.json
├── docker-compose.yml
└── .env.example
```

## Quick start (local)

Prerequisites: Java 21, Maven 3.9+, Node 22+, Docker (or a local PostgreSQL 16).

```bash
cp .env.example .env                       # adjust if needed; defaults work for local dev
docker compose up -d postgres              # PostgreSQL on :5432 (also creates cineverse_test)

# backend (dev profile: demo data; configure gateway credentials or opt in to mock payments)
cd backend
mvn spring-boot:run                        # http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui

# frontend
cd ../frontend
npm install
npm run dev                                # http://localhost:3000
```

Or run everything in containers: `docker compose --profile app up --build`.

On first start Flyway creates the schema, the dev profile loads the demo catalog (10 movies, 13 cinemas in 9 cities,
29 screens, 12 events/sports/plays/activities), creates the accounts below, schedules a rolling 7-day window of
shows, and places a few real sample bookings through the booking + mock-payment pipeline so seat maps and analytics
aren't empty.

| Role | Email | Password |
|------|-------|----------|
| Customer | demo@cineverse.com | `DEMO_USER_PASSWORD` (default `Demo@12345`) |
| Admin | admin@cineverse.com | same |
| Venue staff | staff@cineverse.com | same |
| Partner (owns the Purple Square cinemas) | partner@cineverse.com | same |

These accounts exist only with the `dev` profile. Never enable it in production.

## Environment variables

See [`.env.example`](.env.example) for the full list. The important ones:

| Variable | Purpose |
|---|---|
| `NEXT_PUBLIC_API_URL` | Backend base URL used by the browser |
| `NEXT_PUBLIC_APP_NAME` | Display name |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` (≥ 32 bytes), `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | Token signing and lifetimes (ISO-8601 durations) |
| `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET` | Stripe (cards) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Razorpay (UPI incl. Google Pay/PhonePe apps, cards, net banking, wallets) |
| `PHONEPE_CLIENT_ID`, `PHONEPE_CLIENT_SECRET`, `PHONEPE_CLIENT_VERSION`, `PHONEPE_ENVIRONMENT` | PhonePe PG |
| `EMAIL_PROVIDER` (`console`/`smtp`), `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD`, `EMAIL_FROM` | Email delivery |
| `EMAIL_LOGIN_ALERTS` | Send a "new sign-in" email on every login (default `true`) |
| `BRAND_*` | Company name, motto, support contacts, address and social links used in email headers/footers |
| `TMDB_API_KEY` | Posters, backdrops, trailers, runtimes and cast photos from The Movie Database |
| `YOUTUBE_API_KEY` | Optional trailer search fallback (YouTube Data API v3) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed browser origins |
| `SEAT_LOCK_DURATION`, `CANCELLATION_CUTOFF_MINUTES`, `CONVENIENCE_FEE_PER_TICKET`, `TAX_RATE_ON_FEE` | Business policy |

Secrets are read only by the backend. Nothing secret is ever sent to the browser.

## Database

* Migrations: `backend/src/main/resources/db/migration` (`V1__initial_schema.sql` … `V9__reference_data.sql`).
  Demo data lives separately in `db/demo` and is only on Flyway's path in the `dev` profile.
* Integrity rules are enforced in the database itself as well as in code. Examples: the `ex_shows_no_overlap`
  exclusion constraint (no overlapping shows on one screen), `CHECK (sold + held <= capacity)` on event inventory,
  a unique `(provider, event_id)` on webhook events, and one refund per captured payment.
* Never change a production schema by hand. Add a new `V{n}__*.sql` migration.

## Payments

`PaymentProvider` is the gateway abstraction. `PaymentRouter` maps each customer-facing method to the first
configured gateway that supports it:

| Method | Preferred gateway order |
|---|---|
| Card (Visa/Mastercard/RuPay/Amex where supported) | Stripe → Razorpay → PhonePe → Mock |
| UPI | Razorpay → PhonePe → Mock |
| Google Pay | Razorpay (UPI) → Mock. There is no standalone "Google Pay API"; GPay pays through UPI. |
| PhonePe | PhonePe PG → Razorpay (UPI) → Mock |
| Net banking / Wallets | Razorpay → (PhonePe) → Mock |
| Pay at venue | internal: seats held until a configurable cut-off, staff mark it PAID or CANCELLED |

* **Stripe**: the backend creates a PaymentIntent. The browser gets only the `client_secret` and publishable key
  and confirms with Stripe.js Payment Element. The final state comes from `/api/v1/payments/webhook/stripe`,
  which verifies the `Stripe-Signature` header (HMAC-SHA256 of `timestamp.payload`, 5-minute tolerance).
* **Razorpay**: the backend creates an Order and the browser opens Checkout. The handler response is verified
  server-side (`HMAC_SHA256(order_id|payment_id, key_secret)`), and `/webhook/razorpay` verifies
  `X-Razorpay-Signature`.
* **PhonePe**: Standard Checkout v2 with OAuth client credentials. Base URLs switch on `PHONEPE_ENVIRONMENT`.
  **Before going live, re-check the endpoints against PhonePe's current merchant documentation.**
* **Mock (development)**: opt in with `PAYMENTS_MOCK_ENABLED=true`. Checkout sends you to
  `/dev/payment-simulator`, where you choose SUCCESS / FAILED / CANCELLED. The result is delivered as a signed
  webhook through the same idempotent pipeline, and you can deliver it twice to see duplicate protection.

Card numbers and CVVs never touch CineVerse. Only gateway references (order, payment and refund ids) are stored.

### Connect Stripe or Razorpay

1. Copy `.env.example` to `.env` in the repository root, then add your provider's test credentials. Do not commit
   `.env`; it is git-ignored. The backend imports this file when started from `backend/`, and Docker Compose passes
   it into the backend container.
2. For Stripe, set `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and `STRIPE_WEBHOOK_SECRET`. Register
   `https://<api-host>/api/v1/payments/webhook/stripe` and subscribe to PaymentIntent success, failure, canceled,
   and refund update events. For local testing, forward Stripe CLI events to
   `http://localhost:8080/api/v1/payments/webhook/stripe` and use the signing secret Stripe CLI provides.
3. For Razorpay, set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET`. Register
   `https://<api-host>/api/v1/payments/webhook/razorpay` and enable payment captured, payment failed, and refund
   events. Use the generated webhook **secret**, not the webhook dashboard URL.
4. Restart the backend. Configured methods become selectable at checkout: Stripe handles cards, and Razorpay
   handles UPI (including Google Pay and PhonePe UPI), cards, net banking, and supported wallets. Only public
   gateway identifiers are sent to the browser.

The `dev` profile does not enable the mock gateway by default; opt in with `PAYMENTS_MOCK_ENABLED=true` only
when you want to use the simulator. Stripe is selectable only when both its secret and publishable keys are set;
Razorpay requires its key ID and key secret. Webhook secrets are required for signed webhook deliveries.

### Real-time confirmation on your own PC

Webhooks need a public URL, but you don't need them to see payments complete instantly while developing:

* **Razorpay**: Checkout's success handler posts the signed response to `/api/v1/payments/razorpay/verify`; the
  backend checks the signature and confirms the booking immediately.
* **Stripe**: after `confirmPayment` the browser lands on `/payment/{bookingId}`, which polls
  `/api/v1/payments/{id}` while the backend asks Stripe for the PaymentIntent status. `succeeded` confirms the booking.

Webhooks are still the source of truth in production (they also catch payments whose browser tab was closed).
Test credentials: Stripe card `4242 4242 4242 4242`, any future expiry, any CVC; Razorpay test mode offers
success/failure buttons for UPI, cards and net banking. Live payments require activated (KYC-verified) accounts
with each gateway; switch the keys in `.env` and nothing else changes.

## Real catalog: movies and cinemas

`V101__real_catalog.sql` (all profiles, including `prod`) inserts the movies and cinemas once, keyed on slug, so
it never overwrites admin edits. It is generated from `catalog/`:

```
catalog/source/*.txt        the IMDb lists the catalog was built from (2026 regional releases + Top 250)
catalog/movies.json         parsed: title, year, languages, runtime, certificate, IMDb rating, synopsis, director, cast
catalog/cinemas.json        47 real cinemas: chain, area, address, approximate coordinates
catalog/scripts/            parse_movies.py and gen_catalog_sql.py (and email_art.py for the email images)
```

* Released 2026 titles are `NOW_SHOWING`, announced ones `COMING_SOON`, and the all-time classics `CLASSIC`
  (a "Classics" tab and home-page rail; the dev seeder screens them on weekend late shows).
* Certificates are mapped to CBFC categories (U, UA7+, UA13+, UA16+, A). Missing runtimes and dates are marked
  as estimates and replaced by TMDB data on the first sync.
* In the `dev` profile the fictional launch catalog is retired automatically, and each city's cinemas programme
  their regional language first, then Hindi and English.
* To add movies later, use the admin console, or add rows to `catalog/movies.json` and generate a **new**
  migration number (never edit an applied migration).

## Posters and YouTube trailers (TMDB)

Set `TMDB_API_KEY` (free: themoviedb.org → Settings → API; the v3 key or the v4 read token both work) and restart.
About 15 seconds after startup the backend matches every movie on TMDB by title + year + language, then stores:
poster and backdrop URLs, the official YouTube trailer (preferring the film's own language), and any missing
runtime, release date, synopsis, genres, director and cast headshots. It re-checks weekly and every night.

* Admin → Movies shows coverage and has **Fetch missing** (all) and a refresh button per movie.
* Paste any YouTube link (watch, youtu.be or shorts) into a movie's trailer field; it is converted to an embed URL
  and the movie's media is **locked** so automatic syncs never replace your choice.
* `YOUTUBE_API_KEY` (optional) searches YouTube for an official trailer when TMDB has none.
* If posters don't load on Jio/Airtel networks, change your DNS to 1.1.1.1 or 8.8.8.8 (some ISPs block TMDB).
* TMDB's terms require the attribution shown in the footer. Posters and trailers belong to their owners: fine for
  a personal/demo project; a commercial launch needs the appropriate licences.

## Emails

| Email | When | Contents |
|---|---|---|
| Welcome | Account registered | Thanks-for-registering message, account details, what you can do |
| New sign-in | Every successful login (`EMAIL_LOGIN_ALERTS`) | Time, device, IP, "wasn't you?" reset link |
| Booking confirmed | Payment captured (Stripe/Razorpay/PhonePe/counter) | Order ID, payment ID, gateway transaction ID, method, time, show, seats, price breakdown, entry QR code |
| Cancelled / refund / password reset | As before | Same branded layout |

All share one layout (`backend/src/main/resources/email/templates/layout.html`): logo and banner in the header,
motto, services and social links in the footer (`BRAND_*` settings). Images are embedded inline, so they show
without public hosting. Emails are sent only after the database transaction commits.

* `EMAIL_PROVIDER=console` (default): nothing is sent; each email is saved as `backend/email-outbox/<time>-<template>/email.html`
  so you can open exactly what customers will receive.
* `EMAIL_PROVIDER=smtp`: real delivery. For Gmail use `smtp.gmail.com:587`, your address as `EMAIL_USERNAME` and
  `EMAIL_FROM`, and a 16-character **app password** (Google Account → Security → 2-Step Verification → App passwords).

## Core guarantees and where they live

| Guarantee | Implementation |
|---|---|
| Two customers can't hold or buy the same seat | `SeatLockService.lockSeats`: `SELECT … FOR UPDATE` on the requested `show_seats` rows in id order, inside one transaction; 409 `SEAT_LOCKED` / `SEAT_ALREADY_BOOKED` |
| Holds expire | `SeatLockCleanupJob` (every 5 s) marks locks `EXPIRED` (rows kept for audit) and frees seats. Lapsed holds are also treated as free immediately at read/lock time. |
| Server-side pricing | `PricingService`: tickets and food from DB snapshots, configurable fee and GST, coupon discount capped at the order value |
| Price snapshot | `bookings` keeps ticket/food/fee/tax/discount/total; `booking_seats`, `food_order_items` and `booking_items` keep unit prices |
| No duplicate bookings/payments | `Idempotency-Key` on booking creation, payment initiation and cancellation (`IdempotencyService`); webhook event-id dedupe; payment row locks |
| Late payment after the seats were resold | the booking isn't confirmed and a full refund is created automatically |
| Uncertain payment status | `/payment/[id]` shows "Payment status is being verified" and the backend queries the gateway; nothing is marked failed on a missing callback |
| Refunds | `RefundService`: REQUESTED → PROCESSING → COMPLETED/FAILED (with retries). COMPLETED only when the gateway confirms. |
| QR tickets | `CV1.<ticketCode>.<HMAC>`: no payment data; `/tickets/validate` admits once (row-locked) |
| Authorization | URL rules plus `AdminAccess` (partners are scoped to their own cinemas); frontend guards are UX only |

## API

* Swagger UI: `http://localhost:8080/swagger-ui`, OpenAPI JSON: `/v3/api-docs`
* Postman: import `postman/CineVerse.postman_collection.json`, then run the folders top to bottom.
* Response envelope: `{ "success": true, "data": …, "message": "Success" }` or
  `{ "success": false, "error": { "code": "SEAT_ALREADY_BOOKED", "message": "…", "fields": { … } } }`.
* Paging: `?page=0&size=20` returns `{ content, page, size, totalElements, totalPages }`.

## Testing

```bash
# backend: needs PostgreSQL with a cineverse_test database (docker compose creates it)
cd backend && mvn test

# frontend unit tests
cd frontend && npm test

# end-to-end (backend + frontend running in dev mode)
cd frontend && npx playwright test
```

Backend integration tests (real PostgreSQL, clean-migrated per run):

| Test | Covers |
|---|---|
| `SeatConcurrencyTest` | §97: two users hit seat A1 at the same instant, and 12 users race for 3 seats. Exactly one wins; the others get 409. |
| `PaymentIdempotencyTest` | §98: a duplicate webhook yields one booking and one ticket; forged signatures are rejected; a failed payment can be retried; a late payment after resale is refunded |
| `SeatLockExpiryTest` | §99: after 5 minutes another customer can lock the seat, with or without the cleanup job having run |
| `CancellationTest` | §100: cancellation before the deadline releases seats and completes the refund; after the deadline it's rejected |
| `BookingFlowE2ETest` | §96 over HTTP: login → showtimes → seat map → lock → booking + food → coupon → mock payment (webhook ×2) → CONFIRMED + QR → My Bookings → notifications → gate scan once |
| `AdminFlowTest` | §101: admin creates movie → cinema → screen → seat layout → show, and a customer finds and locks it; overlap rejected; RBAC 403s |
| `AuthTest` | register/login/refresh rotation, reuse detection, logout, validation errors |
| `PricingServiceTest` | pure unit tests of the price engine |

## Deployment notes

* Run the backend with `SPRING_PROFILES_ACTIVE=prod`. This turns off demo data and the mock gateway and switches
  to structured JSON logs. Provide real `JWT_SECRET`, `QR_SIGNING_SECRET` and gateway keys, set
  `COOKIE_SECURE=true`, and serve both apps over HTTPS on the same site (for example `app.example.com` and
  `api.example.com`) so the refresh cookie works.
* Scale-out: seat locks are in PostgreSQL, so any number of API instances stay consistent. The in-memory rate
  limiter and caches are per instance; move them to Redis when you run more than one node (a `redis` service is
  already in `docker-compose.yml`).
* Scheduled jobs are idempotent and lock rows individually, so running them on several nodes is safe. You can
  still pin them to one node with `cineverse.scheduling.enabled`.

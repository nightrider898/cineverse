# Upgrade notes: real catalog, live payments, branded emails

## What changed

* **Real catalog**: `V101__real_catalog.sql` adds 430 movies (2026 Telugu/Hindi/Tamil/Malayalam/Kannada releases
  and 250 all-time classics) and 47 real cinemas in the 9 cities. In the dev profile the fictional demo movies and
  cinemas are retired and new showtimes are generated (the first start takes longer: about 3,700 shows are created).
* **Posters and YouTube trailers**: fetched from TMDB automatically when `TMDB_API_KEY` is set
  (`backend/.../media/MediaSyncService.java`). Admin → Movies has coverage stats, **Fetch missing**, a per-movie
  refresh button and a "lock" option. Trailers play in a privacy-enhanced YouTube player.
* **Emails**: branded HTML emails for registration (welcome), every sign-in, and booking confirmation (order ID,
  payment ID, transaction ID, seats, price breakdown, QR). Cancellation, refund and password-reset emails use
  the same layout. Templates are in `backend/src/main/resources/email/`.
* **Payments**: Stripe (cards) and Razorpay (UPI, Google Pay, PhonePe app, net banking, wallets, and cards when
  Stripe isn't configured) with real-time confirmation. See README → Payments.

## Steps on your PC

1. Replace the project folder with this version (keep your own `.env`).
2. Add the new settings you want to your root `.env` (see `.env.example`): `TMDB_API_KEY`, the Stripe/Razorpay
   test keys, `PAYMENTS_MOCK_ENABLED=false`, `EMAIL_PROVIDER=smtp` plus the SMTP settings, and the `BRAND_*` values.
3. Start Postgres (`docker compose up -d postgres`), then `mvn test` and
   `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"` in `backend`. Flyway applies V101 automatically;
   your existing data stays.
4. In `frontend`: `npm install`, then `npm run dev`.

If you'd rather start from a clean database: `docker compose down -v` (this deletes all local data), then step 3.

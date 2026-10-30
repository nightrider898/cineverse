package com.cineverse.config;

import com.cineverse.booking.BookingService;
import com.cineverse.dto.BookingDtos.CreateBookingRequest;
import com.cineverse.dto.BookingDtos.FoodLine;
import com.cineverse.dto.PaymentDtos.InitiatePaymentRequest;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.*;
import com.cineverse.payment.PaymentProvider;
import com.cineverse.payment.PaymentService;
import com.cineverse.repository.*;
import com.cineverse.seat.SeatLockService;
import com.cineverse.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Development/demo data that depends on "now": accounts with BCrypt passwords from configuration,
 * a rolling window of shows, and a handful of real bookings made through the actual booking and
 * mock-payment pipeline (so seat maps and analytics are not empty). Never enabled in production.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime[] SLOTS = {LocalTime.of(9, 45), LocalTime.of(13, 15), LocalTime.of(16, 45), LocalTime.of(20, 15)};

    private final AppProperties props;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PartnerRepository partnerRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaScreenRepository screenRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewService reviewService;
    private final SeatLockService seatLockService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager txm;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!props.seed().demoData()) return;
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.executeWithoutResult(s -> seedUsers());
        tx.executeWithoutResult(s -> seedReviews());
        tx.executeWithoutResult(s -> retireDemoCatalog());
        int shows = ensureShows();
        tx.executeWithoutResult(s -> rollEvents());
        seedSampleBookings();
        log.info("Demo data ready ({} new shows). Log in as demo@cineverse.com / admin@cineverse.com / staff@cineverse.com / partner@cineverse.com", shows);
    }

    /** Keeps the rolling show window filled as days pass. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        if (!props.seed().demoData()) return;
        ensureShows();
        new TransactionTemplate(txm).executeWithoutResult(s -> rollEvents());
    }

    private void seedUsers() {
        String password = props.seed().demoPassword();
        if (password == null || password.isBlank()) {
            log.warn("DEMO_USER_PASSWORD not set; demo accounts not created");
            return;
        }
        User demo = user("demo@cineverse.com", "Demo Customer", "+91 90000 00001", password, "USER");
        User admin = user("admin@cineverse.com", "Asha Admin", "+91 90000 00002", password, "ADMIN", "USER");
        user("staff@cineverse.com", "Sanjay Staff", "+91 90000 00003", password, "VENUE_STAFF");
        User partnerUser = user("partner@cineverse.com", "Priya Partner", "+91 90000 00004", password, "PARTNER");
        jdbc.update("insert into admin_users (user_id, title) values (?, 'Platform Admin') on conflict do nothing", admin.getId());
        if (demo.getPreferredCity() == null) {
            jdbc.update("update users set preferred_city_id = (select id from cities where slug = 'hyderabad') where id = ?", demo.getId());
        }
        if (partnerRepository.findByUserId(partnerUser.getId()).isEmpty()) {
            Partner p = new Partner();
            p.setName("Purple Square Entertainment");
            p.setContactEmail("partner@cineverse.com");
            p.setUser(partnerUser);
            partnerRepository.save(p);
            jdbc.update("insert into cinema_partners (cinema_id, partner_id) select id, ? from cinemas where slug like 'purple-square-%' on conflict do nothing", p.getId());
        }
    }

    private User user(String email, String name, String phone, String password, String... roles) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name);
            u.setPhone(phone);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setEmailVerified(true);
            u.getRoles().addAll(roleRepository.findByNameIn(List.of(roles)));
            return userRepository.save(u);
        });
    }

    private static final String[] REVIEWERS = {"Kiran Reddy", "Meenakshi Iyer", "Rahul Verma", "Fatima Shaikh", "Arnav Das",
            "Lavanya Rao", "Vikash Kumar", "Sneha Pillai", "Harpreet Singh", "Deepa Nair", "Omar Khan", "Ritika Joshi"};
    private static final String[][] REVIEW_TEXT = {
            {"Worth every rupee", "Gorgeous visuals and a story that actually lands. Watched it in the big format and it was a proper event."},
            {"Solid entertainer", "A bit long in the second half, but the performances carry it. Great with friends."},
            {"Loved the music", "The background score is the real hero. Some scenes gave me goosebumps."},
            {"Good, not great", "Interesting idea, uneven execution. Still enjoyable on a weekend."},
            {"Instant favourite", "Funny, warm and surprisingly emotional. Will watch again."},
            {"Skip the 3D", "Story is fine but the 3D adds nothing. Go for 2D."}};

    private void seedReviews() {
        if (reviewRepository.count() > 0) return;
        List<User> reviewers = new ArrayList<>();
        for (int i = 0; i < REVIEWERS.length; i++) {
            String email = "reviewer" + (i + 1) + "@example.cineverse.com";
            User u = userRepository.findByEmail(email).orElseGet(() -> {
                User x = new User();
                x.setEmail(email);
                x.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); // not a usable login
                x.getRoles().addAll(roleRepository.findByNameIn(List.of("USER")));
                return x;
            });
            u.setFullName(REVIEWERS[i]);
            reviewers.add(userRepository.save(u));
        }
        Random rnd = new Random(42);
        // Sample reviews are only written for the fictional demo titles: real films never get invented reviews.
        for (Movie m : movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.NOW_SHOWING)) {
            if (!"DEMO".equals(m.getCatalogSource())) continue;
            int base = 3 + (int) (m.getId() % 3); // deterministic "quality" per title
            for (User u : reviewers) {
                if (rnd.nextInt(10) < 3) continue;
                int rating = Math.max(1, Math.min(5, base + rnd.nextInt(3) - 1));
                String[] text = REVIEW_TEXT[rnd.nextInt(REVIEW_TEXT.length)];
                Review r = new Review();
                r.setUser(u);
                r.setMovieId(m.getId());
                r.setRating((short) rating);
                r.setTitle(text[0]);
                r.setBody(text[1]);
                r.setStatus(ReviewStatus.APPROVED);
                reviewRepository.save(r);
            }
            reviewRepository.flush();
            reviewService.recompute(m.getId());
        }
    }

    /**
     * Retires the fictional launch catalog once the real catalog (V101) is present: demo movies and cinemas are
     * deactivated and their future shows without bookings are cancelled. Existing bookings are untouched.
     */
    private void retireDemoCatalog() {
        Integer imported = jdbc.queryForObject("select count(*) from movies where catalog_source = 'IMPORT'", Integer.class);
        if (imported == null || imported == 0) return;
        int movies = jdbc.update("update movies set active = false, featured = false, status = 'ARCHIVED' where catalog_source = 'DEMO' and active");
        int cinemas = jdbc.update("update cinemas set active = false where catalog_source = 'DEMO' and active");
        jdbc.update("update cinema_screens set active = false where active and cinema_id in (select id from cinemas where catalog_source = 'DEMO')");
        int shows = jdbc.update("""
                update shows set status = 'CANCELLED'
                where status = 'SCHEDULED' and start_time > now()
                  and (cinema_id in (select id from cinemas where catalog_source = 'DEMO')
                       or movie_id in (select id from movies where catalog_source = 'DEMO'))
                  and not exists (select 1 from bookings b where b.show_id = shows.id
                                  and b.status in ('PENDING', 'PAYMENT_PENDING', 'CONFIRMED', 'REFUND_PENDING'))
                """);
        jdbc.update("update offers set active = false where active and movie_id in (select id from movies where catalog_source = 'DEMO')");
        if (movies + cinemas + shows > 0) {
            log.info("Retired fictional demo catalog: {} movies, {} cinemas, {} future shows cancelled", movies, cinemas, shows);
        }
    }

    /** Languages each city's cinemas programme, most-screened first. */
    private static final Map<String, List<String>> CITY_LANGUAGES = Map.of(
            "hyderabad", List.of("Telugu", "Hindi", "English", "Tamil"),
            "vijayawada", List.of("Telugu", "Hindi", "English", "Tamil"),
            "visakhapatnam", List.of("Telugu", "Hindi", "English", "Tamil"),
            "chennai", List.of("Tamil", "English", "Telugu", "Hindi", "Malayalam"),
            "bengaluru", List.of("Kannada", "Hindi", "Telugu", "Tamil", "English", "Malayalam"),
            "mumbai", List.of("Hindi", "English", "Telugu", "Tamil"),
            "delhi", List.of("Hindi", "English", "Telugu", "Tamil"),
            "pune", List.of("Hindi", "English", "Telugu", "Tamil"),
            "kolkata", List.of("Hindi", "English", "Telugu", "Tamil"));
    private static final int[] TITLES_PER_LANGUAGE = {12, 7, 4, 3, 2, 2};

    /** Per-city programme (movie ids): admin-added titles first, then the most popular titles per city language. */
    private record Programme(Map<String, List<Long>> byCity, List<Long> classics) {}

    private Programme programme() {
        List<Movie> current = movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.NOW_SHOWING);
        Comparator<Movie> popularity = Comparator.comparing((Movie m) -> !"ADMIN".equals(m.getCatalogSource()))
                .thenComparing(m -> m.getImdbVotes() == null ? 0 : m.getImdbVotes(), Comparator.reverseOrder())
                .thenComparing(Movie::getId);
        Map<String, List<Long>> byCity = new HashMap<>();
        CITY_LANGUAGES.forEach((city, langs) -> {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            current.stream().filter(m -> "ADMIN".equals(m.getCatalogSource())).sorted(popularity).forEach(m -> ids.add(m.getId()));
            for (int i = 0; i < langs.size(); i++) {
                String lang = langs.get(i);
                int limit = TITLES_PER_LANGUAGE[Math.min(i, TITLES_PER_LANGUAGE.length - 1)];
                current.stream().filter(m -> m.getLanguages().contains(lang)).sorted(popularity).limit(limit).forEach(m -> ids.add(m.getId()));
            }
            if (ids.isEmpty()) current.stream().sorted(popularity).limit(20).forEach(m -> ids.add(m.getId()));
            byCity.put(city, new ArrayList<>(ids));
        });
        List<Long> classics = movieRepository.findByActiveTrueAndStatusOrderByReleaseDateAsc(MovieStatus.CLASSIC).stream()
                .sorted(Comparator.comparing((Movie m) -> m.getImdbVotes() == null ? 0 : m.getImdbVotes(), Comparator.reverseOrder()))
                .limit(40).map(Movie::getId).toList();
        return new Programme(byCity, classics);
    }

    /** Creates four shows per active screen per day for the configured window, skipping days already scheduled. */
    public int ensureShows() {
        TransactionTemplate tx = new TransactionTemplate(txm);
        Programme programme = tx.execute(s -> programme());
        if (programme == null || programme.byCity().values().stream().allMatch(List::isEmpty)) return 0;
        LocalDate today = LocalDate.now(clock.withZone(IST));
        Instant now = Instant.now(clock);
        int created = 0;
        List<CinemaScreen> screens = screenRepository.findByActiveTrue();
        for (int si = 0; si < screens.size(); si++) {
            CinemaScreen screen = screens.get(si);
            for (int d = 0; d <= props.seed().showDaysAhead(); d++) {
                LocalDate date = today.plusDays(d);
                if (showRepository.existsByScreenIdAndShowDate(screen.getId(), date)) continue;
                final int screenIndex = si;
                final int day = d;
                Integer n = tx.execute(s -> createDay(screen, date, programme, screenIndex, day, now));
                created += n == null ? 0 : n;
            }
        }
        return created;
    }

    private int createDay(CinemaScreen screenRef, LocalDate date, Programme programme, int screenIndex, int day, Instant now) {
        CinemaScreen screen = screenRepository.findById(screenRef.getId()).orElseThrow();
        Cinema cinema = screen.getCinema();
        if (!cinema.isActive()) return 0;
        String city = cinema.getCity().getSlug();
        List<Long> pool = programme.byCity().getOrDefault(city, List.of());
        if (pool.isEmpty()) pool = programme.byCity().values().stream().filter(l -> !l.isEmpty()).findFirst().orElse(List.of());
        if (pool.isEmpty()) return 0;
        boolean weekend = date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        int created = 0;
        for (int slot = 0; slot < SLOTS.length; slot++) {
            // Weekend late shows on every other screen are classic re-releases.
            boolean classicSlot = weekend && slot == SLOTS.length - 1 && screenIndex % 2 == 0 && !programme.classics().isEmpty();
            Long movieId = classicSlot
                    ? programme.classics().get((screenIndex / 2 + day) % programme.classics().size())
                    : pool.get((screenIndex * 3 + day + slot * 2) % pool.size());
            // re-load inside this transaction: lazy collections must be read while the session is open
            Movie movie = movieRepository.findById(movieId).orElseThrow();
            Instant start = date.atTime(SLOTS[slot]).atZone(IST).toInstant();
            if (!start.isAfter(now.plus(Duration.ofMinutes(20)))) continue;
            Instant end = start.plus(Duration.ofMinutes(movie.getDurationMin() + 20L));
            if (showRepository.existsOverlap(screen.getId(), start, end, null)) continue;
            String format = bestFormat(screen, movie);
            Show show = new Show();
            show.setMovie(movie);
            show.setCinema(cinema);
            show.setScreen(screen);
            show.setShowDate(date);
            show.setStartTime(start);
            show.setEndTime(end);
            show.setFormat(format);
            show.setLanguage(language(movie, city, slot));
            show.setBasePrice(classicSlot ? BigDecimal.valueOf(150) : price(format, SLOTS[slot], date));
            showRepository.saveAndFlush(show);
            showSeatRepository.generateForShow(show.getId(), screen.getId(), show.getBasePrice());
            created++;
        }
        return created;
    }

    private static String bestFormat(CinemaScreen screen, Movie movie) {
        List<String> sf = screen.formatList();
        List<String> mf = movie.formatList();
        for (String f : List.of("IMAX", "4DX", "DOLBY_ATMOS", "3D", "2D")) {
            if (sf.contains(f) && mf.contains(f) && !(f.equals("3D") && screen.getScreenType().equals("STANDARD") && movie.getId() % 2 == 0)) return f;
        }
        return "2D";
    }

    /** The show's language: the first of the movie's languages that this city programmes, else its original language. */
    private static String language(Movie movie, String city, int slot) {
        List<String> langs = new ArrayList<>(movie.getLanguages());
        if (langs.isEmpty()) return "English";
        for (String l : CITY_LANGUAGES.getOrDefault(city, List.of())) {
            if (langs.contains(l)) return l;
        }
        return langs.get(slot % langs.size());
    }

    private static BigDecimal price(String format, LocalTime time, LocalDate date) {
        int base = switch (format) {
            case "IMAX" -> 280;
            case "4DX" -> 320;
            case "3D", "DOLBY_ATMOS" -> 220;
            default -> 180;
        };
        if (time.getHour() >= 17) base += 40;
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) base += 30;
        return BigDecimal.valueOf(base);
    }

    /** Events whose sessions have all passed get a fresh session a few weeks out, with the same ticket tiers. */
    private void rollEvents() {
        Instant now = Instant.now(clock);
        for (Event e : eventRepository.findWithoutFutureSessions(now)) {
            EventSession last = e.getSessions().stream().max(Comparator.comparing(EventSession::getStartTime)).orElse(null);
            if (last == null) continue;
            long weeks = Math.max(1, Duration.between(last.getStartTime(), now).toDays() / 7 + 2);
            EventSession s = new EventSession();
            s.setEvent(e);
            s.setStartTime(last.getStartTime().plus(Duration.ofDays(7 * weeks)));
            s.setEndTime(last.getEndTime().plus(Duration.ofDays(7 * weeks)));
            for (EventTicketCategory c : last.getTicketCategories()) {
                EventTicketCategory n = new EventTicketCategory();
                n.setSession(s);
                n.setName(c.getName());
                n.setDescription(c.getDescription());
                n.setPrice(c.getPrice());
                n.setCapacity(c.getCapacity());
                n.setMaxPerBooking(c.getMaxPerBooking());
                n.setSortOrder(c.getSortOrder());
                s.getTicketCategories().add(n);
            }
            e.getSessions().add(s);
        }
    }

    /**
     * Makes real bookings for reviewer accounts through the same services customers use (seat lock ->
     * booking -> mock payment -> confirmation), so the seat maps show sold seats and analytics has data.
     */
    private void seedSampleBookings() {
        if (!props.payments().mockEnabled() || bookingRepository.count() > 0) return;
        List<User> buyers = userRepository.findAll().stream().filter(u -> u.getEmail().endsWith("@example.cineverse.com")).toList();
        if (buyers.isEmpty()) return;
        Instant now = Instant.now(clock);
        List<Show> upcoming = showRepository.findAll().stream()
                .filter(s -> s.getStartTime().isAfter(now.plus(Duration.ofHours(1))) && s.getStartTime().isBefore(now.plus(Duration.ofDays(3))))
                .toList();
        Random rnd = new Random(7);
        int made = 0;
        for (Show show : upcoming) {
            if (rnd.nextInt(10) < 4) continue;
            List<String> rows = List.of("D", "E", "F", "G", "H");
            int groups = 1 + rnd.nextInt(4);
            for (int g = 0; g < groups; g++) {
                User buyer = buyers.get(rnd.nextInt(buyers.size()));
                String row = rows.get(rnd.nextInt(rows.size()));
                int startSeat = 1 + rnd.nextInt(10);
                int count = 2 + rnd.nextInt(3);
                List<String> seats = new ArrayList<>();
                for (int k = 0; k < count && startSeat + k <= 14; k++) seats.add(row + (startSeat + k));
                try {
                    var lock = seatLockService.lockSeats(show.getId(), buyer.getId(), seats);
                    var food = rnd.nextBoolean() ? List.of(new FoodLine(1L, 1), new FoodLine(6L, seats.size())) : List.<FoodLine>of();
                    var booking = bookingService.createFromLock(buyer.getId(), new CreateBookingRequest(lock.lockId(), food));
                    PaymentMethod method = List.of(PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.GPAY, PaymentMethod.NETBANKING).get(rnd.nextInt(4));
                    var payment = paymentService.initiate(buyer.getId(), new InitiatePaymentRequest(booking.id(), method));
                    Long paymentId = paymentRepository.findByPublicId(payment.paymentId()).orElseThrow().getId();
                    paymentService.applyOutcome(paymentId, PaymentProvider.Outcome.SUCCESS, "seed_" + UUID.randomUUID().toString().substring(0, 8), null, "DEMO_SEED");
                    made++;
                } catch (RuntimeException e) {
                    log.debug("sample booking skipped: {}", e.getMessage());
                }
            }
        }
        log.info("Seeded {} sample bookings through the booking pipeline", made);
    }
}
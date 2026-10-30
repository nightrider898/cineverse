// Mirrors the Spring Boot DTOs (com.cineverse.dto.*). Money arrives as JSON numbers in INR.

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message?: string;
  error?: { code: string; message: string; fields?: Record<string, string> };
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface City {
  id: number;
  name: string;
  state: string;
  slug: string;
  popular: boolean;
  latitude?: number;
  longitude?: number;
}

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  avatarUrl?: string;
  preferredCity?: City;
  roles: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Movie {
  id: number;
  title: string;
  slug: string;
  tagline?: string;
  posterUrl?: string;
  backdropUrl?: string;
  certificate: string;
  durationMin: number;
  releaseDate: string;
  status: "NOW_SHOWING" | "COMING_SOON" | "CLASSIC" | "ARCHIVED";
  languages: string[];
  genres: string[];
  formats: string[];
  rating: number;
  votes: number;
  featured: boolean;
  trailerUrl?: string;
  /** IMDb figures supplied with the catalog (shown until our own reviews exist). */
  imdbRating?: number;
  imdbVotes?: number;
  collection?: string;
  /** True when only the year (or an expected window) is known. */
  releaseDateEstimated?: boolean;
}

export interface Person { id: number; name: string; photoUrl?: string }
export interface CastMember { actorId: number; name: string; photoUrl?: string; character?: string; role: string }
export interface RatingSummary { average: number; totalVotes: number; distribution: Record<string, number> }

export interface MovieDetail {
  movie: Movie;
  synopsis: string;
  directors: Person[];
  cast: CastMember[];
  crew: CastMember[];
  photos: string[];
  ratingSummary: RatingSummary;
}

export interface MovieHome {
  featured: Movie[];
  nowShowing: Movie[];
  comingSoon: Movie[];
  trending: Movie[];
  topRated: Movie[];
  popular: Movie[];
  recommended: Movie[];
  classics?: Movie[];
}

export interface CinemaSummary {
  id: number;
  name: string;
  slug: string;
  address: string;
  city: string;
  locality?: string;
  latitude?: number;
  longitude?: number;
  facilities: string[];
  allowPayAtVenue: boolean;
  distanceKm?: number;
  chain?: string;
}

export interface ScreenSummary { id: number; name: string; screenType: string; formats: string[]; capacity: number }
export interface SeatPrice { seatType: string; label: string; price: number }

export interface ShowtimeSlot {
  showId: number;
  startTime: string;
  endTime: string;
  format: string;
  language: string;
  screenName: string;
  minPrice: number;
  maxPrice: number;
  prices: SeatPrice[];
  availability: "AVAILABLE" | "FILLING_FAST" | "ALMOST_FULL" | "SOLD_OUT";
  availableSeats: number;
  totalSeats: number;
}

export interface MovieShowtimes {
  movie: Movie;
  date: string;
  availableDates: string[];
  languages: string[];
  formats: string[];
  cinemas: { cinema: CinemaSummary; shows: ShowtimeSlot[] }[];
}

export interface CinemaShowtimes {
  cinema: CinemaSummary;
  date: string;
  movies: { movie: Movie; shows: ShowtimeSlot[] }[];
}

export type SeatDisplayStatus = "AVAILABLE" | "LOCKED" | "BOOKED" | "DISABLED";

export interface Seat {
  id: number;
  label: string;
  row: string;
  number: number;
  rowIndex: number;
  colIndex: number;
  seatType: string;
  price: number;
  status: SeatDisplayStatus;
  lockedByYou: boolean;
}

export interface ShowDetail {
  showId: number;
  movie: Movie;
  cinema: CinemaSummary;
  screen: ScreenSummary;
  date: string;
  startTime: string;
  endTime: string;
  format: string;
  language: string;
  pricing: SeatPrice[];
  layout: { rows: number; columns: number; rowLabels: string[]; seats: Seat[] };
  availableSeats: number;
  totalSeats: number;
  bookable: boolean;
  yourLock?: { lockId: string; seatIds: string[]; expiresAt: string };
  serverTime: string;
  maxSeatsPerBooking: number;
}

export interface SeatAvailability { showId: number; seats: { label: string; status: SeatDisplayStatus; lockedByYou: boolean }[]; serverTime: string }

export interface SeatLock { lockId: string; showId: number; seatIds: string[]; expiresAt: string; serverTime: string; ticketSubtotal: number }

export interface FoodItem { id: number; name: string; description?: string; category: string; price: number; imageUrl?: string; veg: boolean }

export interface Offer {
  id: number; title: string; description: string; offerType: string; couponCode?: string; imageUrl?: string;
  terms?: string; validTo: string; movieId?: number; cityId?: number;
}

export interface PriceBreakdown {
  ticketSubtotal: number; foodSubtotal: number; convenienceFee: number; tax: number; discount: number; grandTotal: number; currency: string;
}

export interface Ticket { ticketCode: string; status: string; qrToken?: string; qrImage?: string; issuedAt: string; usedAt?: string }

export interface Refund {
  id: number; bookingId: number; bookingReference: string; amount: number; status: "REQUESTED" | "PROCESSING" | "COMPLETED" | "FAILED";
  reason?: string; providerRefundId?: string; requestedAt: string; processedAt?: string;
}

export type BookingStatus = "PENDING" | "PAYMENT_PENDING" | "CONFIRMED" | "CANCELLED" | "EXPIRED" | "REFUND_PENDING" | "REFUNDED";

export interface Booking {
  id: number;
  reference: string;
  type: "MOVIE" | "EVENT";
  status: BookingStatus;
  movieId?: number;
  eventId?: number;
  showId?: number;
  sessionId?: number;
  cinemaId?: number;
  title: string;
  posterUrl?: string;
  certificate?: string;
  language?: string;
  format?: string;
  venueName: string;
  venueAddress: string;
  city: string;
  screenName?: string;
  startTime: string;
  endTime: string;
  seats: { label: string; seatType: string; price: number }[];
  tickets: { categoryId: number; name: string; unitPrice: number; quantity: number }[];
  food: { foodItemId: number; name: string; unitPrice: number; quantity: number }[];
  price: PriceBreakdown;
  couponCode?: string;
  paymentMethod?: string;
  payAtVenue: boolean;
  payAtVenueAllowed: boolean;
  expiresAt?: string;
  confirmedAt?: string;
  cancelledAt?: string;
  cancellable: boolean;
  cancellationDeadline?: string;
  ticket?: Ticket;
  refund?: Refund;
  createdAt: string;
  serverTime: string;
}

export interface PaymentMethodOption { method: string; label: string; description: string; provider?: string; available: boolean }
export interface PaymentMethods { methods: PaymentMethodOption[]; stripePublishableKey?: string; sandbox: boolean }

export interface PaymentAction {
  type: "MOCK_SIMULATOR" | "STRIPE_CLIENT_SECRET" | "RAZORPAY_CHECKOUT" | "REDIRECT" | "PAY_AT_VENUE";
  clientSecret?: string; publishableKey?: string; orderId?: string; keyId?: string; redirectUrl?: string; simulatorUrl?: string;
}

export interface PaymentInit {
  paymentId: string; bookingId: number; status: string; provider: string; method: string; amount: number; currency: string; action: PaymentAction;
}

export interface PaymentStatus {
  paymentId: string; bookingId: number; bookingReference: string; status: string; bookingStatus: BookingStatus;
  failureReason?: string; provider: string; method: string; amount: number; updatedAt: string;
}

export interface EventCategory { id: number; code: string; name: string; vertical: string; icon?: string }

export interface EventCard {
  id: number; title: string; slug: string; vertical: "EVENT" | "SPORTS" | "PLAY" | "ACTIVITY"; category: EventCategory;
  posterUrl?: string; bannerUrl?: string; venueName: string; city: string; nextStartTime?: string; minPrice?: number;
  artist?: string; teamHome?: string; teamAway?: string; languages?: string; ageRestriction?: string; featured: boolean; soldOut: boolean;
}

export interface Venue {
  id: number; name: string; slug: string; address: string; city: string; capacity?: number; facilities: string[];
  latitude?: number; longitude?: number; allowPayAtVenue: boolean;
}

export interface TicketCategory { id: number; name: string; description?: string; price: number; capacity: number; remaining: number; maxPerBooking: number; soldOut: boolean }
export interface EventSession { id: number; startTime: string; endTime: string; status: string; categories: TicketCategory[] }
export interface EventDetail { event: EventCard; description: string; durationMin?: number; venue: Venue; sessions: EventSession[] }

export interface SearchResults {
  query: string; movies: Movie[]; events: EventCard[]; cinemas: CinemaSummary[]; people: Person[]; venues: Venue[];
}

export interface Review {
  id: number; movieId: number; movieTitle?: string; userName: string; rating: number; title?: string; body?: string;
  status: string; createdAt: string; verifiedBooking: boolean;
}

export interface Favorite { id: number; targetType: "MOVIE" | "CINEMA" | "EVENT"; targetId: number; title: string; subtitle?: string; imageUrl?: string; link: string; createdAt: string }
export interface WatchlistItem { id: number; movie: Movie; remindMe: boolean; releaseDate: string; createdAt: string }
export interface Notification { id: number; type: string; title: string; body: string; link?: string; read: boolean; createdAt: string }

export interface TicketValidation {
  result: "VALID" | "ALREADY_USED" | "CANCELLED" | "EXPIRED" | "TOO_EARLY" | "INVALID";
  message: string; bookingReference?: string; title?: string; venue?: string; screen?: string; startTime?: string; seats: string[]; admits: number; usedAt?: string;
}

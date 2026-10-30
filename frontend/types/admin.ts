import type { Booking, Refund } from "./api";

export interface CastInput { name: string; character?: string; crewRole?: string }

export interface AdminMovie {
  id: number; title: string; slug: string; tagline?: string; synopsis: string; durationMin: number; certificate: string;
  releaseDate: string; posterUrl?: string; backdropUrl?: string; trailerUrl?: string; formats: string[]; status: string;
  featured: boolean; active: boolean; languages: string[]; genres: string[]; directors: string[]; cast: CastInput[]; rating: number; votes: number;
  tmdbId?: number; mediaLocked: boolean; mediaSyncedAt?: string; mediaSyncNote?: string; collection?: string; imdbRating?: number; catalogSource?: string;
}

export type MovieUpsert = Omit<AdminMovie, "id" | "slug" | "rating" | "votes" | "tmdbId" | "mediaSyncedAt" | "mediaSyncNote" | "collection" | "imdbRating" | "catalogSource">;

export interface MediaSyncResult { movieId: number; title: string; matched: boolean; tmdbId?: number; poster: boolean; trailer: boolean; trailerSource?: string; note: string }
export interface MediaStatus {
  running: boolean; tmdbConfigured: boolean; youtubeConfigured: boolean;
  activeMovies: number; withPoster: number; withTrailer: number; synced: number; matchedOnTmdb: number;
}

export interface AdminCinema {
  id: number; name: string; slug: string; cityId: number; city: string; locality?: string; address: string;
  latitude?: number; longitude?: number; facilities: string[]; allowPayAtVenue: boolean; cancellationAllowed: boolean; active: boolean; screens: number;
}

export interface RowInput {
  label: string; seatType: string; seatCount: number; offset: number; aisleAfter: number[]; disabledSeats: number[];
  seatTypeOverrides?: Record<string, string>;
}

export interface AdminScreen {
  id: number; cinemaId: number; cinemaName: string; name: string; screenType: string; formats: string[]; capacity: number;
  active: boolean; layout: RowInput[]; hasUpcomingShows: boolean;
}

export interface SeatType { id: number; code: string; name: string; priceDelta: number }

export interface AdminShow {
  id: number; movieId: number; movieTitle: string; cinemaId: number; cinemaName: string; screenId: number; screenName: string;
  date: string; startTime: string; endTime: string; format: string; language: string; basePrice: number; status: string; booked: number; total: number;
}

export interface AdminCategory { id?: number; name: string; description?: string; price: number; capacity: number; sold?: number; held?: number; maxPerBooking: number }
export interface AdminSession { id?: number; startTime: string; endTime: string; categories: AdminCategory[] }
export interface AdminEvent {
  id: number; title: string; slug: string; description: string; categoryCode: string; vertical: string; venueId: number; venueName: string;
  city: string; artist?: string; teamHome?: string; teamAway?: string; languages?: string; durationMin?: number; ageRestriction?: string;
  posterUrl?: string; bannerUrl?: string; featured: boolean; status: string; sessions: AdminSession[];
}

export interface AdminFood { id: number; cinemaId?: number; name: string; description?: string; category: string; price: number; imageUrl?: string; veg: boolean; available: boolean; sortOrder: number }

export interface AdminCoupon {
  id: number; code: string; description: string; discountType: "PERCENT" | "FLAT"; discountValue: number; maxDiscount?: number;
  minOrderAmount: number; validFrom: string; validTo: string; movieId?: number; cityId?: number; cinemaId?: number;
  paymentMethod?: string; firstBookingOnly: boolean; usageLimit?: number; perUserLimit: number; timesUsed: number; active: boolean;
}

export interface AdminOffer {
  id: number; title: string; description: string; offerType: string; couponCode?: string; cityId?: number; movieId?: number;
  imageUrl?: string; terms?: string; validFrom: string; validTo: string; active: boolean; sortOrder: number;
}

export interface AdminUser { id: number; fullName: string; email: string; phone?: string; roles: string[]; enabled: boolean; createdAt: string; lastLoginAt?: string }
export interface AdminBookingRow { booking: Booking; userId: number; userName: string; userEmail: string }
export interface AdminPayment {
  paymentId: string; bookingId: number; bookingReference: string; userId: number; provider: string; method: string; status: string;
  amount: number; refundedAmount: number; providerOrderId?: string; providerPaymentId?: string; failureReason?: string; createdAt: string; completedAt?: string;
}
export type AdminRefund = Refund;
export interface AuditLog { id: number; userId?: number; action: string; resource: string; resourceId?: string; ipAddress?: string; requestId?: string; metadata?: Record<string, unknown>; createdAt: string }

export interface Point { label: string; value: number }
export interface Ranked { name: string; count: number; amount: number }
export interface Dashboard {
  totalUsers: number; todaysBookings: number; todaysRevenue: number; activeMovies: number; activeShows: number; upcomingEvents: number;
  pendingRefunds: number; pendingReviews: number; revenueLast14Days: Point[]; bookingsLast14Days: Point[]; signupsLast14Days: Point[]; occupancyLast14Days: Point[];
}
export interface Analytics {
  from: string; to: string; grossSales: number; netSales: number; refunds: number; discounts: number; bookings: number; ticketsSold: number;
  averageTicketValue: number; occupancyPercent: number; revenueByDay: Point[]; bookingsByDay: Point[]; popularMovies: Ranked[];
  popularCinemas: Ranked[]; popularShowtimes: Ranked[]; couponUsage: Ranked[]; paymentMethods: Ranked[]; bookingStatus: Ranked[];
}

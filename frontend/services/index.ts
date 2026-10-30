import { api, idempotencyKey } from "@/lib/api-client";
import type * as T from "@/types/api";
import type * as A from "@/types/admin";

// Every backend URL lives here — components never build API paths themselves.

export const authApi = {
  login: (email: string, password: string) => api<T.AuthResponse>({ method: "POST", url: "/auth/login", data: { email, password } }),
  register: (data: { fullName: string; email: string; password: string; phone?: string }) =>
    api<T.AuthResponse>({ method: "POST", url: "/auth/register", data }),
  logout: () => api<void>({ method: "POST", url: "/auth/logout" }),
  me: () => api<T.User>({ url: "/auth/me" }),
  forgotPassword: (email: string) => api<void>({ method: "POST", url: "/auth/forgot-password", data: { email } }),
  resetPassword: (token: string, newPassword: string) => api<void>({ method: "POST", url: "/auth/reset-password", data: { token, newPassword } }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<T.AuthResponse>({ method: "POST", url: "/auth/change-password", data: { currentPassword, newPassword } }),
};

export const userApi = {
  update: (data: { fullName: string; phone?: string; avatarUrl?: string; preferredCityId?: number }) =>
    api<T.User>({ method: "PUT", url: "/users/me", data }),
  setCity: (cityId: number) => api<T.User>({ method: "PUT", url: "/users/me/city", data: { cityId } }),
  payments: () => api<T.PaymentStatus[]>({ url: "/users/me/payments" }),
  refunds: () => api<T.Refund[]>({ url: "/users/me/refunds" }),
};

export const catalogApi = {
  cities: () => api<T.City[]>({ url: "/cities" }),
  nearestCity: (lat: number, lng: number) => api<T.City>({ url: "/cities/nearest", params: { lat, lng } }),
  genres: () => api<{ id: number; name: string; slug: string }[]>({ url: "/meta/genres" }),
  config: () => api<Record<string, number | string | boolean>>({ url: "/meta/config" }),
  eventCategories: (vertical?: string) => api<T.EventCategory[]>({ url: "/meta/event-categories", params: { vertical } }),
  food: (cinemaId?: number) => api<T.FoodItem[]>({ url: "/food", params: { cinemaId } }),
  offers: (city?: string) => api<T.Offer[]>({ url: "/offers", params: { city } }),
  venue: (id: number) => api<T.Venue>({ url: `/venues/${id}` }),
};

export interface MovieQuery {
  city?: string; language?: string; genre?: string; format?: string; status?: string; minRating?: number;
  date?: string; cinemaId?: number; q?: string; sort?: string; page?: number; size?: number;
}

export const movieApi = {
  list: (q: MovieQuery) => api<T.Page<T.Movie>>({ url: "/movies", params: q }),
  home: (city?: string) => api<T.MovieHome>({ url: "/movies/home", params: { city } }),
  detail: (id: number | string) => api<T.MovieDetail>({ url: `/movies/${id}` }),
  similar: (id: number) => api<T.Movie[]>({ url: `/movies/${id}/similar` }),
  showtimes: (id: number, p: { city: string; date?: string; language?: string; format?: string; lat?: number; lng?: number }) =>
    api<T.MovieShowtimes>({ url: `/movies/${id}/showtimes`, params: p }),
  reviews: (id: number, page = 0) => api<T.Page<T.Review>>({ url: `/reviews/movie/${id}`, params: { page, size: 6 } }),
};

export const cinemaApi = {
  list: (city: string, lat?: number, lng?: number) => api<T.CinemaSummary[]>({ url: "/cinemas", params: { city, lat, lng } }),
  detail: (id: number) => api<{ cinema: T.CinemaSummary; screens: T.ScreenSummary[] }>({ url: `/cinemas/${id}` }),
  showtimes: (id: number, date?: string) => api<T.CinemaShowtimes>({ url: `/cinemas/${id}/showtimes`, params: { date } }),
};

export const showApi = {
  detail: (id: number) => api<T.ShowDetail>({ url: `/shows/${id}` }),
  availability: (id: number) => api<T.SeatAvailability>({ url: `/shows/${id}/seats` }),
};

export const seatApi = {
  lock: (showId: number, seatIds: string[]) => api<T.SeatLock>({ method: "POST", url: `/shows/${showId}/seat-lock`, data: { seatIds } }),
  release: (showId: number, lockId: string) => api<void>({ method: "DELETE", url: `/shows/${showId}/seat-lock/${lockId}` }),
};

export const bookingApi = {
  create: (lockId: string, food: { foodItemId: number; quantity: number }[], key = idempotencyKey("book")) =>
    api<T.Booking>({ method: "POST", url: "/bookings", data: { lockId, food }, headers: { "Idempotency-Key": key } }),
  createEvent: (sessionId: number, tickets: { categoryId: number; quantity: number }[], key = idempotencyKey("evbook")) =>
    api<T.Booking>({ method: "POST", url: "/bookings/event", data: { sessionId, tickets }, headers: { "Idempotency-Key": key } }),
  get: (id: number) => api<T.Booking>({ url: `/bookings/${id}` }),
  list: (tab: string, page = 0) => api<T.Page<T.Booking>>({ url: "/bookings", params: { tab, page, size: 20 } }),
  updateFood: (id: number, food: { foodItemId: number; quantity: number }[]) => api<T.Booking>({ method: "PUT", url: `/bookings/${id}/food`, data: { food } }),
  applyCoupon: (bookingId: number, couponCode: string) => api<T.Booking>({ method: "POST", url: "/coupons/apply", data: { bookingId, couponCode } }),
  removeCoupon: (id: number) => api<T.Booking>({ method: "DELETE", url: `/bookings/${id}/coupon` }),
  cancel: (id: number, reason?: string, key = idempotencyKey("cancel")) =>
    api<T.Booking>({ method: "POST", url: `/bookings/${id}/cancel`, data: { reason }, headers: { "Idempotency-Key": key } }),
};

export const paymentApi = {
  methods: (bookingId?: number) => api<T.PaymentMethods>({ url: "/payments/methods", params: { bookingId } }),
  initiate: (bookingId: number, method: string, key: string) =>
    api<T.PaymentInit>({ method: "POST", url: "/payments/initiate", data: { bookingId, method }, headers: { "Idempotency-Key": key } }),
  status: (paymentId: string) => api<T.PaymentStatus>({ url: `/payments/${paymentId}` }),
  verifyRazorpay: (data: { paymentId: string; razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }) =>
    api<T.PaymentStatus>({ method: "POST", url: "/payments/razorpay/verify", data }),
  simulatorDetails: (paymentId: string) => api<Record<string, string | number>>({ url: `/dev/payments/${paymentId}` }),
  simulate: (paymentId: string, outcome: "SUCCESS" | "FAILED" | "CANCELLED", deliveries = 1) =>
    api<{ deliveries: string[]; payment: T.PaymentStatus }>({ method: "POST", url: `/dev/payments/${paymentId}/simulate`, params: { deliveries }, data: { outcome } }),
};

export const eventApi = {
  list: (vertical: "events" | "sports" | "plays" | "activities", p: { city?: string; category?: string; date?: string; q?: string; page?: number }) =>
    api<T.Page<T.EventCard>>({ url: `/${vertical}`, params: { ...p, vertical: vertical === "events" ? "EVENT" : undefined, size: 30 } }),
  detail: (id: number) => api<T.EventDetail>({ url: `/events/${id}` }),
};

export const searchApi = {
  search: (q: string, city?: string, record = false, limit = 6) => api<T.SearchResults>({ url: "/search", params: { q, city, record, limit } }),
  suggestions: () => api<{ recent: string[]; trending: string[] }>({ url: "/search/suggestions" }),
};

export const engagementApi = {
  myReviews: () => api<T.Review[]>({ url: "/reviews/me" }),
  review: (data: { movieId: number; rating: number; title?: string; body?: string }) => api<T.Review>({ method: "POST", url: "/reviews", data }),
  favorites: () => api<T.Favorite[]>({ url: "/favorites" }),
  addFavorite: (targetType: string, targetId: number) => api<T.Favorite>({ method: "POST", url: "/favorites", data: { targetType, targetId } }),
  removeFavorite: (id: number) => api<void>({ method: "DELETE", url: `/favorites/${id}` }),
  watchlist: () => api<T.WatchlistItem[]>({ url: "/watchlist" }),
  addWatchlist: (movieId: number, remindMe = true) => api<T.WatchlistItem>({ method: "POST", url: "/watchlist", data: { movieId, remindMe } }),
  removeWatchlist: (movieId: number) => api<void>({ method: "DELETE", url: `/watchlist/${movieId}` }),
  notifications: (page = 0) => api<T.Page<T.Notification>>({ url: "/notifications", params: { page, size: 30 } }),
  unread: () => api<{ unread: number }>({ url: "/notifications/unread-count" }),
  markRead: (id: number) => api<void>({ method: "POST", url: `/notifications/${id}/read` }),
  markAllRead: () => api<void>({ method: "POST", url: "/notifications/read-all" }),
};

export const staffApi = {
  validate: (qrToken: string) => api<T.TicketValidation>({ method: "POST", url: "/tickets/validate", data: { qrToken } }),
  lookup: (reference: string) => api<T.Booking>({ url: `/staff/bookings/${encodeURIComponent(reference)}` }),
  counter: (reference: string, action: "PAID" | "CANCELLED") =>
    api<T.PaymentStatus>({ method: "POST", url: `/staff/bookings/${encodeURIComponent(reference)}/counter-payment`, data: { action } }),
};

export const adminApi = {
  dashboard: () => api<A.Dashboard>({ url: "/admin/dashboard" }),
  analytics: (from?: string, to?: string) => api<A.Analytics>({ url: "/admin/analytics", params: { from, to } }),
  movies: (q?: string) => api<T.Page<A.AdminMovie>>({ url: "/admin/movies", params: { q, size: 100 } }),
  createMovie: (data: A.MovieUpsert) => api<A.AdminMovie>({ method: "POST", url: "/admin/movies", data }),
  updateMovie: (id: number, data: A.MovieUpsert) => api<A.AdminMovie>({ method: "PUT", url: `/admin/movies/${id}`, data }),
  deleteMovie: (id: number) => api<void>({ method: "DELETE", url: `/admin/movies/${id}` }),
  syncMovieMedia: (id: number) => api<A.MediaSyncResult>({ method: "POST", url: `/admin/movies/${id}/sync-media` }),
  syncAllMedia: (force = false) => api<{ running: boolean }>({ method: "POST", url: "/admin/media/sync", params: { force } }),
  mediaStatus: () => api<A.MediaStatus>({ url: "/admin/media/status" }),
  cinemas: () => api<A.AdminCinema[]>({ url: "/admin/cinemas" }),
  createCinema: (data: unknown) => api<A.AdminCinema>({ method: "POST", url: "/admin/cinemas", data }),
  updateCinema: (id: number, data: unknown) => api<A.AdminCinema>({ method: "PUT", url: `/admin/cinemas/${id}`, data }),
  deleteCinema: (id: number) => api<void>({ method: "DELETE", url: `/admin/cinemas/${id}` }),
  venues: () => api<T.Venue[]>({ url: "/admin/venues" }),
  seatTypes: () => api<A.SeatType[]>({ url: "/admin/seat-types" }),
  screens: (cinemaId: number) => api<A.AdminScreen[]>({ url: "/admin/screens", params: { cinemaId } }),
  createScreen: (data: unknown) => api<A.AdminScreen>({ method: "POST", url: "/admin/screens", data }),
  updateScreen: (id: number, data: unknown) => api<A.AdminScreen>({ method: "PUT", url: `/admin/screens/${id}`, data }),
  saveLayout: (id: number, rows: A.RowInput[]) => api<A.AdminScreen>({ method: "PUT", url: `/admin/screens/${id}/layout`, data: { rows } }),
  shows: (p: { cinemaId?: number; movieId?: number; date?: string }) => api<T.Page<A.AdminShow>>({ url: "/admin/shows", params: { ...p, size: 200 } }),
  createShow: (data: unknown) => api<A.AdminShow>({ method: "POST", url: "/admin/shows", data }),
  updateShow: (id: number, data: { basePrice?: number; status?: string }) => api<A.AdminShow>({ method: "PATCH", url: `/admin/shows/${id}`, data }),
  events: () => api<T.Page<A.AdminEvent>>({ url: "/admin/events", params: { size: 100 } }),
  createEvent: (data: unknown) => api<A.AdminEvent>({ method: "POST", url: "/admin/events", data }),
  updateEvent: (id: number, data: unknown) => api<A.AdminEvent>({ method: "PUT", url: `/admin/events/${id}`, data }),
  food: () => api<A.AdminFood[]>({ url: "/admin/food" }),
  saveFood: (id: number | null, data: unknown) => api<A.AdminFood>({ method: id ? "PUT" : "POST", url: id ? `/admin/food/${id}` : "/admin/food", data }),
  coupons: () => api<A.AdminCoupon[]>({ url: "/admin/coupons" }),
  saveCoupon: (id: number | null, data: unknown) => api<A.AdminCoupon>({ method: id ? "PUT" : "POST", url: id ? `/admin/coupons/${id}` : "/admin/coupons", data }),
  offers: () => api<A.AdminOffer[]>({ url: "/admin/offers" }),
  saveOffer: (id: number | null, data: unknown) => api<A.AdminOffer>({ method: id ? "PUT" : "POST", url: id ? `/admin/offers/${id}` : "/admin/offers", data }),
  deleteOffer: (id: number) => api<void>({ method: "DELETE", url: `/admin/offers/${id}` }),
  users: (q?: string, role?: string) => api<T.Page<A.AdminUser>>({ url: "/admin/users", params: { q, role, size: 100 } }),
  updateUser: (id: number, roles: string[], enabled: boolean) => api<A.AdminUser>({ method: "PUT", url: `/admin/users/${id}`, data: { roles, enabled } }),
  bookings: (p: { status?: string; q?: string; date?: string; page?: number }) => api<T.Page<A.AdminBookingRow>>({ url: "/admin/bookings", params: { ...p, size: 50 } }),
  cancelBooking: (id: number, reason?: string) => api<T.Booking>({ method: "POST", url: `/admin/bookings/${id}/cancel`, data: { reason } }),
  payments: (p: { status?: string; provider?: string; page?: number }) => api<T.Page<A.AdminPayment>>({ url: "/admin/payments", params: { ...p, size: 50 } }),
  refunds: (status?: string) => api<T.Page<A.AdminRefund>>({ url: "/admin/refunds", params: { status, size: 100 } }),
  retryRefund: (id: number) => api<A.AdminRefund>({ method: "POST", url: `/admin/refunds/${id}/retry` }),
  completeRefund: (id: number) => api<A.AdminRefund>({ method: "POST", url: `/admin/refunds/${id}/complete` }),
  reviews: () => api<T.Page<T.Review>>({ url: "/admin/reviews" }),
  moderate: (id: number, decision: "approve" | "reject") => api<T.Review>({ method: "POST", url: `/admin/reviews/${id}/${decision}` }),
  audit: (action?: string, resource?: string) => api<T.Page<A.AuditLog>>({ url: "/admin/audit-logs", params: { action, resource } }),
};

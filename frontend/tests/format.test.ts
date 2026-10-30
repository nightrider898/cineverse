import { calendarDay, duration, inr } from "@/lib/format";
import { ApiError, toApiError } from "@/lib/api-client";

describe("format helpers", () => {
  it("formats rupees the Indian way", () => {
    expect(inr(125000)).toBe("₹1,25,000");
    expect(inr(909, { decimals: true })).toBe("₹909.00");
  });
  it("formats calendar days without timezone drift", () => {
    const d = calendarDay("2026-09-28");
    expect(d.day).toBe(28);
    expect(d.weekday).toBe("MON");
  });
  it("formats runtimes", () => expect(duration(148)).toBe("2h 28m"));
});

describe("api error normalisation", () => {
  it("maps a missing response to the offline message", () => {
    const e = toApiError({ isAxiosError: true, message: "Network Error" });
    expect(e).toBeInstanceOf(ApiError);
    expect(e.code).toBe("NETWORK_ERROR");
    expect(e.message).toBe("Unable to connect to BookMyEvent servers.");
  });
  it("keeps backend business codes", () => {
    const e = toApiError({ response: { status: 409, data: { success: false, error: { code: "SEAT_LOCKED", message: "taken" } } } });
    expect(e.code).toBe("SEAT_LOCKED");
    expect(e.status).toBe(409);
  });
});

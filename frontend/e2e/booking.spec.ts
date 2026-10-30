import { expect, test } from "@playwright/test";

const PASSWORD = process.env.DEMO_USER_PASSWORD || "Demo@12345";

/**
 * Spec §120 journey, entirely through the real UI + Spring Boot + PostgreSQL:
 * city -> movie -> date/cinema/show -> seats (lock + countdown) -> food -> coupon -> mock payment
 * -> confirmation with QR -> My Bookings -> cancel -> refund tracking -> notification.
 */
test("customer books a movie end to end", async ({ page }) => {
  // Register a fresh customer so WELCOME100 (first booking) applies.
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto("/register");
  await page.getByLabel("Full name").fill("E2E Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill("Secret123");
  await page.getByLabel("Confirm password").fill("Secret123");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL("/");

  // City
  const dialog = page.getByRole("dialog", { name: "Select your city" });
  if (await dialog.isVisible()) await dialog.getByRole("button", { name: "Hyderabad" }).click();

  // Movie -> showtimes
  await page.goto("/movies");
  await page.getByRole("link", { name: /details/ }).first().click();
  await page.getByRole("link", { name: "Book Tickets" }).first().click();
  await expect(page.getByRole("tablist", { name: "Choose date" })).toBeVisible();
  const slot = page.locator('a[href^="/booking/"]').first();
  await slot.click();

  // Seats
  await expect(page.getByRole("grid", { name: "Seat map" })).toBeVisible();
  const free = page.getByRole("button", { name: /, available,/ });
  await free.nth(0).click();
  await free.nth(1).click();
  await page.getByRole("button", { name: "Reserve seats" }).click();

  // Food (lock countdown visible)
  await expect(page.getByRole("timer")).toContainText("Seats reserved for 0");
  await page.getByRole("button", { name: /Add Salted Popcorn/ }).click();
  await page.getByRole("button", { name: "Continue to checkout" }).click();

  // Checkout: coupon + mock payment
  await page.getByLabel("Coupon code").fill("WELCOME100");
  await page.getByRole("button", { name: "Apply" }).click();
  await expect(page.getByText("WELCOME100")).toBeVisible();
  await page.getByRole("radio", { name: /UPI/ }).first().click();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: /^Pay ₹/ }).click();
  await page.getByLabel(/Deliver the webhook twice/).check();
  await page.getByRole("button", { name: "Simulate successful payment" }).click();

  // Confirmation with QR
  await expect(page.getByRole("heading", { name: "You're all set!" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("img", { name: /Entry QR code/ })).toBeVisible();
  const ref = await page.locator("b.font-mono").first().innerText();

  // My Bookings -> cancel -> refund
  await page.goto("/bookings");
  await page.getByText(ref).click();
  await page.getByRole("button", { name: "Cancel & request refund" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Cancel booking" }).click();
  await expect(page.getByText("Refund", { exact: true })).toBeVisible();

  // Notification
  await page.goto("/notifications");
  await expect(page.getByText(/Booking confirmed/)).toBeVisible();
});

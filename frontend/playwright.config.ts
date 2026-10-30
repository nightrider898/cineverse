import { defineConfig, devices } from "@playwright/test";

// E2E against a running stack: backend on :8080 (dev profile, mock payments) and `npm run dev` on :3000.
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  use: { baseURL: process.env.E2E_BASE_URL || "http://localhost:3000", trace: "retain-on-failure", ...(process.env.PW_CHROMIUM ? { launchOptions: { executablePath: process.env.PW_CHROMIUM } } : {}) },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],
});

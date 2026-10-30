/** Public company details (shown in the footer). Override with NEXT_PUBLIC_* variables in .env.local. */
export const brand = {
  name: process.env.NEXT_PUBLIC_APP_NAME || "BookMyEvent",
  motto: process.env.NEXT_PUBLIC_BRAND_MOTTO || "Every seat tells a story.",
  supportEmail: process.env.NEXT_PUBLIC_SUPPORT_EMAIL || "support@bookmyevent.in",
  socials: [
    { label: "Instagram", href: process.env.NEXT_PUBLIC_INSTAGRAM_URL || "https://www.instagram.com/" },
    { label: "X", href: process.env.NEXT_PUBLIC_X_URL || "https://x.com/" },
    { label: "Facebook", href: process.env.NEXT_PUBLIC_FACEBOOK_URL || "https://www.facebook.com/" },
    { label: "YouTube", href: process.env.NEXT_PUBLIC_YOUTUBE_URL || "https://www.youtube.com/" },
    { label: "LinkedIn", href: process.env.NEXT_PUBLIC_LINKEDIN_URL || "https://www.linkedin.com/" },
  ],
};

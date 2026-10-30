import { z } from "zod";

// Mirrors the backend Bean Validation rules so users get instant feedback; the server re-validates everything.
export const password = z.string().min(8, "At least 8 characters").max(72).regex(/[A-Za-z]/, "Include a letter").regex(/\d/, "Include a number");

export const loginSchema = z.object({
  email: z.string().trim().email("Enter a valid email"),
  password: z.string().min(1, "Enter your password"),
});

export const registerSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your name").max(120),
  email: z.string().trim().email("Enter a valid email"),
  phone: z.string().trim().regex(/^$|^[+0-9 -]{7,20}$/, "Enter a valid phone number").optional(),
  password,
  confirm: z.string(),
}).refine((v) => v.password === v.confirm, { path: ["confirm"], message: "Passwords don't match" });

export const resetSchema = z.object({ newPassword: password, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords don't match" });

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your name").max(120),
  phone: z.string().trim().regex(/^$|^[+0-9 -]{7,20}$/, "Enter a valid phone number").optional(),
  avatarUrl: z.string().trim().url("Enter a valid URL").or(z.literal("")).optional(),
});

export const changePasswordSchema = z.object({ currentPassword: z.string().min(1, "Required"), newPassword: password, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords don't match" });

export const CONSENT_VERSION = "2026-11-18";

export interface Signup {
  email: string;
  platform: "android" | "ios";
  newsletter: boolean;
}

const EMAIL = /^[^\s@]{1,64}@[^\s@.]+(\.[^\s@.]+)+$/;

/** Checks what the form sent. A filled honeypot ("website") is reported as ok to bots: the caller drops it silently. */
export function parseSignup(data: Record<string, unknown>): { signup: Signup } | { spam: true } | { error: string } {
  if (typeof data.website === "string" && data.website !== "") return { spam: true };
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  if (email.length > 254 || !EMAIL.test(email)) return { error: "email" };
  if (data.platform !== "android" && data.platform !== "ios") return { error: "platform" };
  if (!flag(data.consent_testing)) return { error: "consent" };
  return { signup: { email, platform: data.platform, newsletter: flag(data.consent_newsletter) } };
}

function flag(v: unknown): boolean {
  return v === true || v === "on" || v === "1" || v === "true";
}

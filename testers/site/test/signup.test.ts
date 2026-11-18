import { test } from "node:test";
import assert from "node:assert/strict";
import { parseSignup } from "../src/signup.ts";

const ok = { email: " Ann@Example.NL ", platform: "ios", consent_testing: "on" };

test("accepts a good sign-up and normalises the email", () => {
  assert.deepEqual(parseSignup(ok), { signup: { email: "ann@example.nl", platform: "ios", newsletter: false } });
  assert.deepEqual(parseSignup({ ...ok, consent_newsletter: "on" }), { signup: { email: "ann@example.nl", platform: "ios", newsletter: true } });
});
test("needs consent for testing", () => assert.deepEqual(parseSignup({ ...ok, consent_testing: undefined }), { error: "consent" }));
test("rejects a bad email or platform", () => {
  assert.deepEqual(parseSignup({ ...ok, email: "nope" }), { error: "email" });
  assert.deepEqual(parseSignup({ ...ok, platform: "windows" }), { error: "platform" });
});
test("drops bots that fill the hidden field", () => assert.deepEqual(parseSignup({ ...ok, website: "x" }), { spam: true }));

import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { apiOrigin, hostingConfig } from "../scripts/hosting-config.mjs";
const template = JSON.parse(readFileSync(new URL("../../firebase.template.json", import.meta.url), "utf8"));

test("normalizes an HTTPS origin", () => {
  assert.equal(apiOrigin(" https://API.example.test/ "), "https://api.example.test");
});

test("rejects missing values and values unsafe for a CSP origin", () => {
  for (const value of [undefined, "", " ", "not-a-url", "http://api.example.test", "https://user:password@api.example.test", "https://api.example.test/path", "https://api.example.test?key=value", "https://api.example.test#fragment", "https://api.example.test; https://other.example.test"]) {
    assert.throws(() => apiOrigin(value));
  }
});

test("generates CSP from the selected environment without mutating the template", () => {
  for (const origin of ["https://staging.example.test", "https://production.example.test"]) {
    const config = hostingConfig(template, origin);
    const csp = config.hosting.headers.flatMap(group => group.headers).find(header => header.key === "Content-Security-Policy").value;
    assert.ok(csp.includes(`connect-src 'self' ${origin};`));
    assert.ok(!JSON.stringify(config).includes("__API_ORIGIN__"));
    assert.equal(config.hosting.target, "invoiceparse");
  }
  assert.ok(JSON.stringify(template).includes("__API_ORIGIN__"));
});

test("fails if the template loses its CSP placeholder", () => {
  assert.throws(() => hostingConfig({ hosting: { headers: [] } }, "https://api.example.test"));
});

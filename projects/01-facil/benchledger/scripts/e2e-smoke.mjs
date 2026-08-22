import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

const apiUrl = process.env.E2E_API_URL ?? "http://api:3000/api";
const webUrl = process.env.E2E_WEB_URL ?? "http://web:5173";

async function request(path, options = {}) {
  const response = await fetch(`${apiUrl}${path}`, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json", ...options.headers } : options.headers,
  });
  const body = await response.json().catch(() => null);
  assert.ok(response.ok, `${options.method ?? "GET"} ${path} returned ${response.status}: ${JSON.stringify(body)}`);
  return body;
}

const health = await request("/health");
assert.equal(health.status, "ok");

const suffix = randomUUID().slice(0, 8).toUpperCase();
let equipment = await request("/equipment", {
  method: "POST",
  body: JSON.stringify({
    assetTag: `E2E-${suffix}`,
    name: "E2E reference meter",
    category: "Measurement",
    location: "Automated test bench",
    calibrationIntervalDays: 365,
  }),
});
assert.equal(equipment.status, "OVERDUE");

equipment = await request(`/equipment/${equipment.id}`, {
  method: "PATCH",
  body: JSON.stringify({ location: "Verified test bench" }),
});
assert.equal(equipment.location, "Verified test bench");

const calibrationTime = new Date(Date.now() - 2_000).toISOString();
equipment = await request(`/equipment/${equipment.id}/events`, {
  method: "POST",
  body: JSON.stringify({
    type: "CALIBRATION",
    occurredAt: calibrationTime,
    successful: true,
    note: "Automated acceptance calibration",
  }),
});
assert.equal(equipment.status, "ACTIVE");

const failedTime = new Date(Date.now() - 1_000).toISOString();
equipment = await request(`/equipment/${equipment.id}/events`, {
  method: "POST",
  body: JSON.stringify({
    type: "CALIBRATION",
    occurredAt: failedTime,
    successful: false,
    note: "Intentional E2E failure",
  }),
});
assert.equal(equipment.status, "CALIBRATION_FAILED");
const failedEvent = equipment.events.find((event) => event.type === "CALIBRATION" && event.successful === false);
assert.ok(failedEvent, "failed calibration event was not returned");

equipment = await request(`/equipment/${equipment.id}/events/${failedEvent.id}/corrections`, {
  method: "POST",
  body: JSON.stringify({ note: "Intentional event corrected by E2E flow" }),
});
assert.equal(equipment.status, "ACTIVE");
assert.ok(equipment.events.some((event) => event.correctsEventId === failedEvent.id));

equipment = await request(`/equipment/${equipment.id}/archive`, {
  method: "POST",
  body: JSON.stringify({ note: "E2E removal from service" }),
});
assert.equal(equipment.status, "OUT_OF_SERVICE");

equipment = await request(`/equipment/${equipment.id}/restore`, {
  method: "POST",
  body: JSON.stringify({ note: "E2E return to service" }),
});
assert.equal(equipment.status, "ACTIVE");

equipment = await request(`/equipment/${equipment.id}`, {
  method: "DELETE",
  body: JSON.stringify({ note: "E2E logical deletion with history" }),
});
assert.equal(equipment.status, "DELETED");
assert.ok(equipment.deletedAt);
assert.ok(equipment.events.some((event) => event.type === "DELETED"));

const inventory = await request("/equipment");
assert.ok(inventory.some((item) => item.id === equipment.id));

const blockedMutation = await fetch(`${apiUrl}/equipment/${equipment.id}`, {
  method: "PATCH",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ location: "Should not change" }),
});
assert.equal(blockedMutation.status, 409);

equipment = await request(`/equipment/${equipment.id}/restore-deleted`, {
  method: "POST",
  body: JSON.stringify({ note: "E2E restoration" }),
});
assert.equal(equipment.status, "ACTIVE");
assert.equal(equipment.deletedAt, null);
assert.ok(equipment.events.some((event) => event.type === "RESTORED_FROM_DELETION"));

const webResponse = await fetch(webUrl);
assert.equal(webResponse.status, 200);
assert.match(await webResponse.text(), /BenchLedger/);

console.log(`E2E passed for ${equipment.assetTag}: lifecycle, audited soft deletion and restoration.`);

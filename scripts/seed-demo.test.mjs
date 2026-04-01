/**
 * seed-demo.test.mjs — Unit tests for event-shape generation logic.
 * No network calls; validates that every event satisfies the DTO contract
 * expected by the API (/v1/ingest → TelemetryBatch).
 *
 * Run with Node.js built-in test runner (Node 18+):
 *   node --test scripts/seed-demo.test.mjs
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

// ── Inline the pure generator functions (no side-effects, no network) ─────────
// These mirror the implementations in seed-demo.mjs exactly.

function pick(arr) {
	return arr[Math.floor(Math.random() * arr.length)];
}
function rand(min, max) {
	return min + Math.random() * (max - min);
}
function randInt(min, max) {
	return Math.floor(rand(min, max));
}

function base(sessionId, userId, release, platform, device, osVersion, tsISO) {
	return {
		event_id: randomUUID(),
		session_id: sessionId,
		user_id: userId,
		app_name: "DemoApp",
		environment: "production",
		app_version: "1.0.0",
		release,
		platform,
		device_model: device,
		os_version: osVersion,
		timestamp: tsISO,
		trace_id: null,
	};
}

function makeAppStart(
	sessionId,
	userId,
	release,
	platform,
	device,
	osVersion,
	ts,
) {
	const durationMs = release === "v1.1.0" ? rand(600, 1800) : rand(150, 500);
	return {
		...base(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			ts.toISOString(),
		),
		event_type: "app_start",
		cold_start: Math.random() > 0.4,
		duration_ms: Math.round(durationMs),
	};
}

function makeScreenView(
	sessionId,
	userId,
	release,
	platform,
	device,
	osVersion,
	ts,
	screenName,
) {
	return {
		...base(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			ts.toISOString(),
		),
		event_type: "screen_view",
		screen_name: screenName,
	};
}

function makeApiTiming(
	sessionId,
	userId,
	release,
	platform,
	device,
	osVersion,
	ts,
	path = "/checkout",
	method = "POST",
	forceError = false,
) {
	const isRegressed = release === "v1.1.0";
	const durationMs = isRegressed ? rand(3000, 6000) : rand(200, 800);
	const isError =
		forceError ||
		(isRegressed ? Math.random() < 0.18 : Math.random() < 0.03);
	const statusCode = isError ? 500 : 200;
	return {
		...base(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			ts.toISOString(),
		),
		event_type: "api_timing",
		method,
		path,
		status_code: statusCode,
		duration_ms: Math.round(durationMs),
		request_size: randInt(512, 4096),
		response_size: isError ? 512 : randInt(1024, 8192),
		error_message: isError ? "HTTP " + statusCode : null,
	};
}

function makeError(
	sessionId,
	userId,
	release,
	platform,
	device,
	osVersion,
	ts,
	screenName,
) {
	return {
		...base(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			ts.toISOString(),
		),
		event_type: "error",
		error_type: "NullPointerException",
		error_class: "java.lang.NullPointerException",
		error_message: "Attempt to invoke virtual method on a null object",
		stacktrace:
			"java.lang.NullPointerException\n  at com.mobobs.DemoApp.main(DemoApp.java:42)",
		screen_name: screenName ?? null,
	};
}

function makeCustomEvent(
	sessionId,
	userId,
	release,
	platform,
	device,
	osVersion,
	ts,
) {
	return {
		...base(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			ts.toISOString(),
		),
		event_type: "custom_event",
		event_name: "button_tap",
		event_data: { release_flag: release === "v1.1.0" ? "B" : "A" },
	};
}

// ── Required fields per API DTO contract ──────────────────────────────────────

const COMMON_REQUIRED = [
	"event_id",
	"event_type",
	"session_id",
	"app_name",
	"environment",
	"app_version",
	"release",
	"platform",
	"timestamp",
];

function assertCommon(event, label) {
	for (const field of COMMON_REQUIRED) {
		assert.ok(
			event[field] != null && event[field] !== "",
			`${label}: missing required field "${field}"`,
		);
	}
	// timestamp must be a parseable ISO string
	const ms = Date.parse(event.timestamp);
	assert.ok(
		!isNaN(ms),
		`${label}: timestamp is not a valid ISO string: ${event.timestamp}`,
	);
}

// ── Setup ─────────────────────────────────────────────────────────────────────

const SESSION_ID = randomUUID();
const USER_ID = "user_test01";
const NOW = new Date();

function sampleArgs(release = "v1.0.0") {
	return [SESSION_ID, USER_ID, release, "ios", "iPhone14", "17.0", NOW];
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("makeAppStart", () => {
	it("has all required common fields", () => {
		const e = makeAppStart(...sampleArgs());
		assertCommon(e, "AppStart");
		assert.equal(e.event_type, "app_start");
	});

	it("cold_start is boolean", () => {
		const e = makeAppStart(...sampleArgs());
		assert.equal(typeof e.cold_start, "boolean");
	});

	it("duration_ms is a positive integer", () => {
		const e = makeAppStart(...sampleArgs());
		assert.equal(typeof e.duration_ms, "number");
		assert.ok(e.duration_ms > 0);
		assert.equal(e.duration_ms, Math.round(e.duration_ms));
	});

	it("v1.1.0 duration_ms is generally higher than v1.0.0", () => {
		// Sample 20 events to reduce flakiness
		const v10Avg =
			Array.from(
				{ length: 20 },
				() => makeAppStart(...sampleArgs("v1.0.0")).duration_ms,
			).reduce((a, b) => a + b, 0) / 20;
		const v11Avg =
			Array.from(
				{ length: 20 },
				() => makeAppStart(...sampleArgs("v1.1.0")).duration_ms,
			).reduce((a, b) => a + b, 0) / 20;
		assert.ok(
			v11Avg > v10Avg,
			`Expected v1.1.0 avg (${v11Avg}) > v1.0.0 avg (${v10Avg})`,
		);
	});
});

describe("makeScreenView", () => {
	it("has all required common fields and screen_name", () => {
		const e = makeScreenView(...sampleArgs(), "HomeScreen");
		assertCommon(e, "ScreenView");
		assert.equal(e.event_type, "screen_view");
		assert.equal(e.screen_name, "HomeScreen");
	});
});

describe("makeApiTiming", () => {
	it("has all required common fields with /checkout POST shape", () => {
		const e = makeApiTiming(...sampleArgs(), "/checkout", "POST", false);
		assertCommon(e, "ApiTiming");
		assert.equal(e.event_type, "api_timing");
		assert.equal(e.path, "/checkout");
		assert.equal(e.method, "POST");
		assert.ok([200, 500, 503].includes(e.status_code));
		assert.ok(e.duration_ms > 0);
	});

	it("forceError sets non-200 status code", () => {
		// With forceError=true, status_code must be non-2xx
		for (let i = 0; i < 10; i++) {
			const e = makeApiTiming(...sampleArgs(), "/checkout", "POST", true);
			assert.ok(
				e.status_code >= 400,
				`Expected error status, got ${e.status_code}`,
			);
		}
	});

	it("v1.1.0 duration_ms is generally higher than v1.0.0", () => {
		const v10Avg =
			Array.from(
				{ length: 20 },
				() =>
					makeApiTiming(
						...sampleArgs("v1.0.0"),
						"/checkout",
						"POST",
						false,
					).duration_ms,
			).reduce((a, b) => a + b, 0) / 20;
		const v11Avg =
			Array.from(
				{ length: 20 },
				() =>
					makeApiTiming(
						...sampleArgs("v1.1.0"),
						"/checkout",
						"POST",
						false,
					).duration_ms,
			).reduce((a, b) => a + b, 0) / 20;
		assert.ok(
			v11Avg > v10Avg,
			`Expected v1.1.0 checkout avg (${v11Avg.toFixed(0)}ms) > v1.0.0 (${v10Avg.toFixed(0)}ms)`,
		);
	});
});

describe("makeError", () => {
	it("has all required common and error-specific fields", () => {
		const e = makeError(...sampleArgs(), "HomeScreen");
		assertCommon(e, "Error");
		assert.equal(e.event_type, "error");
		assert.ok(e.error_type, "error_type must be non-empty");
		assert.ok(e.error_class, "error_class must be non-empty");
		assert.ok(e.error_message, "error_message must be non-empty");
	});

	it("screen_name is passed through", () => {
		const e = makeError(...sampleArgs(), "CheckoutScreen");
		assert.equal(e.screen_name, "CheckoutScreen");
	});
});

describe("makeCustomEvent", () => {
	it("has all required common fields and event_name", () => {
		const e = makeCustomEvent(...sampleArgs());
		assertCommon(e, "CustomEvent");
		assert.equal(e.event_type, "custom_event");
		assert.ok(e.event_name, "event_name must be non-empty");
		assert.ok(
			e.event_data && typeof e.event_data === "object",
			"event_data must be an object",
		);
	});

	it('release_flag is "A" for v1.0.0 and "B" for v1.1.0', () => {
		assert.equal(
			makeCustomEvent(...sampleArgs("v1.0.0")).event_data.release_flag,
			"A",
		);
		assert.equal(
			makeCustomEvent(...sampleArgs("v1.1.0")).event_data.release_flag,
			"B",
		);
	});
});

describe("DTO contract — TelemetryBatch shape", () => {
	it("every generated event type passes common field validation", () => {
		const events = [
			makeAppStart(...sampleArgs()),
			makeScreenView(...sampleArgs(), "HomeScreen"),
			makeApiTiming(...sampleArgs(), "/checkout", "POST", false),
			makeError(...sampleArgs(), "HomeScreen"),
			makeCustomEvent(...sampleArgs()),
		];
		for (const e of events) {
			assertCommon(e, e.event_type);
		}
	});

	it("each event has a unique event_id", () => {
		const events = Array.from({ length: 10 }, () =>
			makeAppStart(...sampleArgs()),
		);
		const ids = new Set(events.map((e) => e.event_id));
		assert.equal(ids.size, 10, "Each event must have a unique event_id");
	});

	it("events wrapped in {events:[]} form a valid TelemetryBatch", () => {
		const batch = {
			events: [
				makeAppStart(...sampleArgs()),
				makeScreenView(...sampleArgs(), "FeedScreen"),
			],
		};
		assert.ok(Array.isArray(batch.events));
		assert.equal(batch.events.length, 2);
	});
});

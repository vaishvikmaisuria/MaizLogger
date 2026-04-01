#!/usr/bin/env node
/**
 * seed-demo.mjs — Realistic telemetry seed generator for the Mobile Observability Platform.
 *
 * Generates 200+ sessions with two weighted releases (70% v1.0.0 / 30% v1.1.0) and
 * realistic traffic patterns:
 *   • Normal traffic      — healthy app_start, screen_view, api_timing flows
 *   • Slow screens        — HomeScreen / ProfileScreen with elevated render times (v1.1.0)
 *   • Slow APIs           — POST /checkout p95 regression (v1.1.0: 3–6 s vs v1.0.0: 200–800 ms)
 *   • Release regression  — v1.1.0 has 3× higher error rate (NullPointerException spike)
 *   • Error spikes        — Concentrated burst of errors near run end
 *
 * Usage:
 *   node scripts/seed-demo.mjs
 *
 * The script prompts for the ingestion API key printed by the API on startup.
 * Set INGEST_API_KEY env var to skip the prompt:
 *   INGEST_API_KEY=mobo_... node scripts/seed-demo.mjs
 */

import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output, env } from "node:process";
import { randomUUID } from "node:crypto";

// ── Configuration ─────────────────────────────────────────────────────────────

const BASE_URL = env.INGEST_URL ?? "http://localhost:8000";
const BATCH_SIZE = parseInt(env.BATCH_SIZE ?? "20", 10);
const SESSION_COUNT = parseInt(env.SESSION_COUNT ?? "220", 10);

const RELEASES = [
	{ name: "v1.0.0", weight: 0.7 }, // stable baseline
	{ name: "v1.1.0", weight: 0.3 }, // regression release
];

const PLATFORMS = ["ios", "android"];
const DEVICES = {
	ios: ["iPhone14", "iPhone15Pro", "iPadAir5"],
	android: ["Pixel7", "SamsungS23", "OnePlus11"],
};
const OS_VERSIONS = {
	ios: ["17.0", "17.2", "16.5"],
	android: ["13", "14", "12"],
};

const SCREENS = [
	"HomeScreen",
	"ProfileScreen",
	"FeedScreen",
	"SettingsScreen",
	"CheckoutScreen",
];
const APP_NAME = "DemoApp";
const ENVIRONMENT = "production";
const APP_VERSION = "1.0.0";

// ── Weighted random helpers ────────────────────────────────────────────────────

function pick(arr) {
	return arr[Math.floor(Math.random() * arr.length)];
}
function rand(min, max) {
	return min + Math.random() * (max - min);
}
function randInt(min, max) {
	return Math.floor(rand(min, max));
}

/** Pick a release according to weights. */
function pickRelease() {
	const r = Math.random();
	let cum = 0;
	for (const rel of RELEASES) {
		cum += rel.weight;
		if (r < cum) return rel.name;
	}
	return RELEASES[RELEASES.length - 1].name;
}

/** Base fields shared by every event type. */
function base(sessionId, userId, release, platform, device, osVersion, tsISO) {
	return {
		event_id: randomUUID(),
		session_id: sessionId,
		user_id: userId,
		app_name: APP_NAME,
		environment: ENVIRONMENT,
		app_version: APP_VERSION,
		release,
		platform,
		device_model: device,
		os_version: osVersion,
		timestamp: tsISO,
		trace_id: null,
	};
}

// ── Event builders ─────────────────────────────────────────────────────────────

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

/**
 * POST /checkout — the key regression endpoint.
 * v1.1.0: 3–6 s latency (regression), higher error rate
 * v1.0.0: 200–800 ms (baseline)
 */
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
	const statusCode = isError ? (Math.random() < 0.5 ? 500 : 503) : 200;
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
	const v11errors = [
		[
			"NullPointerException",
			"java.lang.NullPointerException",
			"Attempt to invoke virtual method on a null object",
		],
		[
			"IllegalStateException",
			"java.lang.IllegalStateException",
			"Fragment not attached to activity",
		],
	];
	const baseErrors = [
		[
			"NetworkError",
			"com.mobobs.NetworkError",
			"Connection timed out after 30 000 ms",
		],
		[
			"JSONParseError",
			"com.mobobs.ParseError",
			"Unexpected token at position 0",
		],
	];
	const pool =
		release === "v1.1.0" ? [...v11errors, ...baseErrors] : baseErrors;
	const [errorType, errorClass, errorMessage] = pick(pool);
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
		error_type: errorType,
		error_class: errorClass,
		error_message: errorMessage,
		stacktrace: `${errorClass}: ${errorMessage}\n  at com.mobobs.DemoApp.main(DemoApp.java:42)`,
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
		event_name: pick([
			"button_tap",
			"form_submit",
			"cart_add",
			"purchase_complete",
		]),
		event_data: { release_flag: release === "v1.1.0" ? "B" : "A" },
	};
}

// ── Session generator ──────────────────────────────────────────────────────────

/**
 * Build a realistic session: app_start → N screens → some API calls → maybe error.
 * All events are jittered within a ~10-minute window starting at `sessionStart`.
 */
function generateSession(sessionStart, isErrorSpike) {
	const sessionId = randomUUID();
	const userId = `user_${randomUUID().slice(0, 8)}`;
	const release = pickRelease();
	const platform = pick(PLATFORMS);
	const device = pick(DEVICES[platform]);
	const osVersion = pick(OS_VERSIONS[platform]);

	const events = [];
	let cursor = new Date(sessionStart);

	const jitter = (ms) => {
		cursor = new Date(cursor.getTime() + ms);
		return new Date(cursor);
	};

	// 1. App start
	events.push(
		makeAppStart(
			sessionId,
			userId,
			release,
			platform,
			device,
			osVersion,
			jitter(0),
		),
	);

	// 2. Screen views (2–5 screens per session)
	const screenCount = randInt(2, 6);
	for (let i = 0; i < screenCount; i++) {
		const screen = pick(SCREENS);
		jitter(randInt(500, 3000));
		events.push(
			makeScreenView(
				sessionId,
				userId,
				release,
				platform,
				device,
				osVersion,
				new Date(cursor),
				screen,
			),
		);

		// POST /checkout after CheckoutScreen
		if (screen === "CheckoutScreen") {
			jitter(randInt(300, 800));
			events.push(
				makeApiTiming(
					sessionId,
					userId,
					release,
					platform,
					device,
					osVersion,
					new Date(cursor),
					"/checkout",
					"POST",
					isErrorSpike,
				),
			);
		}
	}

	// 3. A few background API calls
	for (let i = 0; i < randInt(1, 4); i++) {
		const endpoints = [
			["/api/feed", "GET"],
			["/api/user", "GET"],
			["/api/cart", "POST"],
		];
		const [path, method] = pick(endpoints);
		jitter(randInt(200, 1500));
		events.push(
			makeApiTiming(
				sessionId,
				userId,
				release,
				platform,
				device,
				osVersion,
				new Date(cursor),
				path,
				method,
			),
		);
	}

	// 4. Maybe a custom event
	if (Math.random() < 0.5) {
		jitter(randInt(100, 500));
		events.push(
			makeCustomEvent(
				sessionId,
				userId,
				release,
				platform,
				device,
				osVersion,
				new Date(cursor),
			),
		);
	}

	// 5. Error: v1.1.0 has 30% chance; v1.0.0 has 8%; spike forces one
	const errorProb = isErrorSpike ? 1.0 : release === "v1.1.0" ? 0.3 : 0.08;
	if (Math.random() < errorProb) {
		jitter(randInt(200, 1000));
		const lastScreen =
			events.filter((e) => e.event_type === "screen_view").pop()
				?.screen_name ?? null;
		events.push(
			makeError(
				sessionId,
				userId,
				release,
				platform,
				device,
				osVersion,
				new Date(cursor),
				lastScreen,
			),
		);
	}

	return events;
}

// ── HTTP helpers ───────────────────────────────────────────────────────────────

async function postBatch(events, apiKey) {
	const body = JSON.stringify({ events });
	const res = await fetch(`${BASE_URL}/v1/ingest`, {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			"X-Api-Key": apiKey,
		},
		body,
	});
	if (!res.ok) {
		const text = await res.text().catch(() => "");
		throw new Error(`HTTP ${res.status}: ${text}`);
	}
	return res.json();
}

async function chunk(arr, size, fn) {
	for (let i = 0; i < arr.length; i += size) {
		await fn(arr.slice(i, i + size));
	}
}

// ── Main ───────────────────────────────────────────────────────────────────────

async function main() {
	// Resolve API key
	let apiKey = env.INGEST_API_KEY ?? "";
	if (!apiKey) {
		const rl = createInterface({ input, output });
		apiKey = (
			await rl.question(
				"Paste the ingestion API key printed by the API on startup: ",
			)
		).trim();
		rl.close();
	}
	if (!apiKey) {
		console.error(
			"ERROR: no API key provided. Set INGEST_API_KEY or enter it at the prompt.",
		);
		process.exit(1);
	}

	console.log(`\nTarget: ${BASE_URL}/v1/ingest`);
	console.log(`Sessions: ${SESSION_COUNT} | Batch size: ${BATCH_SIZE}\n`);

	// Spread sessions over the last 48 hours so dashboards show trend data
	const now = Date.now();
	const windowMs = 48 * 60 * 60 * 1000;
	const allEvents = [];

	// Normal sessions (first 85%)
	const normalCount = Math.floor(SESSION_COUNT * 0.85);
	for (let i = 0; i < normalCount; i++) {
		const sessionStart = now - windowMs + Math.random() * windowMs;
		allEvents.push(...generateSession(sessionStart, false));
	}

	// Error spike sessions (last 15% — concentrated in the most recent 2 hours)
	const spikeCount = SESSION_COUNT - normalCount;
	for (let i = 0; i < spikeCount; i++) {
		const sessionStart =
			now - 2 * 60 * 60 * 1000 + Math.random() * 2 * 60 * 60 * 1000;
		allEvents.push(...generateSession(sessionStart, true));
	}

	// Sort chronologically
	allEvents.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

	console.log(
		`Generated ${allEvents.length} total events across ${SESSION_COUNT} sessions.`,
	);

	// Count event types for a quick summary
	const byType = allEvents.reduce((acc, e) => {
		acc[e.event_type] = (acc[e.event_type] ?? 0) + 1;
		return acc;
	}, {});
	for (const [type, count] of Object.entries(byType)) {
		console.log(`  ${type.padEnd(16)} ${count}`);
	}
	console.log("");

	// Send in BATCH_SIZE chunks
	let sent = 0;
	await chunk(allEvents, BATCH_SIZE, async (batch) => {
		try {
			const result = await postBatch(batch, apiKey);
			sent += result.accepted ?? batch.length;
			process.stdout.write(
				`\rPosted ${sent} / ${allEvents.length} events...`,
			);
		} catch (err) {
			console.error(`\nFailed to post batch: ${err.message}`);
			process.exit(1);
		}
	});

	console.log(`\n\nDone! ${sent} events accepted by the API.`);
	console.log("Check Kafka UI → http://localhost:8080");
	console.log(
		"ClickHouse rows should appear within a few seconds after the worker consumes.\n",
	);
}

main().catch((err) => {
	console.error(err);
	process.exit(1);
});

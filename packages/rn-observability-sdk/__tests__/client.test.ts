import { ObservabilityClient } from "../src/client";
import { SdkConfig } from "../src/types";

// ── Helpers ──────────────────────────────────────────────────────────────────

const BASE_CONFIG: SdkConfig = {
	endpoint: "http://localhost:8000",
	apiKey: "mobo_test_key",
	appName: "TestApp",
	appVersion: "1.0.0",
	release: "v1.0.0",
	environment: "test",
	platform: "ios",
	batchSize: 3,
	flushIntervalMs: 100_000, // large — we control flushes manually
};

function makeClient(overrides?: Partial<SdkConfig>): ObservabilityClient {
	return new ObservabilityClient({ ...BASE_CONFIG, ...overrides });
}

function mockFetch(status = 200): jest.Mock {
	const mock = jest.fn().mockResolvedValue({
		status,
		ok: status >= 200 && status < 300,
	} as Response);
	global.fetch = mock;
	return mock;
}

// ── Suite ─────────────────────────────────────────────────────────────────────

describe("ObservabilityClient", () => {
	afterEach(() => {
		jest.restoreAllMocks();
	});

	// ── Batching ──────────────────────────────────────────────────────────────

	describe("batching", () => {
		it("does NOT flush before reaching batchSize", async () => {
			const fetchMock = mockFetch();
			const client = makeClient({ batchSize: 3 });

			client.trackAppStart(150);
			client.trackScreenView("Home");

			expect(fetchMock).not.toHaveBeenCalled();
		});

		it("auto-flushes when queue reaches batchSize", async () => {
			const fetchMock = mockFetch();
			const client = makeClient({ batchSize: 3 });

			// Push exactly batchSize events
			client.trackAppStart(100);
			client.trackScreenView("Home");
			client.trackScreenView("Profile");

			// flush is async — give it one tick
			await Promise.resolve();

			expect(fetchMock).toHaveBeenCalledTimes(1);
		});

		it("flushes every batchSize events across multiple batches", async () => {
			const fetchMock = mockFetch();
			const client = makeClient({ batchSize: 2 });

			for (let i = 0; i < 6; i++) {
				client.trackScreenView(`Screen${i}`);
			}

			await Promise.resolve();

			expect(fetchMock).toHaveBeenCalledTimes(3);
		});
	});

	// ── flush() ───────────────────────────────────────────────────────────────

	describe("flush()", () => {
		it("is a no-op when queue is empty", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			await client.flush();

			expect(fetchMock).not.toHaveBeenCalled();
		});

		it("posts to the correct URL", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackAppStart(200);
			await client.flush();

			expect(fetchMock).toHaveBeenCalledWith(
				"http://localhost:8000/v1/ingest",
				expect.objectContaining({ method: "POST" }),
			);
		});

		it("sends the X-Api-Key header", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackAppStart(200);
			await client.flush();

			const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
			const headers = init.headers as Record<string, string>;
			expect(headers["X-Api-Key"]).toBe("mobo_test_key");
		});

		it("sends all queued events in the body", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackAppStart(100);
			client.trackScreenView("Home");
			await client.flush();

			const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
			const body = JSON.parse(init.body as string);
			expect(body.events).toHaveLength(2);
			expect(body.events[0].event_type).toBe("app_start");
			expect(body.events[1].event_type).toBe("screen_view");
		});

		it("clears the queue after a successful flush", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackAppStart(100);
			await client.flush();
			await client.flush(); // second flush should be no-op

			expect(fetchMock).toHaveBeenCalledTimes(1);
		});

		it("re-queues events when fetch throws (network error)", async () => {
			const fetchMock = jest
				.fn()
				.mockRejectedValue(new Error("Network failure"));
			global.fetch = fetchMock;
			const client = makeClient();

			client.trackScreenView("Home");
			await client.flush(); // fails silently

			// Events should be back in the queue — a second flush fires fetch again
			const recoverMock = mockFetch();
			await client.flush();
			expect(recoverMock).toHaveBeenCalledTimes(1);
		});
	});

	// ── Event shapes ──────────────────────────────────────────────────────────

	describe("event payloads", () => {
		it("trackApiTiming includes path, method, status_code, duration_ms", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackApiTiming("/users", "GET", 200, 42);
			await client.flush();

			const body = JSON.parse(
				(fetchMock.mock.calls[0][1] as RequestInit).body as string,
			);
			const event = body.events[0];
			expect(event.event_type).toBe("api_timing");
			expect(event.path).toBe("/users");
			expect(event.method).toBe("GET");
			expect(event.status_code).toBe(200);
			expect(event.duration_ms).toBe(42);
		});

		it("trackError includes error_class, error_message, fatal", async () => {
			const fetchMock = mockFetch();
			const client = makeClient();

			client.trackError("TypeError", "Cannot read properties of null", {
				screenName: "Profile",
				fatal: true,
			});
			await client.flush();

			const body = JSON.parse(
				(fetchMock.mock.calls[0][1] as RequestInit).body as string,
			);
			const event = body.events[0];
			expect(event.event_type).toBe("error");
			expect(event.error_class).toBe("TypeError");
			expect(event.screen_name).toBe("Profile");
			expect(event.fatal).toBe(true);
		});

		it("each event has a unique event_id", async () => {
			mockFetch();
			const client = makeClient();

			client.trackScreenView("A");
			client.trackScreenView("B");
			await client.flush();

			// Access via the internal queue isn't possible post-flush, so we check
			// via the sent payload
			const body = JSON.parse(
				((global.fetch as jest.Mock).mock.calls[0][1] as RequestInit)
					.body as string,
			);
			expect(body.events[0].event_id).not.toBe(body.events[1].event_id);
		});
	});

	// ── Auto-flush ────────────────────────────────────────────────────────────

	describe("auto-flush", () => {
		beforeEach(() => jest.useFakeTimers());
		afterEach(() => jest.useRealTimers());

		it("flushes on interval when started", async () => {
			const fetchMock = mockFetch();
			const client = makeClient({ flushIntervalMs: 1_000 });

			client.startAutoFlush();
			client.trackScreenView("Home");

			jest.advanceTimersByTime(1_000);
			await Promise.resolve(); // flush is async

			expect(fetchMock).toHaveBeenCalledTimes(1);

			client.stopAutoFlush();
		});

		it("does not flush after stopAutoFlush", async () => {
			const fetchMock = mockFetch();
			const client = makeClient({ flushIntervalMs: 500 });

			client.startAutoFlush();
			client.stopAutoFlush();
			client.trackScreenView("Home");

			jest.advanceTimersByTime(2_000);
			await Promise.resolve();

			expect(fetchMock).not.toHaveBeenCalled();
		});
	});
});

import { ObservabilityClient } from "../src/client";
import { createTrackedFetch } from "../src/trackedFetch";
import { SdkConfig } from "../src/types";

// ── Helpers ──────────────────────────────────────────────────────────────────

const BASE_CONFIG: SdkConfig = {
	endpoint: "http://localhost:8000",
	apiKey: "mobo_test_key",
	appName: "TestApp",
	appVersion: "1.0.0",
	release: "v1.0.0",
	environment: "test",
	platform: "android",
	batchSize: 100, // prevent auto-flush in tests
};

// Capture what the client actually sends to trackApiTiming
function spiedClient(): {
	client: ObservabilityClient;
	calls: Parameters<ObservabilityClient["trackApiTiming"]>[];
} {
	const client = new ObservabilityClient(BASE_CONFIG);
	const calls: Parameters<ObservabilityClient["trackApiTiming"]>[] = [];

	jest.spyOn(client, "trackApiTiming").mockImplementation(
		(...args: Parameters<ObservabilityClient["trackApiTiming"]>) => {
			calls.push(args);
		},
	);

	return { client, calls };
}

// ── Suite ─────────────────────────────────────────────────────────────────────

describe("createTrackedFetch", () => {
	afterEach(() => {
		jest.restoreAllMocks();
	});

	// ── Success path ──────────────────────────────────────────────────────────

	it("returns the original Response on success", async () => {
		const fakeResponse = { status: 200, ok: true } as Response;
		global.fetch = jest.fn().mockResolvedValue(fakeResponse);

		const { client } = spiedClient();
		const http = createTrackedFetch(client);

		const result = await http("https://api.example.com/users");
		expect(result).toBe(fakeResponse);
	});

	it("records api_timing with status 200 and a non-negative duration", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 200, ok: true } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await http("https://api.example.com/items", { method: "GET" });

		expect(calls).toHaveLength(1);
		const [path, method, statusCode, durationMs] = calls[0];
		expect(path).toBe("/items");
		expect(method).toBe("GET");
		expect(statusCode).toBe(200);
		expect(durationMs).toBeGreaterThanOrEqual(0);
		expect(durationMs).toBeLessThan(5_000);
	});

	it("uppercases the HTTP method", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 201, ok: true } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await http("https://api.example.com/orders", { method: "post" });

		expect(calls[0][1]).toBe("POST");
	});

	it("defaults method to GET when init is omitted", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 200, ok: true } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await http("https://api.example.com/ping");

		expect(calls[0][1]).toBe("GET");
	});

	// ── Non-2xx responses ─────────────────────────────────────────────────────

	it("records the actual non-2xx status code (404)", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 404, ok: false } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		const res = await http("https://api.example.com/missing");
		expect(res.status).toBe(404);

		expect(calls[0][2]).toBe(404);
	});

	it("records status 500 without throwing", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 500, ok: false } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await expect(
			http("https://api.example.com/boom"),
		).resolves.toBeDefined();
		expect(calls[0][2]).toBe(500);
	});

	// ── Network error ─────────────────────────────────────────────────────────

	it("records status 0 and re-throws on network failure", async () => {
		global.fetch = jest
			.fn()
			.mockRejectedValue(new TypeError("Failed to fetch"));

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await expect(
			http("https://api.example.com/unreachable"),
		).rejects.toThrow("Failed to fetch");

		expect(calls).toHaveLength(1);
		expect(calls[0][2]).toBe(0); // status_code 0 for network error
		expect(calls[0][4]).toBe("Failed to fetch"); // error_message
	});

	// ── URL path extraction ───────────────────────────────────────────────────

	it("extracts pathname from absolute URL", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 200, ok: true } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await http("https://api.example.com/v2/products?page=1");

		expect(calls[0][0]).toBe("/v2/products");
	});

	it("strips query string from relative URL", async () => {
		global.fetch = jest
			.fn()
			.mockResolvedValue({ status: 200, ok: true } as Response);

		const { client, calls } = spiedClient();
		const http = createTrackedFetch(client);

		await http("/search?q=hello");

		expect(calls[0][0]).toBe("/search");
	});
});

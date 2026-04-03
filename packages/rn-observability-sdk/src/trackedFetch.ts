import { ObservabilityClient } from "./client";

/**
 * Creates a `fetch`-compatible wrapper that automatically records an
 * `api_timing` event for every request made through it.
 *
 * Usage:
 *   const http = createTrackedFetch(client);
 *   const res = await http('https://api.example.com/users');
 */
export function createTrackedFetch(
	client: ObservabilityClient,
): (input: string | URL, init?: RequestInit) => Promise<Response> {
	return async function trackedFetch(
		input: string | URL,
		init?: RequestInit,
	): Promise<Response> {
		const startMs = Date.now();
		const method = (init?.method ?? "GET").toUpperCase();
		const path = extractPath(input);

		try {
			const response = await fetch(input, init);
			const durationMs = Date.now() - startMs;
			client.trackApiTiming(path, method, response.status, durationMs);
			return response;
		} catch (err) {
			const durationMs = Date.now() - startMs;
			const message = err instanceof Error ? err.message : String(err);
			client.trackApiTiming(path, method, 0, durationMs, message);
			throw err;
		}
	};
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function extractPath(input: string | URL): string {
	const raw = typeof input === "string" ? input : input.toString();
	try {
		// Works for absolute URLs
		return new URL(raw).pathname;
	} catch {
		// Relative URL or non-standard string — use as-is
		return raw.split("?")[0];
	}
}

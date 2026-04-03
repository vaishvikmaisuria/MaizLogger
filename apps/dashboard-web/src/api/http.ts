import type {
	ErrorFeedResponse,
	Filters,
	LatencyResponse,
	OverviewResponse,
} from "./types";

/** Base URL — empty string so Vite proxy forwards /v1/* to localhost:8000 in dev;
 *  set VITE_API_BASE in production (e.g. "http://api:8000"). */
const BASE = import.meta.env.VITE_API_BASE ?? "";

function toParams(f: object): string {
	const p = new URLSearchParams();
	for (const [k, v] of Object.entries(f)) {
		if (typeof v === "string" && v !== "") p.set(k, v);
	}
	const s = p.toString();
	return s ? `?${s}` : "";
}

async function get<T>(path: string): Promise<T> {
	const res = await fetch(`${BASE}${path}`);
	if (!res.ok) throw new Error(`API ${path} returned ${res.status}`);
	return res.json() as Promise<T>;
}

export function fetchOverview(filters: Filters): Promise<OverviewResponse> {
	return get<OverviewResponse>(
		`/v1/metrics/overview${toParams({ ...filters })}`,
	);
}

export function fetchErrors(
	filters: Filters & { limit?: string },
): Promise<ErrorFeedResponse> {
	return get<ErrorFeedResponse>(`/v1/errors/feed${toParams({ ...filters })}`);
}

export function fetchLatency(
	filters: Filters & { path?: string; method?: string },
): Promise<LatencyResponse> {
	return get<LatencyResponse>(`/v1/api/latency${toParams({ ...filters })}`);
}

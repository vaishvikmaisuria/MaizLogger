// ── Shared filter shape ───────────────────────────────────────────────────────

export interface Filters {
	app?: string;
	env?: string;
	release?: string;
	from?: string; // ISO-8601
	to?: string; // ISO-8601
}

// ── /v1/metrics/overview ─────────────────────────────────────────────────────

export interface OverviewResponse {
	totalEvents: number;
	uniqueSessions: number;
	totalErrors: number;
	totalApiCalls: number;
	apiErrors: number;
	avgAppStartMs: number;
}

// ── /v1/errors/feed ──────────────────────────────────────────────────────────

export interface ErrorRow {
	eventId: string;
	errorClass: string;
	errorMessage: string;
	release: string;
	platform: string;
	screenName: string | null;
	eventTime: string; // ISO-8601
}

export interface ErrorFeedResponse {
	count: number;
	errors: ErrorRow[];
}

// ── /v1/api/latency ──────────────────────────────────────────────────────────

export interface LatencyBucket {
	path: string;
	method: string;
	release: string;
	bucket: string; // ISO-8601 hour start
	requestCount: number;
	errorCount: number;
	p50Ms: number;
	p95Ms: number;
	p99Ms: number;
}

export interface LatencyResponse {
	buckets: LatencyBucket[];
}

import { SdkConfig, TelemetryEvent } from "./types";

// ── UUID generation ──────────────────────────────────────────────────────────

function generateId(): string {
	// Use crypto.randomUUID when available (Node 14.17+, RN 0.70+)
	if (
		typeof crypto !== "undefined" &&
		typeof crypto.randomUUID === "function"
	) {
		return crypto.randomUUID();
	}

	// Fallback: RFC 4122 v4 from Math.random
	return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
		const r = (Math.random() * 16) | 0;
		const v = c === "x" ? r : (r & 0x3) | 0x8;
		return v.toString(16);
	});
}

// ── ObservabilityClient ──────────────────────────────────────────────────────

const DEFAULT_BATCH_SIZE = 20;
const DEFAULT_FLUSH_INTERVAL_MS = 10_000;
const INGEST_PATH = "/v1/ingest";

export class ObservabilityClient {
	private readonly cfg: Required<SdkConfig>;
	private readonly sessionId: string;
	private queue: TelemetryEvent[] = [];
	private timerId: ReturnType<typeof setInterval> | null = null;

	constructor(config: SdkConfig) {
		this.cfg = {
			batchSize: DEFAULT_BATCH_SIZE,
			flushIntervalMs: DEFAULT_FLUSH_INTERVAL_MS,
			sessionId: generateId(),
			deviceModel: "",
			osVersion: "",
			...config,
		};
		this.sessionId = this.cfg.sessionId;
	}

	// ── Public tracking helpers ────────────────────────────────────────────────

	trackAppStart(durationMs: number): void {
		this.track({
			...this.baseFields("app_start"),
			duration_ms: durationMs,
		});
	}

	trackScreenView(screenName: string, durationMs?: number): void {
		this.track({
			...this.baseFields("screen_view"),
			screen_name: screenName,
			...(durationMs !== undefined ? { duration_ms: durationMs } : {}),
		});
	}

	trackApiTiming(
		path: string,
		method: string,
		statusCode: number,
		durationMs: number,
		errorMessage?: string,
	): void {
		this.track({
			...this.baseFields("api_timing"),
			path,
			method: method.toUpperCase(),
			status_code: statusCode,
			duration_ms: durationMs,
			...(errorMessage ? { error_message: errorMessage } : {}),
		});
	}

	trackError(
		errorClass: string,
		errorMessage: string,
		options?: { screenName?: string; fatal?: boolean },
	): void {
		this.track({
			...this.baseFields("error"),
			error_class: errorClass,
			error_message: errorMessage,
			screen_name: options?.screenName,
			fatal: options?.fatal ?? false,
		});
	}

	trackCustom(
		name: string,
		properties?: Record<string, string | number | boolean>,
	): void {
		this.track({
			...this.baseFields("custom_event"),
			name,
			properties,
		});
	}

	// ── Generic enqueue ────────────────────────────────────────────────────────

	track(event: TelemetryEvent): void {
		this.queue.push(event);
		if (this.queue.length >= this.cfg.batchSize) {
			void this.flush();
		}
	}

	// ── Flush ──────────────────────────────────────────────────────────────────

	async flush(): Promise<void> {
		if (this.queue.length === 0) {
			return;
		}

		const batch = this.queue.splice(0, this.queue.length);

		try {
			await fetch(`${this.cfg.endpoint}${INGEST_PATH}`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Api-Key": this.cfg.apiKey,
				},
				body: JSON.stringify({ events: batch }),
			});
		} catch {
			// On network failure, re-queue the events so we don't lose them
			this.queue.unshift(...batch);
		}
	}

	// ── Auto-flush lifecycle ───────────────────────────────────────────────────

	startAutoFlush(): void {
		if (this.timerId !== null) {
			return;
		}
		this.timerId = setInterval(() => {
			void this.flush();
		}, this.cfg.flushIntervalMs);
	}

	stopAutoFlush(): void {
		if (this.timerId !== null) {
			clearInterval(this.timerId);
			this.timerId = null;
		}
	}

	// ── Internal helpers ───────────────────────────────────────────────────────

	private baseFields<T extends TelemetryEvent["event_type"]>(eventType: T) {
		return {
			event_id: generateId(),
			event_type: eventType,
			session_id: this.sessionId,
			app_name: this.cfg.appName,
			app_version: this.cfg.appVersion,
			release: this.cfg.release,
			environment: this.cfg.environment,
			platform: this.cfg.platform,
			timestamp: new Date().toISOString(),
			...(this.cfg.deviceModel
				? { device_model: this.cfg.deviceModel }
				: {}),
			...(this.cfg.osVersion ? { os_version: this.cfg.osVersion } : {}),
		} as const;
	}
}

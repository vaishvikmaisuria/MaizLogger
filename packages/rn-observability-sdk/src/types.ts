/**
 * Shared telemetry types for the MaizLogger RN SDK.
 *
 * These mirror the server-side TelemetryDtos.java DTO contracts so that the
 * payload posted to /v1/ingest is accepted without validation errors.
 */

// ── Common fields shared by every event ─────────────────────────────────────

export interface BaseEvent {
	event_id: string; // UUID, client-generated
	event_type: EventType; // discriminator
	session_id: string;
	app_name: string;
	app_version: string;
	release: string; // semver tag, e.g. "v1.1.0"
	environment: string; // "dev" | "staging" | "prod"
	platform: "ios" | "android";
	timestamp: string; // ISO-8601 with Z suffix
	// optional
	user_id?: string;
	trace_id?: string;
	device_model?: string;
	os_version?: string;
}

export type EventType =
	| "app_start"
	| "screen_view"
	| "api_timing"
	| "error"
	| "custom_event";

// ── Concrete event shapes ────────────────────────────────────────────────────

export interface AppStartEvent extends BaseEvent {
	event_type: "app_start";
	duration_ms: number; // cold/warm start duration
}

export interface ScreenViewEvent extends BaseEvent {
	event_type: "screen_view";
	screen_name: string;
	duration_ms?: number; // time on screen (filled on leave)
}

export interface ApiTimingEvent extends BaseEvent {
	event_type: "api_timing";
	path: string; // e.g. "/checkout"
	method: string; // "GET" | "POST" | …
	status_code: number;
	duration_ms: number;
	error_message?: string;
}

export interface ErrorEvent extends BaseEvent {
	event_type: "error";
	error_class: string; // exception class name
	error_message: string;
	screen_name?: string;
	fatal: boolean;
}

export interface CustomEvent extends BaseEvent {
	event_type: "custom_event";
	name: string;
	properties?: Record<string, string | number | boolean>;
}

export type TelemetryEvent =
	| AppStartEvent
	| ScreenViewEvent
	| ApiTimingEvent
	| ErrorEvent
	| CustomEvent;

// ── SDK configuration ────────────────────────────────────────────────────────

export interface SdkConfig {
	/** Base URL of the MaizLogger API, e.g. "http://localhost:8000" */
	endpoint: string;
	/** API key printed by the API on startup (mobo_xxxx format) */
	apiKey: string;
	appName: string;
	appVersion: string;
	release: string;
	environment: string;
	platform: "ios" | "android";
	/** Max events to buffer before auto-flush (default 20) */
	batchSize?: number;
	/** Auto-flush interval in ms (default 10_000) */
	flushIntervalMs?: number;
	/** Optional stable session ID — generated if omitted */
	sessionId?: string;
	/** Optional device model string */
	deviceModel?: string;
	/** Optional OS version string */
	osVersion?: string;
}

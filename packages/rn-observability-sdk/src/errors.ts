import { ObservabilityClient } from "./client";

// React Native exposes ErrorUtils as a global for uncaught JS exceptions.
// We reference it via `globalThis` so that:
//   - Tests running in Node do not throw ReferenceError.
//   - Production RN apps get the real handler.
declare const ErrorUtils:
	| {
			setGlobalHandler(
				handler: (error: Error, isFatal?: boolean) => void,
			): void;
	  }
	| undefined;

/**
 * Attach an uncaught-exception handler to React Native's `ErrorUtils`.
 * This is a no-op in plain Node environments (Jest tests) where `ErrorUtils`
 * is not defined, so tests remain clean without mocking.
 *
 * @param client       Shared SDK client instance.
 * @param getCurrentScreen  Optional callback returning the active screen name.
 */
export function registerErrorHandlers(
	client: ObservabilityClient,
	getCurrentScreen?: () => string | null,
): void {
	// Guard: not available in plain Node / older RN versions
	const rnErrorUtils =
		typeof ErrorUtils !== "undefined" ? ErrorUtils : undefined;

	if (!rnErrorUtils) {
		return;
	}

	rnErrorUtils.setGlobalHandler((error: Error, isFatal = false) => {
		client.trackError(error.name || "Error", error.message, {
			screenName: getCurrentScreen?.() ?? undefined,
			fatal: isFatal,
		});

		if (isFatal) {
			// Give flush a best-effort chance before the process dies.
			// We cannot await here because RN may terminate immediately.
			void client.flush();
		}
	});
}

/**
 * Convenience helper for manually tracking caught exceptions.
 *
 * Usage:
 *   try { ... }
 *   catch (e) { trackHandledError(client, e, 'CheckoutScreen'); }
 */
export function trackHandledError(
	client: ObservabilityClient,
	error: unknown,
	screenName?: string,
): void {
	const err = error instanceof Error ? error : new Error(String(error));
	client.trackError(err.name || "Error", err.message, {
		screenName,
		fatal: false,
	});
}

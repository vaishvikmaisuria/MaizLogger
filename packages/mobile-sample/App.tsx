/**
 * Mobile Sample — App.tsx
 *
 * This file demonstrates how to wire the @mobobs/rn-observability-sdk into a
 * React Native application.  It is intentionally kept simple so that the SDK
 * integration pattern is easy to copy into a real project.
 *
 * Prerequisites (in your RN project):
 *   npm install @mobobs/rn-observability-sdk
 *   # or, if consuming from the monorepo locally:
 *   # "dependencies": { "@mobobs/rn-observability-sdk": "*" }  + workspaces
 *
 * The file follows typical React Native conventions but does NOT assume any
 * navigation library.  Comments explain how to adapt it for React Navigation.
 */

import React, { useEffect, useRef } from "react";
import {
	Button,
	SafeAreaView,
	ScrollView,
	StyleSheet,
	Text,
	View,
} from "react-native";

import {
	ObservabilityClient,
	createTrackedFetch,
	registerErrorHandlers,
	trackHandledError,
} from "@mobobs/rn-observability-sdk";

// ── 1. Initialise the SDK once (module-level singleton) ───────────────────────

const client = new ObservabilityClient({
	endpoint: "http://localhost:8000", // Change to your API host
	apiKey: "mobo_demo_key",
	appName: "MobileSample",
	appVersion: "1.0.0",
	release: "v1.0.0",
	environment: "dev",
	platform: "ios", // or 'android' — use Platform.OS in a real app
	batchSize: 10,
	flushIntervalMs: 15_000,
});

// ── 2. Register a crash handler (uncaught JS errors in React Native) ──────────

// Pass an optional callback that returns the currently visible screen name.
// With React Navigation you would do:
//   navigationRef.getCurrentRoute()?.name ?? null
registerErrorHandlers(client, () => null);

// ── 3. Create a tracked fetch wrapper ────────────────────────────────────────

/**
 * Use `http` everywhere instead of the bare `fetch`.
 * Every request automatically emits an `api_timing` event.
 */
const http = createTrackedFetch(client);

// ── 4. Track application startup time ────────────────────────────────────────

// Record module-load time as a rough cold-start marker
const MODULE_LOAD_TIME = Date.now();

// ── 5. Root component ─────────────────────────────────────────────────────────

export default function App(): React.JSX.Element {
	const mountTime = useRef<number>(Date.now());

	useEffect(() => {
		// ── App start event ──────────────────────────────────────────────────────
		const coldStartMs = Date.now() - MODULE_LOAD_TIME;
		client.trackAppStart(coldStartMs);

		// ── Start auto-flush so events drain periodically ────────────────────────
		client.startAutoFlush();

		// ── Screen view tracking ─────────────────────────────────────────────────
		// With React Navigation, replace this with a NavigationContainer onReady /
		// onStateChange callback.  For a single-screen sample we track it here.
		client.trackScreenView("Home");

		return () => {
			// Flush remaining events and tear down the timer on unmount
			void client.flush();
			client.stopAutoFlush();
		};
	}, []);

	// ── Screen view on re-focus ──────────────────────────────────────────────
	// In React Navigation you would use useFocusEffect from
	// @react-navigation/native instead of this useEffect trick:
	//
	//   useFocusEffect(
	//     React.useCallback(() => {
	//       client.trackScreenView('Home');
	//     }, []),
	//   );

	// ── Handlers ──────────────────────────────────────────────────────────────

	async function handleFetchUsers(): Promise<void> {
		try {
			// `http` behaves exactly like `fetch` but records timing automatically
			const response = await http(
				"https://jsonplaceholder.typicode.com/users",
			);
			const data = await response.json();
			console.log("Fetched", (data as unknown[]).length, "users");
		} catch (err) {
			// Manually track a caught (handled) error
			trackHandledError(client, err, "Home");
		}
	}

	async function handleFetchFailure(): Promise<void> {
		try {
			// Intentionally broken URL — will produce a network-error api_timing event
			await http("https://non.existent.host.example.com/data");
		} catch (err) {
			trackHandledError(client, err, "Home");
		}
	}

	function handleCustomEvent(): void {
		client.trackCustom("button_pressed", {
			button_id: "demo_custom",
			screen: "Home",
		});
	}

	// ── Render ─────────────────────────────────────────────────────────────────

	return (
		<SafeAreaView style={styles.container}>
			<ScrollView contentInsetAdjustmentBehavior="automatic">
				<View style={styles.header}>
					<Text style={styles.title}>MaizLogger — Sample App</Text>
					<Text style={styles.subtitle}>
						SDK demo: tap each button then check the API for
						telemetry events.
					</Text>
				</View>

				<View style={styles.section}>
					<Text style={styles.sectionTitle}>API Timing</Text>
					<Button
						title="Fetch Users (success)"
						onPress={() => void handleFetchUsers()}
					/>
					<View style={styles.spacer} />
					<Button
						title="Fetch Users (network error)"
						onPress={() => void handleFetchFailure()}
					/>
				</View>

				<View style={styles.section}>
					<Text style={styles.sectionTitle}>Custom Event</Text>
					<Button
						title="Track Custom Event"
						onPress={handleCustomEvent}
					/>
				</View>

				<View style={styles.section}>
					<Text style={styles.sectionTitle}>Manual Flush</Text>
					<Button
						title="Flush Buffer Now"
						onPress={() => void client.flush()}
					/>
				</View>
			</ScrollView>
		</SafeAreaView>
	);
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: "#f5f5f5",
	},
	header: {
		padding: 24,
		backgroundColor: "#1a56db",
	},
	title: {
		fontSize: 22,
		fontWeight: "700",
		color: "#fff",
	},
	subtitle: {
		marginTop: 6,
		fontSize: 13,
		color: "#c7d2fe",
	},
	section: {
		margin: 16,
		padding: 16,
		backgroundColor: "#fff",
		borderRadius: 12,
		shadowColor: "#000",
		shadowOpacity: 0.06,
		shadowRadius: 4,
		elevation: 2,
	},
	sectionTitle: {
		fontSize: 15,
		fontWeight: "600",
		color: "#111",
		marginBottom: 12,
	},
	spacer: {
		height: 8,
	},
});

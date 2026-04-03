import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as http from "../../api/http";
import type { ErrorFeedResponse } from "../../api/types";
import Errors from "../Errors";

const mockFeed: ErrorFeedResponse = {
	count: 2,
	errors: [
		{
			eventId: "evt-1",
			errorClass: "NullPointerException",
			errorMessage: "Cannot read property of null",
			release: "v1.1.0",
			platform: "ios",
			screenName: "HomeScreen",
			eventTime: "2025-06-01T12:00:00Z",
		},
		{
			eventId: "evt-2",
			errorClass: "IOException",
			errorMessage: "Broken pipe",
			release: "v1.0.0",
			platform: "android",
			screenName: null,
			eventTime: "2025-06-01T11:30:00Z",
		},
	],
};

describe("Errors page", () => {
	afterEach(() => vi.restoreAllMocks());

	it("renders error rows after data loads", async () => {
		vi.spyOn(http, "fetchErrors").mockResolvedValue(mockFeed);

		render(
			<MemoryRouter>
				<Errors />
			</MemoryRouter>,
		);

		expect(
			await screen.findByText("NullPointerException"),
		).toBeInTheDocument();
		expect(await screen.findByText("IOException")).toBeInTheDocument();
		expect(
			await screen.findByText("Cannot read property of null"),
		).toBeInTheDocument();
		expect(await screen.findByText("v1.1.0")).toBeInTheDocument();
		// Platform badges
		expect(await screen.findByText("ios")).toBeInTheDocument();
		expect(await screen.findByText("android")).toBeInTheDocument();
		// null screenName renders as dash
		expect(await screen.findByText("—")).toBeInTheDocument();
	});

	it("shows empty state when no errors", async () => {
		vi.spyOn(http, "fetchErrors").mockResolvedValue({
			count: 0,
			errors: [],
		});

		render(
			<MemoryRouter>
				<Errors />
			</MemoryRouter>,
		);

		expect(
			await screen.findByText(/no errors in the selected window/i),
		).toBeInTheDocument();
	});

	it("shows error message when fetch fails", async () => {
		vi.spyOn(http, "fetchErrors").mockRejectedValue(new Error("timeout"));

		render(
			<MemoryRouter>
				<Errors />
			</MemoryRouter>,
		);

		expect(await screen.findByText(/timeout/i)).toBeInTheDocument();
	});
});

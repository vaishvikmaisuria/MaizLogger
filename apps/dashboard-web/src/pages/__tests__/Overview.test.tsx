import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as http from "../../api/http";
import type { OverviewResponse } from "../../api/types";
import Overview from "../Overview";

const mockOverview: OverviewResponse = {
	totalEvents: 12345,
	uniqueSessions: 200,
	totalErrors: 15,
	totalApiCalls: 500,
	apiErrors: 20,
	avgAppStartMs: 840.5,
};

describe("Overview page", () => {
	afterEach(() => vi.restoreAllMocks());

	it("renders stat cards after data loads", async () => {
		vi.spyOn(http, "fetchOverview").mockResolvedValue(mockOverview);

		render(
			<MemoryRouter>
				<Overview />
			</MemoryRouter>,
		);

		// Heading always visible
		expect(screen.getByText("Overview")).toBeInTheDocument();

		// Wait for async data
		expect(await screen.findByText("12.3K")).toBeInTheDocument(); // totalEvents
		expect(await screen.findByText("200")).toBeInTheDocument(); // uniqueSessions
		expect(await screen.findByText("15")).toBeInTheDocument(); // totalErrors
		expect(await screen.findByText("841 ms")).toBeInTheDocument(); // avgAppStartMs rounded
	});

	it("shows error message when fetch fails", async () => {
		vi.spyOn(http, "fetchOverview").mockRejectedValue(
			new Error("network failure"),
		);

		render(
			<MemoryRouter>
				<Overview />
			</MemoryRouter>,
		);

		expect(await screen.findByText(/network failure/i)).toBeInTheDocument();
	});

	it("shows loading state initially", () => {
		// Never resolves within the test
		vi.spyOn(http, "fetchOverview").mockReturnValue(new Promise(() => {}));

		render(
			<MemoryRouter>
				<Overview />
			</MemoryRouter>,
		);

		expect(screen.getByText("Loading…")).toBeInTheDocument();
	});
});

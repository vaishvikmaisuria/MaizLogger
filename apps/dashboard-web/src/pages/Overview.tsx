import { useEffect, useState } from "react";
import { fetchOverview } from "../api/http";
import type { Filters, OverviewResponse } from "../api/types";
import FilterBar from "../components/FilterBar";
import StatCard from "../components/StatCard";

const grid: React.CSSProperties = {
	display: "grid",
	gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
	gap: 16,
	marginBottom: 32,
};

function fmt(n: number, decimals = 0): string {
	if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
	if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
	return n.toFixed(decimals);
}

export default function Overview() {
	const [filters, setFilters] = useState<Filters>({});
	const [data, setData] = useState<OverviewResponse | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		setLoading(true);
		setError(null);
		fetchOverview(filters)
			.then(setData)
			.catch((e: Error) => setError(e.message))
			.finally(() => setLoading(false));
	}, [filters]);

	const errorRate =
		data && data.totalApiCalls > 0
			? ((data.apiErrors / data.totalApiCalls) * 100).toFixed(1)
			: "—";

	return (
		<section>
			<h1 style={{ marginBottom: 20, fontSize: 22, fontWeight: 700 }}>
				Overview
			</h1>
			<FilterBar filters={filters} onChange={setFilters} />

			{loading && <p style={{ color: "#8b949e" }}>Loading…</p>}
			{error && (
				<p style={{ color: "#f85149", marginBottom: 16 }}>
					Failed to load: {error}
				</p>
			)}

			{data && (
				<div style={grid}>
					<StatCard
						label="Total Events"
						value={fmt(data.totalEvents)}
					/>
					<StatCard
						label="Unique Sessions"
						value={fmt(data.uniqueSessions)}
						accent="#58a6ff"
					/>
					<StatCard
						label="Total Errors"
						value={fmt(data.totalErrors)}
						accent={data.totalErrors > 0 ? "#f85149" : "#3fb950"}
					/>
					<StatCard
						label="API Calls"
						value={fmt(data.totalApiCalls)}
					/>
					<StatCard
						label="API Errors"
						value={fmt(data.apiErrors)}
						sub={`${errorRate}% error rate`}
						accent={data.apiErrors > 0 ? "#f85149" : "#3fb950"}
					/>
					<StatCard
						label="Avg App Start"
						value={
							data.avgAppStartMs > 0
								? `${fmt(data.avgAppStartMs, 0)} ms`
								: "—"
						}
						accent="#d2a8ff"
					/>
				</div>
			)}
		</section>
	);
}

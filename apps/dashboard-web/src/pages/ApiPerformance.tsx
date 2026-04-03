import { useEffect, useState } from "react";
import {
	CartesianGrid,
	Legend,
	Line,
	LineChart,
	ResponsiveContainer,
	Tooltip,
	XAxis,
	YAxis,
} from "recharts";
import { fetchLatency } from "../api/http";
import type { Filters, LatencyBucket } from "../api/types";
import FilterBar from "../components/FilterBar";

const inp: React.CSSProperties = {
	padding: "6px 10px",
	background: "#161b22",
	border: "1px solid #30363d",
	borderRadius: 6,
	color: "#e2e8f0",
	fontSize: 13,
};

/** Group buckets by path+method so we can render one chart per endpoint. */
function groupByEndpoint(
	buckets: LatencyBucket[],
): Map<string, LatencyBucket[]> {
	const map = new Map<string, LatencyBucket[]>();
	for (const b of buckets) {
		const key = `${b.method} ${b.path}`;
		const arr = map.get(key) ?? [];
		arr.push(b);
		map.set(key, arr);
	}
	return map;
}

function formatBucket(iso: string): string {
	try {
		return new Date(iso).toLocaleTimeString([], {
			hour: "2-digit",
			minute: "2-digit",
		});
	} catch {
		return iso;
	}
}

const CHART_COLORS = { p50: "#58a6ff", p95: "#d2a8ff", p99: "#f85149" };

export default function ApiPerformance() {
	const [filters, setFilters] = useState<Filters>({});
	const [pathFilter, setPathFilter] = useState("");
	const [methodFilter, setMethodFilter] = useState("");
	const [buckets, setBuckets] = useState<LatencyBucket[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		setLoading(true);
		setError(null);
		fetchLatency({
			...filters,
			path: pathFilter || undefined,
			method: methodFilter || undefined,
		})
			.then((r) => setBuckets(r.buckets))
			.catch((e: Error) => setError(e.message))
			.finally(() => setLoading(false));
	}, [filters, pathFilter, methodFilter]);

	const pathInput = (
		<input
			style={inp}
			placeholder="path (e.g. /checkout)"
			value={pathFilter}
			onChange={(e) => setPathFilter(e.target.value)}
		/>
	);

	const methodInput = (
		<input
			style={{ ...inp, width: 80 }}
			placeholder="method"
			value={methodFilter}
			onChange={(e) => setMethodFilter(e.target.value)}
		/>
	);

	const grouped = groupByEndpoint(buckets);

	return (
		<section>
			<h1 style={{ marginBottom: 20, fontSize: 22, fontWeight: 700 }}>
				API Performance
			</h1>
			<FilterBar
				filters={filters}
				onChange={setFilters}
				extras={
					<>
						{pathInput}
						{methodInput}
					</>
				}
			/>

			{loading && <p style={{ color: "#8b949e" }}>Loading…</p>}
			{error && (
				<p style={{ color: "#f85149", marginBottom: 16 }}>
					Failed to load: {error}
				</p>
			)}

			{!loading && !error && buckets.length === 0 && (
				<p style={{ color: "#8b949e" }}>
					No API timing data in the selected window.
				</p>
			)}

			{[...grouped.entries()].map(([endpoint, data]) => {
				const chartData = data
					.sort((a, b) => a.bucket.localeCompare(b.bucket))
					.map((b) => ({
						bucket: formatBucket(b.bucket),
						p50: Math.round(b.p50Ms),
						p95: Math.round(b.p95Ms),
						p99: Math.round(b.p99Ms),
						errors: b.errorCount,
						requests: b.requestCount,
					}));

				const totalRequests = data.reduce(
					(s, b) => s + b.requestCount,
					0,
				);
				const totalErrors = data.reduce((s, b) => s + b.errorCount, 0);
				const errorRate =
					totalRequests > 0
						? ((totalErrors / totalRequests) * 100).toFixed(1)
						: "—";

				return (
					<div
						key={endpoint}
						style={{
							background: "#161b22",
							border: "1px solid #30363d",
							borderRadius: 10,
							padding: 20,
							marginBottom: 24,
						}}
					>
						<div
							style={{
								display: "flex",
								justifyContent: "space-between",
								marginBottom: 12,
							}}
						>
							<h2
								style={{
									fontSize: 15,
									fontWeight: 600,
									fontFamily: "monospace",
									color: "#79c0ff",
								}}
							>
								{endpoint}
							</h2>
							<span style={{ fontSize: 13, color: "#8b949e" }}>
								{totalRequests.toLocaleString()} requests ·{" "}
								{errorRate}% error rate
							</span>
						</div>

						<ResponsiveContainer width="100%" height={220}>
							<LineChart
								data={chartData}
								margin={{
									top: 4,
									right: 16,
									bottom: 4,
									left: 0,
								}}
							>
								<CartesianGrid
									strokeDasharray="3 3"
									stroke="#21262d"
								/>
								<XAxis
									dataKey="bucket"
									tick={{ fill: "#8b949e", fontSize: 11 }}
								/>
								<YAxis
									tick={{ fill: "#8b949e", fontSize: 11 }}
									tickFormatter={(v: number) => `${v}ms`}
								/>
								<Tooltip
									contentStyle={{
										background: "#161b22",
										border: "1px solid #30363d",
										borderRadius: 6,
									}}
									formatter={(v: number, name: string) => [
										`${v} ms`,
										name.toUpperCase(),
									]}
								/>
								<Legend wrapperStyle={{ fontSize: 12 }} />
								<Line
									type="monotone"
									dataKey="p50"
									stroke={CHART_COLORS.p50}
									dot={false}
									strokeWidth={2}
								/>
								<Line
									type="monotone"
									dataKey="p95"
									stroke={CHART_COLORS.p95}
									dot={false}
									strokeWidth={2}
								/>
								<Line
									type="monotone"
									dataKey="p99"
									stroke={CHART_COLORS.p99}
									dot={false}
									strokeWidth={2}
								/>
							</LineChart>
						</ResponsiveContainer>
					</div>
				);
			})}
		</section>
	);
}

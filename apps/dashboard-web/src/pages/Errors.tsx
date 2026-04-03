import { useEffect, useState } from "react";
import { fetchErrors } from "../api/http";
import type { ErrorRow, Filters } from "../api/types";
import FilterBar from "../components/FilterBar";

const table: React.CSSProperties = {
	width: "100%",
	borderCollapse: "collapse",
	fontSize: 13,
};

const th: React.CSSProperties = {
	textAlign: "left",
	padding: "8px 12px",
	borderBottom: "1px solid #30363d",
	color: "#8b949e",
	fontWeight: 500,
	whiteSpace: "nowrap",
};

const td: React.CSSProperties = {
	padding: "8px 12px",
	borderBottom: "1px solid #21262d",
	verticalAlign: "top",
};

const badge = (platform: string): React.CSSProperties => ({
	display: "inline-block",
	padding: "2px 6px",
	borderRadius: 4,
	fontSize: 11,
	fontWeight: 600,
	background: platform === "ios" ? "#1f3a5f" : "#1a3320",
	color: platform === "ios" ? "#79c0ff" : "#56d364",
});

function formatTime(iso: string): string {
	try {
		return new Date(iso).toLocaleString();
	} catch {
		return iso;
	}
}

export default function Errors() {
	const [filters, setFilters] = useState<Filters>({});
	const [limit, setLimit] = useState("50");
	const [rows, setRows] = useState<ErrorRow[]>([]);
	const [count, setCount] = useState(0);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		setLoading(true);
		setError(null);
		fetchErrors({ ...filters, limit })
			.then((r) => {
				setRows(r.errors);
				setCount(r.count);
			})
			.catch((e: Error) => setError(e.message))
			.finally(() => setLoading(false));
	}, [filters, limit]);

	const limitInput = (
		<select
			value={limit}
			onChange={(e) => setLimit(e.target.value)}
			style={{
				padding: "6px 10px",
				background: "#161b22",
				border: "1px solid #30363d",
				borderRadius: 6,
				color: "#e2e8f0",
				fontSize: 13,
			}}
		>
			{["20", "50", "100", "200"].map((v) => (
				<option key={v} value={v}>
					{v} rows
				</option>
			))}
		</select>
	);

	return (
		<section>
			<h1 style={{ marginBottom: 20, fontSize: 22, fontWeight: 700 }}>
				Errors{" "}
				{!loading && (
					<span
						style={{
							fontSize: 14,
							color: "#8b949e",
							fontWeight: 400,
						}}
					>
						({count} total)
					</span>
				)}
			</h1>
			<FilterBar
				filters={filters}
				onChange={setFilters}
				extras={limitInput}
			/>

			{loading && <p style={{ color: "#8b949e" }}>Loading…</p>}
			{error && (
				<p style={{ color: "#f85149", marginBottom: 16 }}>
					Failed to load: {error}
				</p>
			)}

			{!loading && !error && rows.length === 0 && (
				<p style={{ color: "#8b949e" }}>
					No errors in the selected window.
				</p>
			)}

			{rows.length > 0 && (
				<div style={{ overflowX: "auto" }}>
					<table style={table}>
						<thead>
							<tr>
								<th style={th}>Time</th>
								<th style={th}>Class</th>
								<th style={th}>Message</th>
								<th style={th}>Release</th>
								<th style={th}>Platform</th>
								<th style={th}>Screen</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((r) => (
								<tr key={r.eventId}>
									<td
										style={{
											...td,
											color: "#8b949e",
											whiteSpace: "nowrap",
										}}
									>
										{formatTime(r.eventTime)}
									</td>
									<td
										style={{
											...td,
											color: "#f85149",
											fontFamily: "monospace",
										}}
									>
										{r.errorClass}
									</td>
									<td
										style={{
											...td,
											maxWidth: 340,
											wordBreak: "break-word",
										}}
									>
										{r.errorMessage}
									</td>
									<td style={{ ...td, color: "#d2a8ff" }}>
										{r.release}
									</td>
									<td style={td}>
										<span style={badge(r.platform)}>
											{r.platform}
										</span>
									</td>
									<td style={{ ...td, color: "#8b949e" }}>
										{r.screenName ?? "—"}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</section>
	);
}

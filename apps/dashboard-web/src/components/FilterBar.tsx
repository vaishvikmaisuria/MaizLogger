import type { Filters } from "../api/types";

interface Props {
	filters: Filters;
	onChange: (f: Filters) => void;
	extras?: React.ReactNode;
}

const row: React.CSSProperties = {
	display: "flex",
	gap: 10,
	flexWrap: "wrap",
	marginBottom: 24,
	alignItems: "center",
};

const inp: React.CSSProperties = {
	padding: "6px 10px",
	background: "#161b22",
	border: "1px solid #30363d",
	borderRadius: 6,
	color: "#e2e8f0",
	fontSize: 13,
};

/** 24-hour sliding windows available in the quick picker */
const WINDOWS = [
	{ label: "1 h", hours: 1 },
	{ label: "6 h", hours: 6 },
	{ label: "24 h", hours: 24 },
	{ label: "7 d", hours: 168 },
];

function hoursAgo(h: number): string {
	return new Date(Date.now() - h * 3_600_000).toISOString();
}

export default function FilterBar({ filters, onChange, extras }: Props) {
	const set =
		(k: keyof Filters) => (e: React.ChangeEvent<HTMLInputElement>) =>
			onChange({ ...filters, [k]: e.target.value || undefined });

	return (
		<div style={row}>
			<input
				style={inp}
				placeholder="app"
				value={filters.app ?? ""}
				onChange={set("app")}
			/>
			<input
				style={inp}
				placeholder="env"
				value={filters.env ?? ""}
				onChange={set("env")}
			/>
			<input
				style={inp}
				placeholder="release"
				value={filters.release ?? ""}
				onChange={set("release")}
			/>
			<span style={{ fontSize: 13, color: "#8b949e" }}>Window:</span>
			{WINDOWS.map(({ label, hours }) => (
				<button
					key={label}
					onClick={() =>
						onChange({
							...filters,
							from: hoursAgo(hours),
							to: new Date().toISOString(),
						})
					}
					style={{
						...inp,
						cursor: "pointer",
						background:
							filters.from === hoursAgo(hours)
								? "#1f6feb"
								: "#161b22",
					}}
				>
					{label}
				</button>
			))}
			{extras}
		</div>
	);
}

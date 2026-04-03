interface Props {
	label: string;
	value: string | number;
	sub?: string;
	accent?: string; // CSS colour for the value text
}

const card: React.CSSProperties = {
	background: "#161b22",
	border: "1px solid #30363d",
	borderRadius: 10,
	padding: "20px 24px",
	display: "flex",
	flexDirection: "column",
	gap: 4,
};

export default function StatCard({
	label,
	value,
	sub,
	accent = "#e2e8f0",
}: Props) {
	return (
		<div style={card}>
			<span
				style={{
					fontSize: 12,
					color: "#8b949e",
					textTransform: "uppercase",
					letterSpacing: 1,
				}}
			>
				{label}
			</span>
			<span
				style={{
					fontSize: 32,
					fontWeight: 700,
					color: accent,
					lineHeight: 1.1,
				}}
			>
				{value}
			</span>
			{sub && (
				<span style={{ fontSize: 12, color: "#8b949e" }}>{sub}</span>
			)}
		</div>
	);
}

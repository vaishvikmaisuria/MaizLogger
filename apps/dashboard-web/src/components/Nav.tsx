import { NavLink } from "react-router-dom";

const links = [
	{ to: "/overview", label: "Overview" },
	{ to: "/errors", label: "Errors" },
	{ to: "/api-performance", label: "API Performance" },
];

const navStyle: React.CSSProperties = {
	display: "flex",
	alignItems: "center",
	gap: 8,
	padding: "12px 32px",
	background: "#161b22",
	borderBottom: "1px solid #30363d",
};

const brandStyle: React.CSSProperties = {
	fontWeight: 700,
	fontSize: 18,
	color: "#58a6ff",
	marginRight: 24,
	textDecoration: "none",
};

export default function Nav() {
	return (
		<nav style={navStyle}>
			<a href="/overview" style={brandStyle}>
				MaizLogger
			</a>
			{links.map(({ to, label }) => (
				<NavLink
					key={to}
					to={to}
					style={({ isActive }) => ({
						color: isActive ? "#58a6ff" : "#8b949e",
						textDecoration: "none",
						fontWeight: isActive ? 600 : 400,
						padding: "6px 12px",
						borderRadius: 6,
						background: isActive
							? "rgba(88,166,255,0.1)"
							: "transparent",
						fontSize: 14,
					})}
				>
					{label}
				</NavLink>
			))}
		</nav>
	);
}

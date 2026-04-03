import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Nav from "./components/Nav";
import Overview from "./pages/Overview";
import Errors from "./pages/Errors";
import ApiPerformance from "./pages/ApiPerformance";

export default function App() {
	return (
		<BrowserRouter>
			<Nav />
			<main
				style={{
					padding: "24px 32px",
					maxWidth: 1280,
					margin: "0 auto",
				}}
			>
				<Routes>
					<Route
						path="/"
						element={<Navigate to="/overview" replace />}
					/>
					<Route path="/overview" element={<Overview />} />
					<Route path="/errors" element={<Errors />} />
					<Route
						path="/api-performance"
						element={<ApiPerformance />}
					/>
				</Routes>
			</main>
		</BrowserRouter>
	);
}

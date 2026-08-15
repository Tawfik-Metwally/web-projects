import { useEffect, useState } from "react";

interface HealthResponse {
  status: "ok";
  database: "reachable";
}

type ConnectionState = "checking" | "ready" | "unavailable";

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";

export function App() {
  const [connection, setConnection] = useState<ConnectionState>("checking");

  useEffect(() => {
    const controller = new AbortController();

    async function checkHealth() {
      try {
        const response = await fetch(`${apiUrl}/health`, { signal: controller.signal });

        if (!response.ok) {
          throw new Error(`Health check failed with status ${response.status}`);
        }

        const health = (await response.json()) as HealthResponse;
        setConnection(health.status === "ok" ? "ready" : "unavailable");
      } catch (error) {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setConnection("unavailable");
        }
      }
    }

    void checkHealth();
    return () => controller.abort();
  }, []);

  return (
    <main className="shell">
      <p className="eyebrow">Equipment operations</p>
      <h1>BenchLedger</h1>
      <p className="intro">
        A clear, auditable record of laboratory equipment, calibration deadlines, and maintenance history.
      </p>
      <section className="status-card" aria-live="polite">
        <span className={`status-dot status-dot--${connection}`} aria-hidden="true" />
        <div>
          <strong>{connection === "ready" ? "System ready" : connection === "checking" ? "Checking system" : "System unavailable"}</strong>
          <p>{connection === "ready" ? "The API and database are connected." : "Waiting for the application services."}</p>
        </div>
      </section>
    </main>
  );
}

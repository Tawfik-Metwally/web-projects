import { type FormEvent, useEffect, useState } from "react";

import { createEquipment, listEquipment, type Equipment } from "./equipment.js";

interface HealthResponse {
  status: "ok";
  database: "reachable";
}

type ConnectionState = "checking" | "ready" | "unavailable";

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";

export function App() {
  const [connection, setConnection] = useState<ConnectionState>("checking");
  const [equipment, setEquipment] = useState<Equipment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    async function checkHealth() {
      try {
        const [response, equipmentList] = await Promise.all([
          fetch(`${apiUrl}/health`, { signal: controller.signal }),
          listEquipment(controller.signal),
        ]);

        if (!response.ok) {
          throw new Error(`Health check failed with status ${response.status}`);
        }

        const health = (await response.json()) as HealthResponse;
        setConnection(health.status === "ok" ? "ready" : "unavailable");
        setEquipment(equipmentList);
      } catch (error) {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setConnection("unavailable");
          setMessage("Could not load the equipment catalog.");
        }
      } finally {
        setIsLoading(false);
      }
    }

    void checkHealth();
    return () => controller.abort();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setMessage(null);

    const form = event.currentTarget;
    const data = new FormData(form);
    const intervalValue = String(data.get("calibrationIntervalDays") ?? "").trim();

    try {
      const created = await createEquipment({
        assetTag: String(data.get("assetTag") ?? ""),
        name: String(data.get("name") ?? ""),
        category: String(data.get("category") ?? ""),
        manufacturer: String(data.get("manufacturer") ?? "") || undefined,
        model: String(data.get("model") ?? "") || undefined,
        serialNumber: String(data.get("serialNumber") ?? "") || undefined,
        location: String(data.get("location") ?? ""),
        calibrationIntervalDays: intervalValue ? Number(intervalValue) : undefined,
      });

      setEquipment((current) => [created, ...current]);
      setMessage(`${created.name} was added to the ledger.`);
      form.reset();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not save the equipment.");
    } finally {
      setIsSaving(false);
    }
  }

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

      <div className="workspace-grid">
        <section className="panel" aria-labelledby="add-equipment-title">
          <p className="section-kicker">New record</p>
          <h2 id="add-equipment-title">Add equipment</h2>
          <form onSubmit={handleSubmit}>
            <div className="field-row">
              <label>
                Asset tag
                <input name="assetTag" maxLength={50} required placeholder="OSC-001" />
              </label>
              <label>
                Name
                <input name="name" maxLength={120} required placeholder="Digital oscilloscope" />
              </label>
            </div>
            <div className="field-row">
              <label>
                Category
                <input name="category" maxLength={80} required placeholder="Measurement" />
              </label>
              <label>
                Location
                <input name="location" maxLength={100} required placeholder="Electronics bench" />
              </label>
            </div>
            <details>
              <summary>Optional technical details</summary>
              <div className="field-row details-grid">
                <label>
                  Manufacturer
                  <input name="manufacturer" maxLength={100} />
                </label>
                <label>
                  Model
                  <input name="model" maxLength={100} />
                </label>
                <label>
                  Serial number
                  <input name="serialNumber" maxLength={100} />
                </label>
                <label>
                  Calibration interval (days)
                  <input name="calibrationIntervalDays" type="number" min={1} step={1} />
                </label>
              </div>
            </details>
            <button type="submit" disabled={isSaving || connection !== "ready"}>
              {isSaving ? "Saving…" : "Add to ledger"}
            </button>
          </form>
          {message ? <p className="form-message" role="status">{message}</p> : null}
        </section>

        <section className="panel" aria-labelledby="catalog-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Current inventory</p>
              <h2 id="catalog-title">Equipment catalog</h2>
            </div>
            <span className="count-badge">{equipment.length}</span>
          </div>
          {isLoading ? <p className="empty-state">Loading equipment…</p> : null}
          {!isLoading && equipment.length === 0 ? (
            <p className="empty-state">No equipment yet. Add the first record using the form.</p>
          ) : null}
          <ul className="equipment-list">
            {equipment.map((item) => (
              <li key={item.id}>
                <div>
                  <span className="asset-tag">{item.assetTag}</span>
                  <h3>{item.name}</h3>
                  <p>{item.category} · {item.location}</p>
                </div>
                <span className="interval">
                  {item.calibrationIntervalDays ? `${item.calibrationIntervalDays} day cycle` : "No calibration cycle"}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </main>
  );
}

import { useRef, useState } from "react";
import { tableFromJSON } from "apache-arrow";
import type { Table } from "apache-arrow";
import { ARROW_STREAM, fetchArrow } from "columnwire";
import { ArrowGrid } from "columnwire/react";

type Format = "json" | "arrow";

interface Run {
  format: Format;
  rows: number;
  wireBytes: number | null;
  bodyBytes: number | null;
  firstRowsMs: number;
  totalMs: number;
}

const SIZES = [10_000, 100_000, 1_000_000];

function resourceSizes(url: string) {
  const absolute = new URL(url, location.href).href;
  const entry = performance
    .getEntriesByType("resource")
    .find((e): e is PerformanceResourceTiming => e.name === absolute && "encodedBodySize" in e);
  return { wireBytes: entry?.encodedBodySize || null, bodyBytes: entry?.decodedBodySize || null };
}

const megabytes = (bytes: number | null) => (bytes == null ? "n/a" : `${(bytes / 1e6).toFixed(2)} MB`);
const millis = (ms: number) => (ms >= 100 ? `${ms.toFixed(0)} ms` : `${ms.toFixed(1)} ms`);

export function App() {
  const [rows, setRows] = useState(100_000);
  const [table, setTable] = useState<Table | null>(null);
  const [runs, setRuns] = useState<Run[]>([]);
  const [status, setStatus] = useState("Pick a size and load it both ways.");
  const [busy, setBusy] = useState(false);
  const runId = useRef(0);

  const loadJson = async (count: number): Promise<Run> => {
    const url = `/api/orders?rows=${count}&run=${++runId.current}`;
    setStatus(`Loading ${count.toLocaleString()} rows as JSON…`);
    const started = performance.now();
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    const data = (await response.json()) as Record<string, unknown>[];
    const totalMs = performance.now() - started;
    setTable(tableFromJSON(data));
    return { format: "json", rows: data.length, firstRowsMs: totalMs, totalMs, ...resourceSizes(url) };
  };

  const loadArrow = async (count: number): Promise<Run> => {
    const url = `/api/orders?rows=${count}&run=${++runId.current}`;
    setStatus(`Streaming ${count.toLocaleString()} rows as Arrow…`);
    let frame = 0;
    const result = await fetchArrow(url, {
      headers: { Accept: ARROW_STREAM },
      onBatch: (progress) => {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(() => {
          setTable(progress.table);
          setStatus(`Streaming… ${progress.rows.toLocaleString()} rows in ${progress.batches} batches`);
        });
      },
    });
    cancelAnimationFrame(frame);
    setTable(result.table);
    return {
      format: "arrow",
      rows: result.rows,
      firstRowsMs: result.firstBatchMs ?? result.elapsedMs,
      totalMs: result.elapsedMs,
      ...resourceSizes(url),
    };
  };

  const run = async (formats: Format[]) => {
    setBusy(true);
    try {
      for (const format of formats) {
        const result = format === "json" ? await loadJson(rows) : await loadArrow(rows);
        setRuns((previous) => [result, ...previous].slice(0, 8));
      }
      setStatus("Done. Scroll the grid or click a header to sort.");
    } catch (error) {
      setStatus(`Failed: ${error instanceof Error ? error.message : String(error)}. Is the example app running on :8080?`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main>
      <header>
        <h1>Columnwire</h1>
        <p>One Spring Boot endpoint, two formats. JSON for everyone, Apache Arrow for clients that ask.</p>
      </header>

      <section className="controls" aria-label="Controls">
        <div className="sizes" role="radiogroup" aria-label="Rows">
          {SIZES.map((size) => (
            <button
              key={size}
              type="button"
              role="radio"
              aria-checked={rows === size}
              className={rows === size ? "active" : ""}
              onClick={() => setRows(size)}
              disabled={busy}
            >
              {size.toLocaleString()} rows
            </button>
          ))}
        </div>
        <div className="actions">
          <button type="button" onClick={() => run(["json"])} disabled={busy}>Load as JSON</button>
          <button type="button" onClick={() => run(["arrow"])} disabled={busy}>Load as Arrow</button>
          <button type="button" className="primary" onClick={() => run(["json", "arrow"])} disabled={busy}>
            Compare both
          </button>
        </div>
      </section>

      <p className="status" role="status">{status}</p>

      {runs.length > 0 ? (
        <table className="runs">
          <thead>
            <tr>
              <th>Format</th>
              <th>Rows</th>
              <th>Over the wire</th>
              <th>Body</th>
              <th>First rows ready</th>
              <th>All rows ready</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((r, i) => (
              <tr key={i} className={r.format}>
                <td>{r.format === "arrow" ? "Arrow" : "JSON"}</td>
                <td>{r.rows.toLocaleString()}</td>
                <td>{megabytes(r.wireBytes)}</td>
                <td>{megabytes(r.bodyBytes)}</td>
                <td>{millis(r.firstRowsMs)}</td>
                <td>{millis(r.totalMs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      <ArrowGrid table={table} height={520} columnWidths={{ id: 110, quantity: 100, priority: 100, createdAt: 220 }} />
    </main>
  );
}

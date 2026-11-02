import { RecordBatch, RecordBatchReader, Table, tableFromIPC, tableFromJSON } from "apache-arrow";

export const ARROW_STREAM = "application/vnd.apache.arrow.stream";

export const ACCEPT_ARROW_OR_JSON = `${ARROW_STREAM}, application/json;q=0.5`;

export type ArrowFormat = "arrow" | "json";

export interface ArrowProgress {
  table: Table;
  batch: RecordBatch;
  rows: number;
  batches: number;
  elapsedMs: number;
}

export interface ArrowResult {
  table: Table;
  format: ArrowFormat;
  rows: number;
  batches: number;
  elapsedMs: number;
  firstBatchMs: number | null;
}

export interface FetchArrowOptions extends RequestInit {
  onBatch?: (progress: ArrowProgress) => void;
  fetch?: typeof globalThis.fetch;
}

export class ArrowHttpError extends Error {
  readonly status: number;
  readonly body: string;

  constructor(status: number, body: string) {
    super(`Request failed with HTTP ${status}`);
    this.name = "ArrowHttpError";
    this.status = status;
    this.body = body;
  }
}

export async function fetchArrow(input: RequestInfo | URL, options: FetchArrowOptions = {}): Promise<ArrowResult> {
  const { onBatch, fetch: fetchImpl = globalThis.fetch, headers, ...init } = options;
  const started = performance.now();
  const requestHeaders = new Headers(headers);
  if (!requestHeaders.has("Accept")) {
    requestHeaders.set("Accept", ACCEPT_ARROW_OR_JSON);
  }
  const response = await fetchImpl(input, { ...init, headers: requestHeaders });
  if (!response.ok) {
    throw new ArrowHttpError(response.status, await response.text().catch(() => ""));
  }
  const contentType = (response.headers.get("Content-Type") ?? "").toLowerCase();
  if (isJson(contentType)) {
    const payload: unknown = await response.json();
    const table = tableFromJSON(toRows(payload));
    const elapsedMs = performance.now() - started;
    return { table, format: "json", rows: table.numRows, batches: table.batches.length, elapsedMs, firstBatchMs: elapsedMs };
  }
  if (!contentType.startsWith(ARROW_STREAM)) {
    throw new Error(`Expected ${ARROW_STREAM} or JSON but the server sent ${contentType || "no Content-Type"}`);
  }
  if (!response.body) {
    const table = tableFromIPC(new Uint8Array(await response.arrayBuffer()));
    const elapsedMs = performance.now() - started;
    return { table, format: "arrow", rows: table.numRows, batches: table.batches.length, elapsedMs, firstBatchMs: elapsedMs };
  }
  const body = response.body.getReader();
  const reader = await RecordBatchReader.from(chunks(body));
  await reader.open();
  const schema = reader.schema;
  const batches: RecordBatch[] = [];
  let rows = 0;
  let firstBatchMs: number | null = null;
  for await (const batch of reader) {
    batches.push(batch);
    rows += batch.numRows;
    const elapsedMs = performance.now() - started;
    firstBatchMs ??= elapsedMs;
    onBatch?.({ table: new Table(batches), batch, rows, batches: batches.length, elapsedMs });
  }
  await drain(body);
  const table = batches.length > 0 ? new Table(batches) : new Table(schema);
  return { table, format: "arrow", rows, batches: batches.length, elapsedMs: performance.now() - started, firstBatchMs };
}

async function* chunks(body: ReadableStreamDefaultReader<Uint8Array>): AsyncGenerator<Uint8Array> {
  for (;;) {
    const { done, value } = await body.read();
    if (done) {
      return;
    }
    yield value;
  }
}

async function drain(body: ReadableStreamDefaultReader<Uint8Array>): Promise<void> {
  for (;;) {
    const { done } = await body.read();
    if (done) {
      body.releaseLock();
      return;
    }
  }
}

function isJson(contentType: string): boolean {
  return contentType.startsWith("application/json") || /^application\/[^;]+\+json/.test(contentType);
}

function toRows(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) {
    return payload as Record<string, unknown>[];
  }
  if (payload !== null && typeof payload === "object") {
    return [payload as Record<string, unknown>];
  }
  throw new Error("Expected a JSON array of objects");
}

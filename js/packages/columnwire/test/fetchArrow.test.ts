import { describe, expect, it, vi } from "vitest";
import { Int32, Table, tableFromArrays, tableToIPC, Schema, Field } from "apache-arrow";
import { ACCEPT_ARROW_OR_JSON, ARROW_STREAM, ArrowHttpError, fetchArrow } from "../src/index.js";
import { fixtureBytes } from "./fixtures.js";

const arrowBytes = fixtureBytes("orders.arrow");

function chunked(bytes: Uint8Array, size: number): ReadableStream<Uint8Array> {
  let offset = 0;
  return new ReadableStream({
    pull(controller) {
      if (offset >= bytes.length) {
        controller.close();
        return;
      }
      controller.enqueue(bytes.slice(offset, offset + size));
      offset += size;
    },
  });
}

function serve(body: BodyInit | null, contentType: string, status = 200) {
  return vi.fn<typeof fetch>(async () => new Response(body, { status, headers: { "Content-Type": contentType } }));
}

describe("fetchArrow", () => {
  it("asks for Arrow first and streams every batch", async () => {
    const fetchMock = serve(chunked(arrowBytes, 97), ARROW_STREAM);
    const progress: number[] = [];

    const result = await fetchArrow("/orders", { fetch: fetchMock, onBatch: (p) => progress.push(p.rows) });

    const headers = new Headers(fetchMock.mock.calls[0]![1]!.headers);
    expect(headers.get("Accept")).toBe(ACCEPT_ARROW_OR_JSON);
    expect(result.format).toBe("arrow");
    expect(result.rows).toBe(64);
    expect(result.batches).toBe(4);
    expect(result.table.numRows).toBe(64);
    expect(result.firstBatchMs).not.toBeNull();
    expect(progress).toEqual([20, 40, 60, 64]);
  });

  it("reads the response to the very end so the connection is released", async () => {
    let closed = false;
    const source = chunked(arrowBytes, 1024).getReader();
    const body = new ReadableStream<Uint8Array>({
      async pull(controller) {
        const { done, value } = await source.read();
        if (done) {
          closed = true;
          controller.close();
        } else {
          controller.enqueue(value);
        }
      },
    });

    await fetchArrow("/orders", { fetch: serve(body, ARROW_STREAM) });

    expect(closed).toBe(true);
  });

  it("keeps an Accept header the caller set", async () => {
    const fetchMock = serve(arrowBytes, ARROW_STREAM);

    await fetchArrow("/orders", { fetch: fetchMock, headers: { Accept: ARROW_STREAM } });

    expect(new Headers(fetchMock.mock.calls[0]![1]!.headers).get("Accept")).toBe(ARROW_STREAM);
  });

  it("falls back to JSON when the server has no Arrow support yet", async () => {
    const fetchMock = serve(JSON.stringify([{ id: 1, sku: "a" }, { id: 2, sku: "b" }]), "application/json");

    const result = await fetchArrow("/orders", { fetch: fetchMock });

    expect(result.format).toBe("json");
    expect(result.table.numRows).toBe(2);
    expect(result.table.getChild("sku")?.get(1)).toBe("b");
  });

  it("returns the schema for an empty stream", async () => {
    const empty = Uint8Array.from(tableToIPC(new Table(new Schema([new Field("x", new Int32(), false)])), "stream"));

    const result = await fetchArrow("/empty", { fetch: serve(empty, ARROW_STREAM) });

    expect(result.rows).toBe(0);
    expect(result.table.schema.fields.map((f) => f.name)).toEqual(["x"]);
  });

  it("raises ArrowHttpError with the status and body", async () => {
    const failing = fetchArrow("/orders", { fetch: serve("nope", "text/plain", 503) });

    await expect(failing).rejects.toBeInstanceOf(ArrowHttpError);
    await expect(failing).rejects.toMatchObject({ status: 503, body: "nope" });
  });

  it("refuses content it cannot decode", async () => {
    await expect(fetchArrow("/orders", { fetch: serve("<html>", "text/html") })).rejects.toThrow(/text\/html/);
  });

  it("reads a body served without streaming support", async () => {
    const bytes = Uint8Array.from(tableToIPC(tableFromArrays({ n: Int32Array.from([1, 2, 3]) }), "stream"));
    const response = new Response(bytes, { headers: { "Content-Type": ARROW_STREAM } });
    Object.defineProperty(response, "body", { value: null });

    const result = await fetchArrow("/n", { fetch: vi.fn<typeof fetch>(async () => response) });

    expect(result.rows).toBe(3);
  });
});

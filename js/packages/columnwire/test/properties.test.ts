import { describe, expect, it } from "vitest";
import fc from "fast-check";
import { Float64, Int32, Table, tableFromArrays, tableToIPC, vectorFromArray, Utf8 } from "apache-arrow";
import { ARROW_STREAM, fetchArrow } from "../src/index.js";
import { formatValue, sortOrder } from "../src/react.js";

function chunked(bytes: Uint8Array, sizes: number[]): ReadableStream<Uint8Array> {
  let offset = 0;
  let turn = 0;
  return new ReadableStream({
    pull(controller) {
      if (offset >= bytes.length) {
        controller.close();
        return;
      }
      const size = sizes[turn++ % sizes.length] ?? 1;
      controller.enqueue(bytes.slice(offset, offset + size));
      offset += size;
    },
  });
}

const rows = fc.array(
  fc.record({
    n: fc.integer({ min: -(2 ** 31), max: 2 ** 31 - 1 }),
    x: fc.option(fc.double({ noNaN: true }), { nil: null }),
    s: fc.option(fc.string({ unit: "grapheme" }), { nil: null }),
  }),
  { maxLength: 300 },
);

function tableOf(data: { n: number; x: number | null; s: string | null }[]): Table {
  return new Table({
    n: vectorFromArray(data.map((r) => r.n), new Int32()),
    x: vectorFromArray(data.map((r) => r.x), new Float64()),
    s: vectorFromArray(data.map((r) => r.s), new Utf8()),
  });
}

describe("properties", () => {
  it("fetchArrow returns exactly what was encoded, however the bytes are chunked", async () => {
    await fc.assert(
      fc.asyncProperty(rows, fc.array(fc.integer({ min: 1, max: 4096 }), { minLength: 1, maxLength: 8 }), async (data, sizes) => {
        const bytes = Uint8Array.from(tableToIPC(tableOf(data), "stream"));
        const response = new Response(chunked(bytes, sizes), { headers: { "Content-Type": ARROW_STREAM } });

        const result = await fetchArrow("/p", { fetch: async () => response });

        expect(result.rows).toBe(data.length);
        data.forEach((expected, i) => {
          const row = result.table.get(i)!;
          expect(row.n).toBe(expected.n);
          expect(row.x).toBe(expected.x);
          expect(row.s).toBe(expected.s);
        });
      }),
      { numRuns: 150 },
    );
  });

  it("sortOrder is a permutation, ordered, with nulls and NaN last in both directions", () => {
    const value = fc.oneof(fc.double(), fc.constant(null), fc.constant(Number.NaN));
    fc.assert(
      fc.property(fc.array(value, { maxLength: 400 }), fc.boolean(), (values, descending) => {
        const vector = tableFromArrays({ v: Float64Array.from(values.map((v) => v ?? 0)) }).getChild("v")!;
        const withNulls = vectorFromArray(values, new Float64());
        for (const target of [vector, withNulls]) {
          const order = Array.from(sortOrder(target, descending));
          expect([...order].sort((a, b) => a - b)).toEqual(target.length ? [...Array(target.length).keys()] : []);
          const seen = order.map((i) => target.get(i) as number | null);
          const firstMissing = seen.findIndex((v) => v === null || Number.isNaN(v));
          if (firstMissing >= 0) {
            expect(seen.slice(firstMissing).every((v) => v === null || Number.isNaN(v))).toBe(true);
          }
          const present = (firstMissing >= 0 ? seen.slice(0, firstMissing) : seen) as number[];
          for (let i = 1; i < present.length; i++) {
            expect(descending ? present[i - 1]! >= present[i]! : present[i - 1]! <= present[i]!).toBe(true);
          }
        }
      }),
      { numRuns: 300 },
    );
  });

  it("formatValue never throws, whatever the value", () => {
    fc.assert(
      fc.property(fc.anything({ withBigInt: true, withDate: true, withMap: true, withSet: true, withTypedArray: true }), (value) => {
        expect(() => formatValue(value)).not.toThrow();
      }),
      { numRuns: 500 },
    );
  });
});

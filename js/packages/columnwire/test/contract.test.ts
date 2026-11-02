import { describe, expect, it } from "vitest";
import { tableFromIPC } from "apache-arrow";

import { fixtureBytes, fixtureText } from "./fixtures.js";

interface JsonOrder {
  id: number;
  sku: string;
  region: string | null;
  status: string | null;
  quantity: number;
  unitPrice: number;
  total: number;
  orderDate: string | null;
  createdAt: string | null;
  customer: string | null;
  priority: boolean;
}

function isoToMicros(iso: string): bigint {
  const match = /^(.*T\d\d:\d\d:\d\d)(?:\.(\d+))?Z$/.exec(iso);
  if (!match?.[1]) {
    throw new Error(`Unexpected instant ${iso}`);
  }
  const seconds = BigInt(Date.parse(`${match[1]}Z`)) * 1000n;
  const fraction = (match[2] ?? "").padEnd(6, "0").slice(0, 6);
  return seconds + BigInt(fraction);
}

describe("Java to JavaScript contract", () => {
  const json = JSON.parse(fixtureText("orders.json")) as JsonOrder[];
  const table = tableFromIPC(fixtureBytes("orders.arrow"));

  it("decodes the batches the Java encoder wrote", () => {
    expect(table.numRows).toBe(64);
    expect(table.batches.map((b) => b.numRows)).toEqual([20, 20, 20, 4]);
    expect(table.schema.fields.map((f) => f.name)).toEqual(Object.keys(json[0]!));
  });

  it("matches the Jackson JSON of the same records, value for value", () => {
    json.forEach((expected, i) => {
      const row = table.get(i)!;
      expect(row.id).toBe(BigInt(expected.id));
      expect(row.sku).toBe(expected.sku);
      expect(row.region).toBe(expected.region);
      expect(row.status).toBe(expected.status);
      expect(row.quantity).toBe(expected.quantity);
      expect(row.unitPrice).toBe(expected.unitPrice);
      expect(row.total).toBe(expected.total);
      expect(row.customer).toBe(expected.customer);
      expect(row.priority).toBe(expected.priority);
      expect(row.orderDate == null ? null : new Date(row.orderDate as number).toISOString().slice(0, 10))
        .toBe(expected.orderDate);
      expect(row.createdAt == null ? null : BigInt(Math.round((row.createdAt as number) * 1000)))
        .toBe(expected.createdAt == null ? null : isoToMicros(expected.createdAt));
    });
  });

  it("keeps nulls as nulls", () => {
    const last = table.get(63)!;
    expect([last.region, last.status, last.orderDate, last.createdAt, last.customer]).toEqual([
      null, null, null, null, null,
    ]);
  });
});

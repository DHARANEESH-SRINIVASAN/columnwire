import { describe, expect, it } from "vitest";
import { Type, tableFromIPC } from "apache-arrow";
import type { Decimal } from "apache-arrow";

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
  const match = /^(.*T\d\d:\d\d:\d\d)(?:\.(\d+))?(Z|[+-]\d\d:\d\d)$/.exec(iso);
  if (!match?.[1]) {
    throw new Error(`Unexpected instant ${iso}`);
  }
  const seconds = BigInt(Date.parse(`${match[1]}${match[3]}`)) * 1000n;
  const fraction = (match[2] ?? "").padEnd(6, "0").slice(0, 6);
  return seconds + BigInt(fraction);
}

type JsonValue = string | boolean | null;

function parseKeepingNumberText(text: string): Record<string, JsonValue>[] {
  const reviver = (_key: string, value: unknown, context?: { source?: string }): unknown =>
    typeof value === "number" ? context?.source : value;
  return JSON.parse(text, reviver) as Record<string, JsonValue>[];
}

function unscaled(decimal: string): bigint {
  return BigInt(decimal.replace(".", ""));
}

function micros(timestamp: unknown): bigint {
  return BigInt(Math.round((timestamp as number) * 1000));
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

describe("JDBC ResultSet to JavaScript contract", () => {
  const json = parseKeepingNumberText(fixtureText("jdbc.json"));
  const table = tableFromIPC(fixtureBytes("jdbc.arrow"));
  const field = (name: string) => table.schema.fields.find((f) => f.name === name)!;

  it("decodes the batches the Java encoder wrote from the ResultSet", () => {
    expect(table.numRows).toBe(64);
    expect(table.batches.map((b) => b.numRows)).toEqual([20, 20, 20, 4]);
    expect(table.schema.fields.map((f) => f.name)).toEqual(Object.keys(json[0]!));
  });

  it("maps SQL types to the Arrow types the README lists", () => {
    const types = Object.fromEntries(table.schema.fields.map((f) => [f.name, f.type.typeId]));
    expect(types).toEqual({
      ID: Type.Int, SKU: Type.Utf8, REGION: Type.Utf8, STATUS: Type.Utf8, QUANTITY: Type.Int,
      UNIT_PRICE: Type.Decimal, TOTAL: Type.Decimal, ORDER_DATE: Type.Date, CREATED_AT: Type.Timestamp,
      CUSTOMER: Type.Utf8, PRIORITY: Type.Bool, REFUND: Type.Decimal, TINY: Type.Int, SMALL: Type.Int,
      RATIO: Type.Float, APPROX: Type.Float, CLOCK: Type.Time, CREATED_ZONED: Type.Timestamp, CODE: Type.Utf8,
      NOTHING: Type.Null,
    });
    const price = field("UNIT_PRICE").type as Decimal;
    expect([price.precision, price.scale, price.bitWidth]).toEqual([10, 2, 128]);
    expect((field("TOTAL").type as Decimal).precision).toBe(12);
  });

  it("matches the queryForList JSON of the same query, value for value", () => {
    json.forEach((expected, i) => {
      const row = table.get(i)!;
      expect(row.ID).toBe(BigInt(expected.ID as string));
      expect(row.SKU).toBe(expected.SKU);
      expect(row.REGION).toBe(expected.REGION);
      expect(row.STATUS).toBe(expected.STATUS);
      expect(row.QUANTITY).toBe(Number(expected.QUANTITY));
      expect(BigInt(String(row.UNIT_PRICE))).toBe(unscaled(expected.UNIT_PRICE as string));
      expect(BigInt(String(row.TOTAL))).toBe(unscaled(expected.TOTAL as string));
      expect(BigInt(String(row.REFUND))).toBe(unscaled(expected.REFUND as string));
      expect(row.ORDER_DATE).toBe(expected.ORDER_DATE == null ? null : Date.parse(expected.ORDER_DATE as string));
      expect(row.CREATED_AT == null ? null : Math.floor(row.CREATED_AT as number)).toBe(
        expected.CREATED_AT == null ? null : Date.parse(expected.CREATED_AT as string));
      expect(row.CUSTOMER).toBe(expected.CUSTOMER);
      expect(row.PRIORITY).toBe(expected.PRIORITY);
      expect(row.TINY).toBe(Number(expected.TINY));
      expect(row.SMALL).toBe(Number(expected.SMALL));
      expect(row.RATIO).toBe(Math.fround(Number(expected.RATIO)));
      expect(row.APPROX).toBe(Number(expected.APPROX));
      expect(row.CLOCK == null ? null : new Date(Number((row.CLOCK as bigint) / 1000n)).toISOString().slice(11, 19))
        .toBe(expected.CLOCK);
      expect(row.CREATED_ZONED == null ? null : micros(row.CREATED_ZONED)).toBe(
        expected.CREATED_ZONED == null ? null : isoToMicros(expected.CREATED_ZONED as string));
      expect(row.CODE).toBe(expected.CODE);
      expect(row.NOTHING).toBeNull();
    });
  });

  it("keeps the microseconds that the JSON timestamp rounds away", () => {
    const row = table.get(1)!;
    expect(micros(row.CREATED_AT)).toBe(isoToMicros(json[1]!.CREATED_ZONED as string));
    expect(micros(row.CREATED_AT) % 1000n).not.toBe(0n);
  });

  it("keeps SQL NULLs as nulls", () => {
    const nulls = table.get(0)!;
    expect(nulls.SKU).toBe("SKU-NULLS");
    expect([nulls.REGION, nulls.STATUS, nulls.ORDER_DATE, nulls.CREATED_AT, nulls.CUSTOMER, nulls.CLOCK,
      nulls.CREATED_ZONED, nulls.CODE, nulls.NOTHING]).toEqual([null, null, null, null, null, null, null, null, null]);
  });
});

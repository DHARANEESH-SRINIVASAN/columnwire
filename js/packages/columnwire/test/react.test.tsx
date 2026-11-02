import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor, within } from "@testing-library/react";
import { tableFromArrays, tableFromIPC } from "apache-arrow";
import { ARROW_STREAM } from "../src/index.js";
import { fixtureBytes } from "./fixtures.js";
import { ArrowGrid, MAX_SCROLL_HEIGHT, formatValue, sortOrder, useArrow } from "../src/react.js";

const arrowBytes = fixtureBytes("orders.arrow");

afterEach(cleanup);

function numbers(count: number) {
  const n = new Int32Array(count);
  for (let i = 0; i < count; i++) {
    n[i] = (i * 7919) % count;
  }
  return tableFromArrays({ n });
}

function bodyRows(container: HTMLElement) {
  return within(container).getAllByRole("row").filter((r) => r.getAttribute("aria-rowindex") !== "1");
}

describe("useArrow", () => {
  it("loads a table and reports progress", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      new Response(arrowBytes, { headers: { "Content-Type": ARROW_STREAM } }),
    );

    const { result } = renderHook(() => useArrow("/orders", { fetch: fetchMock }));

    expect(result.current.status).toBe("loading");
    await waitFor(() => expect(result.current.status).toBe("done"));
    expect(result.current.rows).toBe(64);
    expect(result.current.batches).toBe(4);
    expect(result.current.format).toBe("arrow");
    expect(result.current.table?.numRows).toBe(64);
  });

  it("reports errors", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => new Response("down", { status: 500 }));

    const { result } = renderHook(() => useArrow("/orders", { fetch: fetchMock }));

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error?.message).toContain("500");
  });

  it("aborts the request when unmounted", async () => {
    let signal: AbortSignal | undefined;
    const fetchMock = vi.fn<typeof fetch>((_, init) => {
      signal = init?.signal ?? undefined;
      return new Promise<Response>(() => {});
    });

    const { unmount } = renderHook(() => useArrow("/slow", { fetch: fetchMock }));
    unmount();

    expect(signal?.aborted).toBe(true);
  });

  it("stays idle without a url and refetches on demand", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      new Response(arrowBytes, { headers: { "Content-Type": ARROW_STREAM } }),
    );
    const { result, rerender } = renderHook(({ url }) => useArrow(url, { fetch: fetchMock }), {
      initialProps: { url: null as string | null },
    });
    expect(result.current.status).toBe("idle");

    rerender({ url: "/orders" });
    await waitFor(() => expect(result.current.status).toBe("done"));
    act(() => result.current.refetch());
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });
});

describe("ArrowGrid", () => {
  it("renders only the rows in view", () => {
    const { container } = render(<ArrowGrid table={numbers(100_000)} height={320} rowHeight={32} />);

    const rows = bodyRows(container);
    expect(rows.length).toBeLessThanOrEqual(16);
    expect(rows[0]?.getAttribute("aria-rowindex")).toBe("2");
    expect(screen.getByRole("grid").getAttribute("aria-rowcount")).toBe("100001");
  });

  it("moves the window as you scroll", () => {
    const { container } = render(<ArrowGrid table={numbers(100_000)} height={320} rowHeight={32} overscan={0} />);
    const grid = screen.getByRole("grid");

    fireEvent.scroll(grid, { target: { scrollTop: 32 * 5000 } });

    expect(bodyRows(container)[0]?.getAttribute("aria-rowindex")).toBe(String(5000 + 2));
  });

  it("caps the scroll height and still reaches the last row of huge tables", () => {
    const count = 2_000_000;
    const { container } = render(<ArrowGrid table={numbers(count)} height={320} rowHeight={32} overscan={0} />);
    const grid = screen.getByRole("grid");
    const spacer = grid.querySelector('[aria-hidden="true"]') as HTMLElement;
    expect(spacer.style.height).toBe(`${MAX_SCROLL_HEIGHT}px`);

    fireEvent.scroll(grid, { target: { scrollTop: MAX_SCROLL_HEIGHT - (320 - 32) } });

    const rows = bodyRows(container);
    expect(rows.at(-1)?.getAttribute("aria-rowindex")).toBe(String(count + 1));
  });

  it("sorts by a column, then reverses, then clears", () => {
    const table = numbers(1000);
    const { container } = render(<ArrowGrid table={table} height={200} rowHeight={20} />);
    const header = screen.getByRole("button", { name: "n" });
    const firstCell = () => within(bodyRows(container)[0]!).getAllByRole("gridcell")[0]!.textContent;

    fireEvent.click(header);
    expect(firstCell()).toBe("0");
    expect(screen.getByRole("columnheader").getAttribute("aria-sort")).toBe("ascending");
    fireEvent.click(header);
    expect(firstCell()).toBe("999");
    fireEvent.click(header);
    expect(screen.getByRole("columnheader").getAttribute("aria-sort")).toBe("none");
  });

  it("formats values from the Java fixture", () => {
    const table = tableFromIPC(arrowBytes);
    render(<ArrowGrid table={table} height={400} columns={["id", "orderDate", "createdAt", "region"]} />);

    const firstRow = screen.getAllByRole("row")[1]!;
    const cells = within(firstRow).getAllByRole("gridcell").map((c) => c.textContent);
    expect(cells[0]).toBe(String(table.getChild("id")!.get(0)));
    expect(cells[1]).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(cells[2]).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/);
    expect(cells[3]).toBe(table.getChild("region")!.get(0));
  });

  it("shows the empty message for a table with no rows", () => {
    render(<ArrowGrid table={tableFromArrays({ n: new Int32Array(0) })} empty="Nothing here" />);

    expect(screen.getByRole("note").textContent).toBe("Nothing here");
  });
});

describe("helpers", () => {
  it("sorts nulls last in both directions", () => {
    const vector = tableFromArrays({ s: ["b", null, "a", "c"] as (string | null)[] }).getChild("s")!;

    expect(Array.from(sortOrder(vector, false))).toEqual([2, 0, 3, 1]);
    expect(Array.from(sortOrder(vector, true))).toEqual([3, 0, 2, 1]);
  });

  it("formats bigints and empties", () => {
    expect(formatValue(12n)).toBe("12");
    expect(formatValue(null)).toBe("");
    expect(formatValue(undefined)).toBe("");
  });
});

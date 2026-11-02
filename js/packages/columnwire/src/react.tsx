import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, ReactNode, UIEvent } from "react";
import { DataType, Type } from "apache-arrow";
import type { Table, Vector } from "apache-arrow";
import { fetchArrow } from "./index.js";
import type { ArrowFormat, FetchArrowOptions } from "./index.js";

export type ArrowStatus = "idle" | "loading" | "streaming" | "done" | "error";

export interface ArrowState {
  table: Table | null;
  status: ArrowStatus;
  error: Error | null;
  rows: number;
  batches: number;
  format: ArrowFormat | null;
  elapsedMs: number | null;
  firstBatchMs: number | null;
  refetch: () => void;
}

export type UseArrowOptions = Omit<FetchArrowOptions, "onBatch" | "signal">;

type Snapshot = Omit<ArrowState, "refetch">;

const IDLE: Snapshot = {
  table: null,
  status: "idle",
  error: null,
  rows: 0,
  batches: 0,
  format: null,
  elapsedMs: null,
  firstBatchMs: null,
};

export function useArrow(url: string | URL | null | undefined, options?: UseArrowOptions): ArrowState {
  const [snapshot, setSnapshot] = useState<Snapshot>(IDLE);
  const [generation, setGeneration] = useState(0);
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const key = url == null ? null : String(url);

  useEffect(() => {
    if (key == null) {
      setSnapshot(IDLE);
      return;
    }
    const controller = new AbortController();
    let frame = 0;
    let pending: Snapshot | null = null;
    const flush = () => {
      frame = 0;
      if (pending && !controller.signal.aborted) {
        setSnapshot(pending);
        pending = null;
      }
    };
    setSnapshot({ ...IDLE, status: "loading" });
    let firstBatchMs: number | null = null;
    fetchArrow(key, {
      ...optionsRef.current,
      signal: controller.signal,
      onBatch: (progress) => {
        firstBatchMs ??= progress.elapsedMs;
        pending = {
          table: progress.table,
          status: "streaming",
          error: null,
          rows: progress.rows,
          batches: progress.batches,
          format: "arrow",
          elapsedMs: progress.elapsedMs,
          firstBatchMs,
        };
        if (frame === 0) {
          frame = requestAnimationFrame(flush);
        }
      },
    }).then(
      (result) => {
        if (controller.signal.aborted) {
          return;
        }
        cancelAnimationFrame(frame);
        pending = null;
        setSnapshot({
          table: result.table,
          status: "done",
          error: null,
          rows: result.rows,
          batches: result.batches,
          format: result.format,
          elapsedMs: result.elapsedMs,
          firstBatchMs: result.firstBatchMs,
        });
      },
      (error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        cancelAnimationFrame(frame);
        pending = null;
        setSnapshot({ ...IDLE, status: "error", error: error instanceof Error ? error : new Error(String(error)) });
      },
    );
    return () => {
      controller.abort();
      cancelAnimationFrame(frame);
    };
  }, [key, generation]);

  const refetch = useCallback(() => setGeneration((g) => g + 1), []);
  return { ...snapshot, refetch };
}

export interface ArrowGridProps {
  table: Table | null | undefined;
  height?: number;
  rowHeight?: number;
  overscan?: number;
  columns?: string[];
  columnWidth?: number;
  columnWidths?: Partial<Record<string, number>>;
  format?: Partial<Record<string, (value: unknown) => ReactNode>>;
  sortable?: boolean;
  className?: string;
  style?: CSSProperties;
  empty?: ReactNode;
}

export interface SortState {
  column: string;
  descending: boolean;
}

export const MAX_SCROLL_HEIGHT = 15_000_000;

export function ArrowGrid({
  table,
  height = 480,
  rowHeight = 32,
  overscan = 6,
  columns,
  columnWidth = 160,
  columnWidths,
  format,
  sortable = true,
  className,
  style,
  empty = "No rows",
}: ArrowGridProps) {
  const [scrollTop, setScrollTop] = useState(0);
  const [sort, setSort] = useState<SortState | null>(null);
  const names = useMemo(
    () => columns ?? table?.schema.fields.map((f) => f.name) ?? [],
    [columns, table],
  );
  const vectors = useMemo(
    () => names.map((name) => (table?.getChild(name) ?? null) as Vector | null),
    [names, table],
  );
  const order = useMemo(() => {
    if (!sort || !table) {
      return null;
    }
    const index = names.indexOf(sort.column);
    const vector = index >= 0 ? vectors[index] : null;
    return vector ? sortOrder(vector, sort.descending) : null;
  }, [sort, table, names, vectors]);

  const rowCount = table?.numRows ?? 0;
  const headerHeight = rowHeight;
  const viewport = Math.max(height - headerHeight, rowHeight);
  const fullHeight = rowCount * rowHeight;
  const spacerHeight = Math.min(fullHeight, MAX_SCROLL_HEIGHT);
  const maxScroll = Math.max(spacerHeight - viewport, 0);
  const scale = spacerHeight > viewport ? Math.max(fullHeight - viewport, 0) / maxScroll : 1;
  const offset = Math.min(scrollTop, maxScroll) * scale;
  const first = Math.max(Math.floor(offset / rowHeight) - overscan, 0);
  const last = Math.min(Math.ceil((offset + viewport) / rowHeight) + overscan, rowCount);

  const widths = names.map((name) => columnWidths?.[name] ?? columnWidth);
  const template = widths.map((w) => `${w}px`).join(" ");
  const totalWidth = widths.reduce((sum, w) => sum + w, 0);

  const onScroll = useCallback((event: UIEvent<HTMLDivElement>) => {
    setScrollTop(event.currentTarget.scrollTop);
  }, []);

  const toggleSort = (column: string) => {
    if (!sortable) {
      return;
    }
    setSort((current) =>
      current?.column !== column
        ? { column, descending: false }
        : current.descending
          ? null
          : { column, descending: true },
    );
  };

  const rows: ReactNode[] = [];
  for (let visual = first; visual < last; visual++) {
    const rowIndex = order ? (order[visual] ?? visual) : visual;
    const top = visual * rowHeight - offset + Math.min(scrollTop, maxScroll);
    rows.push(
      <div
        key={visual}
        role="row"
        aria-rowindex={visual + 2}
        className="cw-row"
        style={{
          position: "absolute",
          top: top + headerHeight,
          left: 0,
          height: rowHeight,
          width: totalWidth,
          display: "grid",
          gridTemplateColumns: template,
          background: visual % 2 === 1 ? "var(--cw-row-alt, rgba(127,127,127,0.06))" : "transparent",
        }}
      >
        {names.map((name, c) => {
          const value = vectors[c]?.get(rowIndex);
          const render = format?.[name];
          return (
            <div key={name} role="gridcell" className="cw-cell" style={CELL}>
              {render ? render(value) : formatValue(value, vectors[c]?.type)}
            </div>
          );
        })}
      </div>,
    );
  }

  return (
    <div
      role="grid"
      aria-rowcount={rowCount + 1}
      aria-colcount={names.length}
      className={className ? `cw-grid ${className}` : "cw-grid"}
      onScroll={onScroll}
      style={{
        position: "relative",
        height,
        overflow: "auto",
        fontFamily: "var(--cw-font, ui-sans-serif, system-ui, sans-serif)",
        fontSize: 13,
        color: "var(--cw-text, inherit)",
        border: "1px solid var(--cw-border, rgba(127,127,127,0.3))",
        borderRadius: 6,
        ...style,
      }}
    >
      <div
        role="row"
        aria-rowindex={1}
        className="cw-header"
        style={{
          position: "sticky",
          top: 0,
          zIndex: 1,
          height: headerHeight,
          width: totalWidth,
          display: "grid",
          gridTemplateColumns: template,
          background: "var(--cw-header-bg, Canvas)",
          borderBottom: "1px solid var(--cw-border, rgba(127,127,127,0.3))",
          fontWeight: 600,
        }}
      >
        {names.map((name) => {
          const active = sort?.column === name;
          return (
            <div
              key={name}
              role="columnheader"
              aria-sort={active ? (sort.descending ? "descending" : "ascending") : "none"}
              className="cw-cell"
              style={CELL}
            >
              <button
                type="button"
                onClick={() => toggleSort(name)}
                disabled={!sortable}
                style={HEADER_BUTTON}
              >
                {name}
                {active ? (sort.descending ? " ▼" : " ▲") : ""}
              </button>
            </div>
          );
        })}
      </div>
      <div aria-hidden="true" style={{ height: spacerHeight, width: totalWidth }} />
      {rows}
      {rowCount === 0 && table ? (
        <div role="note" style={{ position: "absolute", top: headerHeight + 12, left: 12, opacity: 0.7 }}>
          {empty}
        </div>
      ) : null}
    </div>
  );
}

const CELL: CSSProperties = {
  overflow: "hidden",
  textOverflow: "ellipsis",
  whiteSpace: "nowrap",
  padding: "0 10px",
  display: "flex",
  alignItems: "center",
  borderRight: "1px solid var(--cw-border, rgba(127,127,127,0.15))",
};

const HEADER_BUTTON: CSSProperties = {
  all: "unset",
  cursor: "pointer",
  width: "100%",
  overflow: "hidden",
  textOverflow: "ellipsis",
};

export function formatValue(value: unknown, type?: DataType): ReactNode {
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "bigint") {
    return value.toString();
  }
  if (typeof value === "number" && type) {
    if (type.typeId === Type.Date) {
      return new Date(value).toISOString().slice(0, 10);
    }
    if (type.typeId === Type.Timestamp) {
      return new Date(value).toISOString().replace("T", " ").slice(0, 23);
    }
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === "object") {
    return JSON.stringify(value, (_, v: unknown) => (typeof v === "bigint" ? v.toString() : v));
  }
  return String(value);
}

export function sortOrder(vector: Vector, descending: boolean): Uint32Array {
  const length = vector.length;
  const order = new Uint32Array(length);
  for (let i = 0; i < length; i++) {
    order[i] = i;
  }
  const fast = vector.nullCount === 0 && isPrimitive(vector.type);
  const values: ArrayLike<unknown> = fast ? (vector.toArray() as ArrayLike<unknown>) : Array.from(vector);
  const direction = descending ? -1 : 1;
  order.sort((a, b) => {
    const x = values[a] as number | bigint | string | boolean | null | undefined;
    const y = values[b] as number | bigint | string | boolean | null | undefined;
    const xMissing = isMissing(x);
    const yMissing = isMissing(y);
    if (xMissing || yMissing) {
      return xMissing ? (yMissing ? a - b : 1) : -1;
    }
    if (x < y) {
      return -direction;
    }
    if (x > y) {
      return direction;
    }
    return a - b;
  });
  return order;
}

function isMissing(value: unknown): value is null | undefined {
  return value == null || (typeof value === "number" && Number.isNaN(value));
}

function isPrimitive(type: DataType): boolean {
  switch (type.typeId) {
    case Type.Int:
    case Type.Float:
    case Type.Date:
    case Type.Timestamp:
    case Type.Time:
      return true;
    default:
      return false;
  }
}

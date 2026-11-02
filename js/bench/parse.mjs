import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { performance } from "node:perf_hooks";
import { tableFromIPC } from "apache-arrow";

const dir = resolve(process.argv[2] ?? "../columnwire-benchmark/target/payloads");
const sizes = (process.argv[3] ?? "10000,100000,1000000").split(",").map(Number);
const VISIBLE = 30;
const gc = globalThis.gc ?? (() => {});

function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)];
}

function time(fn, runs) {
  for (let i = 0; i < 2; i++) fn();
  const samples = [];
  for (let i = 0; i < runs; i++) {
    gc();
    const start = performance.now();
    fn();
    samples.push(performance.now() - start);
  }
  return median(samples);
}

function retained(make) {
  gc();
  const before = process.memoryUsage();
  const value = make();
  gc();
  const after = process.memoryUsage();
  const bytes = after.heapUsed - before.heapUsed + (after.arrayBuffers - before.arrayBuffers);
  return { value, bytes };
}

const decoder = new TextDecoder();
const jsonParse = (bytes) => JSON.parse(decoder.decode(bytes));
const arrowParse = (bytes) => tableFromIPC(bytes);

function firstScreenJson(bytes) {
  const rows = jsonParse(bytes);
  let touched = 0;
  for (let i = 0; i < VISIBLE; i++) for (const k in rows[i]) touched += rows[i][k] == null ? 0 : 1;
  return touched;
}

function firstScreenArrow(bytes) {
  const table = arrowParse(bytes);
  const columns = table.schema.fields.map((f) => table.getChild(f.name));
  let touched = 0;
  for (let i = 0; i < VISIBLE; i++) for (const c of columns) touched += c.get(i) == null ? 0 : 1;
  return touched;
}

const results = [];
for (const rows of sizes) {
  const json = new Uint8Array(readFileSync(resolve(dir, `orders-${rows}.json`)));
  const arrow = new Uint8Array(readFileSync(resolve(dir, `orders-${rows}.arrow`)));
  const runs = rows >= 1_000_000 ? 5 : 15;
  const parsedJson = jsonParse(json);
  const parsedArrow = arrowParse(arrow);
  if (parsedJson.length !== rows || parsedArrow.numRows !== rows) throw new Error("row count mismatch");
  const totalColumn = parsedArrow.getChild("total");
  const row = {
    rows,
    parseJsonMs: time(() => jsonParse(json), runs),
    parseArrowMs: time(() => arrowParse(arrow), runs),
    firstScreenJsonMs: time(() => firstScreenJson(json), runs),
    firstScreenArrowMs: time(() => firstScreenArrow(arrow), runs),
    sumTotalJsonMs: time(() => { let s = 0; for (const o of parsedJson) s += o.total; return s; }, runs),
    sumTotalArrowMs: time(() => { let s = 0; const a = totalColumn.toArray(); for (let i = 0; i < a.length; i++) s += a[i]; return s; }, runs),
    memoryJsonBytes: retained(() => jsonParse(json)).bytes,
    memoryArrowBytes: retained(() => arrowParse(arrow)).bytes + arrow.byteLength,
  };
  results.push(row);
  console.error(JSON.stringify(row));
}

const fmt = (n) => (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2));
const mb = (b) => (b / 1e6).toFixed(1);
console.log(`Node ${process.version} (V8 ${process.versions.v8}), apache-arrow ${JSON.parse(readFileSync(new URL("../node_modules/apache-arrow/package.json", import.meta.url))).version}`);
console.log("| Rows | Decode JSON | Decode Arrow | First screen JSON | First screen Arrow | Sum a column JSON | Sum a column Arrow | Memory JSON | Memory Arrow |");
console.log("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
for (const r of results) {
  console.log(`| ${r.rows.toLocaleString("en-US")} | ${fmt(r.parseJsonMs)} ms | ${fmt(r.parseArrowMs)} ms | ${fmt(r.firstScreenJsonMs)} ms | ${fmt(r.firstScreenArrowMs)} ms | ${fmt(r.sumTotalJsonMs)} ms | ${fmt(r.sumTotalArrowMs)} ms | ${mb(r.memoryJsonBytes)} MB | ${mb(r.memoryArrowBytes)} MB |`);
}

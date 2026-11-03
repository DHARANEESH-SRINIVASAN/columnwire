<h1 align="center">Columnwire</h1>

<p align="center">
  <b>Apache Arrow from Spring Boot to React. Same endpoints, columnar bytes instead of JSON.</b>
</p>

<p align="center">
  <a href="https://github.com/DHARANEESH-SRINIVASAN/columnwire/actions/workflows/ci.yml"><img src="https://github.com/DHARANEESH-SRINIVASAN/columnwire/actions/workflows/ci.yml/badge.svg" alt="CI"/></a>
  <a href="https://central.sonatype.com/artifact/io.github.dharaneesh-srinivasan/columnwire-spring-boot-starter"><img src="https://img.shields.io/maven-central/v/io.github.dharaneesh-srinivasan/columnwire-spring-boot-starter?label=Maven%20Central" alt="Maven Central"/></a>
  <a href="https://www.npmjs.com/package/columnwire"><img src="https://img.shields.io/npm/v/columnwire?label=npm" alt="npm"/></a>
  <a href="https://www.npmjs.com/package/columnwire"><img src="https://img.shields.io/npm/dm/columnwire?label=downloads" alt="npm downloads"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="Apache-2.0"/></a>
  <br/>
  <a href="https://www.bestpractices.dev/projects/16203"><img src="https://www.bestpractices.dev/projects/16203/badge" alt="OpenSSF Best Practices"/></a>
  <a href="https://www.bestpractices.dev/projects/16203"><img src="https://www.bestpractices.dev/projects/16203/baseline" alt="OpenSSF Baseline"/></a>
  <a href="https://scorecard.dev/viewer/?uri=github.com/DHARANEESH-SRINIVASAN/columnwire"><img src="https://api.scorecard.dev/projects/github.com/DHARANEESH-SRINIVASAN/columnwire/badge" alt="OpenSSF Scorecard"/></a>
  <a href="https://github.com/DHARANEESH-SRINIVASAN/columnwire/actions/workflows/codeql.yml"><img src="https://github.com/DHARANEESH-SRINIVASAN/columnwire/actions/workflows/codeql.yml/badge.svg" alt="CodeQL"/></a>
  <a href="https://www.npmjs.com/package/columnwire"><img src="https://img.shields.io/badge/npm-provenance-2f9e44" alt="npm provenance"/></a>
</p>

<p align="center">
  <a href="https://dharaneesh-srinivasan.github.io/columnwire/"><b>▶ Try the live demo</b></a> · 300,000 rows as JSON and as Arrow, fetched and decoded in your browser
</p>

<p align="center">
  <img src="docs/demo.png" width="820" alt="The demo loading one million rows from the same Spring Boot endpoint as JSON and as Arrow"/>
</p>

Most Java backends send tables to React as JSON. The server turns every value into text, the browser parses it back into one object per row, and the grid has to wait for the last byte before it can show the first row. At a hundred thousand rows that wait is visible. At a million it is several seconds and a quarter of a gigabyte of heap.

Columnwire keeps your endpoints exactly as they are and adds a second representation. A client that sends `Accept: application/vnd.apache.arrow.stream` gets [Apache Arrow](https://arrow.apache.org/) record batches, streamed as they are encoded. Every other client keeps getting JSON. On the React side, a hook and a virtualized grid read the columns straight out of the Arrow buffers, so no row objects are ever built.

```
┌──────────────┐   Accept: application/vnd.apache.arrow.stream   ┌──────────────────────────────┐
│ React        │ ─────────────────────────────────────────────▶  │ Spring Boot                  │
│ useArrow()   │                                                  │ @GetMapping List<Order> ...  │
│ <ArrowGrid/> │ ◀── batch 1 ── batch 2 ── ... ── batch n ─────── │ columnwire-spring-boot-starter│
└──────────────┘     rows on screen after the first batch         └──────────────────────────────┘
        curl, mobile apps, other services: no Arrow in Accept ─▶ the same endpoint answers with JSON
```

## Results

Measured on one machine (Intel i7-1255U, 12 threads, 31 GB, Ubuntu, JDK 25.0.5, Node 22.23.3, Chrome headless). Each record has 11 fields: ids, SKUs, two enums, quantities, prices, a date, an instant, a nullable customer and a flag. Numbers vary by machine, so every one of them can be reproduced with the commands below.

**Server: encoding in Spring Boot** (JMH, average of 6 iterations after 4 warmups, against Jackson 3, the JSON library Spring Boot 4 uses)

| Rows | Jackson JSON | Columnwire Arrow | Garbage per response, JSON → Arrow |
|---:|---:|---:|---:|
| 10,000 | 6.28 ms | **1.46 ms** (4.3×) | 5.3 MB → 0.6 MB |
| 100,000 | 67.4 ms | **14.4 ms** (4.7×) | 52.8 MB → 5.9 MB |
| 1,000,000 | 643 ms | **154 ms** (4.2×) | 576 MB → 58 MB |

**Payload size**

| Rows | JSON | Arrow | JSON, gzip | Arrow, gzip |
|---:|---:|---:|---:|---:|
| 10,000 | 2.23 MB | **0.69 MB** | 0.39 MB | **0.27 MB** |
| 1,000,000 | 223 MB | **68.7 MB** (3.2× smaller) | 39.2 MB | **27.2 MB** (1.4× smaller) |

JSON compresses well, so with gzip on the size gap is 1.4×, not 3.2×. The larger wins are in the next two tables.

**Browser engine: decoding** (V8, median; Arrow decoding is zero-copy, values are read from the buffer when accessed)

| Rows | Decode JSON | Decode Arrow | First 30 rows ready, JSON | First 30 rows ready, Arrow | Memory held, JSON | Memory held, Arrow |
|---:|---:|---:|---:|---:|---:|---:|
| 10,000 | 8.98 ms | **0.92 ms** | 7.74 ms | **0.97 ms** | 2.4 MB | **0.7 MB** |
| 100,000 | 95.1 ms | **2.15 ms** | 95.6 ms | **1.94 ms** | 23.8 MB | **7.0 MB** |
| 1,000,000 | 1,213 ms | **7.23 ms** | 1,244 ms | **9.91 ms** | 238.5 MB | **69.7 MB** |

"Memory held, Arrow" includes the response buffer itself, which the table keeps alive.

**End to end in Chrome** (the demo below, Spring Boot with gzip on, 1,000,000 rows)

| | JSON (`fetch().json()`) | Arrow (`fetchArrow`) |
|---|---:|---:|
| Over the wire | 39.16 MB | **26.48 MB** |
| First rows on screen | 6,573 ms | **103 ms** |
| All rows loaded | 6,573 ms | **4,868 ms** |

The headline is the first-paint number: the grid starts drawing after the first batch of 16,384 rows instead of after the last byte.

## Quick start

### Spring Boot

```xml
<dependency>
  <groupId>io.github.dharaneesh-srinivasan</groupId>
  <artifactId>columnwire-spring-boot-starter</artifactId>
  <version>0.2.0</version>
</dependency>
```

Arrow reads off-heap memory, so the JVM needs one flag (add `--sun-misc-unsafe-memory-access=allow` as well on Java 24 and newer to silence a deprecation warning):

```
--add-opens=java.base/java.nio=ALL-UNNAMED
```

Your controllers do not change:

```java
public record Order(long id, String sku, Region region, double total, LocalDate orderDate, Instant createdAt) {}

@GetMapping("/api/orders")
Stream<Order> orders() {
    return repository.streamAll();
}
```

`List`, `Stream`, `Iterable`, arrays and `ResponseEntity` of records all work. Rows are encoded in batches as they are pulled from the `Stream`, so a large result is never held twice in memory. SQL results can skip the records entirely: see [Database queries](#database-queries).

### React

```sh
npm install columnwire apache-arrow
```

```tsx
import { useArrow, ArrowGrid } from "columnwire/react";

export function Orders() {
  const { table, status, rows, error } = useArrow("/api/orders");
  if (error) return <p>{error.message}</p>;
  return (
    <>
      <p>{status === "streaming" ? `Loading… ${rows.toLocaleString()} rows` : `${rows.toLocaleString()} rows`}</p>
      <ArrowGrid table={table} height={520} />
    </>
  );
}
```

Without React, `fetchArrow(url, { onBatch })` returns an Arrow `Table` and reports each batch as it lands. The client asks for `application/vnd.apache.arrow.stream, application/json;q=0.5`, so it also works against a backend that does not have the starter yet: JSON is turned into the same `Table`.

## How it behaves

- **Only on request.** The Arrow converter is appended after Spring's own converters, so `Accept: */*`, browsers, `curl` and axios defaults keep getting JSON. Clients have to ask for Arrow explicitly, and quality values (`q=`) are honoured.
- **Cache-safe.** Every endpoint that can answer in both formats sends `Vary: Accept`, on the JSON responses as well, so a shared cache never hands one client the other's format.
- **Honest failures.** A body Arrow cannot represent (a single object, a record with a nested list) gets `406 Not Acceptable` for Arrow and still serves JSON. A JVM without the flag above fails at startup with the exact flag to add, instead of a cryptic error on the first request.
- **Streaming.** Records are written in batches of `columnwire.batch-size` (default 8192). The browser can render batch one while the server is still encoding batch two.
- **Same names as your JSON.** Jackson's `@JsonProperty` renames and `@JsonIgnore` are respected, so column names match the JSON your clients already know.
- **Enums are dictionaries.** An enum column is sent as one-byte indices plus a dictionary of its constants, which the browser decodes back to strings.

| Java | Arrow | JavaScript value |
|---|---|---|
| `boolean`, `Boolean` | Bool | `boolean` |
| `byte`, `short`, `int` and boxes | Int8, Int16, Int32 | `number` |
| `long`, `Long` | Int64 | `bigint` |
| `float`, `double` and boxes | Float32, Float64 | `number` |
| `String`, `char`, `UUID`, `BigInteger` | Utf8 | `string` |
| `BigDecimal` | Utf8 (plain string, lossless) | `string` |
| `enum` | Dictionary of Utf8 | `string` |
| `LocalDate` | Date32 | `number` (epoch ms) |
| `LocalDateTime` | Timestamp µs | `number` (epoch ms) |
| `Instant`, `OffsetDateTime`, `ZonedDateTime` | Timestamp µs, UTC | `number` (epoch ms) |
| `LocalTime` | Time64 µs | `bigint` |

Primitive components are non-nullable columns. Everything else is nullable.

### Configuration

| Property | Default | |
|---|---|---|
| `columnwire.enabled` | `true` | Set to `false` to remove the converter entirely |
| `columnwire.batch-size` | `8192` | Rows per Arrow record batch |

A note for anyone wiring Arrow into Spring by hand: on Java 24 and newer, Arrow's Netty allocator (`arrow-memory-netty`) fails with a `ClassCastException` because Netty turns off its `Unsafe` path there. Columnwire uses `arrow-memory-unsafe`, which works on Java 17 through 25.

## Database queries

Teams that read tables with `JdbcTemplate` can stream the `ResultSet` straight into Arrow batches, without a `Map` or an object per row. Put `spring-boot-starter-jdbc` on the classpath (Columnwire only needs `spring-jdbc`, and keeps it optional) and return a `JdbcQuery`:

```java
@GetMapping("/api/orders")
JdbcQuery orders(@RequestParam LocalDate since) {
    return JdbcQuery.of(jdbcTemplate, "select * from orders where order_date >= ?", since);
}
```

- **Runs while the response is written.** The query executes inside `JdbcTemplate` when the converter starts writing, and Columnwire holds one batch at a time. The `ResultSet`, statement and connection are closed when the response ends, when it fails, and when the client disconnects halfway (tested for both formats against a tracking `DataSource`).
- **Same negotiation as records.** Arrow only for clients that ask for it, JSON for `*/*`, browsers and axios, quality values honoured, `Vary: Accept` on both, and `ResponseEntity<JdbcQuery>` keeps its headers.
- **The JSON does not change.** Every other client gets the JSON that `jdbcTemplate.queryForList(sql)` returns from the same endpoint, byte for byte in the tests, but written one row at a time instead of after the whole list is built. This path uses Jackson 3, which Spring Boot 4 configures.
- **Column names are the column labels**, the same keys `queryForList` uses, so aliases (`select id as "orderId"`) carry over. H2, for one, upper-cases unquoted names.
- **Unsupported columns fail before the first byte.** A column type Arrow cannot carry yet answers `500` before anything is sent, and the log names the column and its type: `Column PAYLOAD has SQL type VARBINARY (BINARY VARYING), which Columnwire does not encode. Supported: …`. Decimals wider than 38 digits, `NUMERIC` without a precision (PostgreSQL reports `SUM`, `AVG` and plain `numeric` columns that way) and times with a time zone get a suggested `CAST`. Clients that ask for JSON are not affected.

| SQL type (`java.sql.Types`) | Arrow | JavaScript value |
|---|---|---|
| `BOOLEAN`, `BIT(1)` | Bool | `boolean` |
| `TINYINT`, `SMALLINT`, `INTEGER` | Int8, Int16, Int32 | `number` |
| `BIGINT` | Int64 | `bigint` |
| unsigned `TINYINT`, `SMALLINT`, `INTEGER`, `BIGINT` | Int16, Int32, Int64, Decimal128(20, 0) | `number`, `number`, `bigint`, Arrow decimal |
| `REAL` | Float32 | `number` |
| `FLOAT`, `DOUBLE` | Float64 | `number` |
| `DECIMAL(p, s)`, `NUMERIC(p, s)`, p up to 38 | Decimal128(p, s) | Arrow decimal; `ArrowGrid` shows it with its scale |
| `CHAR`, `VARCHAR`, `LONGVARCHAR`, `NCHAR`, `NVARCHAR`, `LONGNVARCHAR` | Utf8 | `string` |
| `DATE` | Date32 | `number` (epoch ms) |
| `TIME` | Time64 µs | `bigint` |
| `TIMESTAMP` | Timestamp µs, no zone | `number` (epoch ms) |
| `TIMESTAMP WITH TIME ZONE`, and PostgreSQL `timestamptz` (which its driver reports as `TIMESTAMP`) | Timestamp µs, UTC | `number` (epoch ms) |
| `NULL` | Null | `null` |

Columns the driver reports as `NOT NULL` are non-nullable; every other column is nullable, and SQL `NULL` arrives as `null`. `DATE`, `TIME` and `TIMESTAMP` are read as `LocalDate`, `LocalTime` and `LocalDateTime`, so Arrow carries the values stored in the database, to the microsecond. The JSON from `queryForList` goes through `java.sql.Date` and `java.sql.Timestamp` instead, which Jackson writes as UTC instants with millisecond precision, shifted by the JVM's time zone: on a JVM in `Asia/Kolkata`, `DATE '2026-01-01'` comes out as `"2025-12-31T18:30:00.000Z"`. On a JVM running in UTC the two agree to the millisecond.

Whether rows really stream from the database depends on the driver. By its documentation, MySQL needs `useCursorFetch=true` with a fetch size. PostgreSQL reads the whole result into the driver unless auto-commit is off and a fetch size is set, which is what this measurement shows.

**Measured on PostgreSQL 18.6** (pgjdbc 42.7.14, a local server, 1,000,000 rows of `bigint`, `integer`, `numeric(12, 2)`, `varchar` and `timestamptz`, `curl` on the same machine, second of two runs). A 15-column check of every type above, NULLs included, matched the database value for value in both setups.

| Setup | Format | First byte | Whole response | Size | Server peak memory |
|---|---|---:|---:|---:|---:|
| Defaults | JSON | 0.58 s | 1.92 s | 111.5 MB | 1.17 GB |
| Defaults | Arrow | 0.54 s | **0.96 s** | **55.6 MB** | (same process) |
| `hikari.auto-commit=false`, `jdbc.template.fetch-size=1000` | JSON | 8 ms | 2.05 s | 111.5 MB | 0.44 GB |
| `hikari.auto-commit=false`, `jdbc.template.fetch-size=1000` | Arrow | 24 ms | **1.12 s** | **55.6 MB** | (same process) |

With the defaults the driver holds every row before the first one reaches Columnwire, so the first byte waits for the database either way and the gain is in total time and size. Turning auto-commit off for a whole pool changes how every other query in the application commits, so use it only for a pool that serves reads. Columnwire does not change auto-commit itself yet.

**Measured: a query, JdbcTemplate to bytes** (JMH on the machine above, JDK 25.0.5, average of 6 iterations of 2 s after 4 warmups, 1 fork, `-Xmx8g`, `-prof gc`). Both sides run `SELECT * FROM orders ORDER BY id` through `JdbcTemplate` on one reused connection to an in-memory H2 2.4.240 database (`LAZY_QUERY_EXECUTION=TRUE`), over the same 11-column orders as the record benchmark, with prices as `DECIMAL(10, 2)` and `DECIMAL(12, 2)` and the creation time as `TIMESTAMP(6)`. The baseline is `queryForList` written by Jackson 3. "First 8 KiB" is the time until 8 KiB, Tomcat's default response buffer, have reached the output stream.

| Rows | `queryForList` + Jackson | Columnwire `JdbcQuery` | First 8 KiB out, JSON → Arrow | Garbage per query, JSON → Arrow |
|---:|---:|---:|---:|---:|
| 10,000 | 23.9 ms | **5.48 ms** (4.4×) | 8.99 ms → **4.87 ms** | 23.6 MB → 4.7 MB |
| 100,000 | 227 ms | **58.1 ms** (3.9×) | 88.5 ms → **4.66 ms** | 233 MB → 46.8 MB |
| 1,000,000 | 3,721 ms | **603 ms** (6.2×) | 1,718 ms → **4.44 ms** | 2,335 MB → 468 MB |

`queryForList` cannot send a byte before it has built a `Map` for every row, so its first bytes wait for the whole result; Columnwire's first batch of 8,192 rows was out in 4.4 to 4.9 ms at every size measured. At a million rows the error margins (99.9%) are wide: ±1,014 ms for JSON, whose million maps keep the garbage collector busy (36 collections across the measured iterations), and ±118 ms for Arrow. The garbage figures include the objects H2 itself creates while reading rows, which both sides pay. A real database adds network and server time to both columns. Raw results are in [`docs/jdbc-jmh-result.json`](docs/jdbc-jmh-result.json).

## The grid

`ArrowGrid` renders only the rows in view, plus a few above and below. It reads each visible cell with `vector.get(row)` and never materializes the table. It stays usable past the browsers' maximum element height by scaling the scroll range, so the last of two million rows is still reachable. Clicking a header sorts the whole column (numbers, dates and timestamps sort on the typed array directly), clicking again reverses, and a third click clears. It exposes `role="grid"`, `aria-rowcount` and `aria-sort`, and takes `columns`, `columnWidths`, `format` and CSS variables (`--cw-border`, `--cw-header-bg`, `--cw-row-alt`) for styling.

## Run it yourself

The [live demo](https://dharaneesh-srinivasan.github.io/columnwire/) runs on static files that CI encodes with `columnwire-core` on every push. To run it against a real Spring Boot server, which streams batches while it encodes them:

```sh
mvn -pl columnwire-example -am install -DskipTests
mvn -pl columnwire-example spring-boot:run
cd js && npm install && npm run dev -w columnwire-demo
```

Open http://localhost:5173 and press **Compare both**.

Benchmarks:

```sh
mvn -pl columnwire-benchmark -am package -DskipTests
cd columnwire-benchmark
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" org.openjdk.jmh.Main EncodeBenchmark -prof gc
java -cp "target/classes:$(cat target/cp.txt)" org.openjdk.jmh.Main JdbcBenchmark -prof gc
java --add-opens=java.base/java.nio=ALL-UNNAMED -cp "target/classes:$(cat target/cp.txt)" \
  io.github.dharaneeshsrinivasan.columnwire.benchmark.PayloadSizes target/payloads
cd ../js && node --expose-gc --max-old-space-size=6000 bench/parse.mjs ../columnwire-benchmark/target/payloads
```

Raw results from the runs above are in [`docs/`](docs).

## Tests

- **Core:** round-trips every supported type through Arrow's own Java reader, checks batch splitting, empty streams, Jackson renames, and that no off-heap memory leaks. The JDBC path does the same for every mapped SQL type from a real H2 `ResultSet`, with NULLs, decimals, column labels, the unsupported-type messages and driver errors.
- **Starter:** runs against a real Tomcat on a random port: Arrow on request; JSON for `*/*`, browser and axios `Accept` headers; quality values; `Stream`, arrays and `ResponseEntity`; 50,000 rows in 50 batches; `406` for bodies Arrow cannot represent; `Vary` on both formats; the disabled switch. `JdbcQuery` gets the same checks on an in-memory H2 database, plus JSON compared byte for byte with `queryForList`, bound arguments, the `500` for unsupported columns, and a client that disconnects mid-stream, after which every `ResultSet`, statement and connection is closed and fewer rows were read than the query returns.
- **Contract:** a fixture written by the Java encoder is decoded in JavaScript and compared value for value with Jackson's JSON for the same records, down to microsecond timestamps. A second fixture is written from an H2 `ResultSet` and compared with `queryForList`'s JSON for the same query, decimals digit for digit.
- **JS:** streaming in 97-byte chunks, the JSON fallback, aborts on unmount, reading the response to the end, windowed rendering, sorting, and the two-million-row scroll cap.

CI runs the Java suite on JDK 17, 21 and 25 and the JS suite on Node 22.

## Limits and roadmap

- Records and JDBC queries for now. JavaBeans and `Map` rows are next.
- JDBC: binary, LOB, UUID, array, interval and vendor-specific column types are not encoded yet (cast them in SQL). The automated tests run against H2; PostgreSQL 18.6 was checked by hand as described above; MySQL, SQL Server and Oracle drivers have not been tested yet. The JSON side of `JdbcQuery` needs Jackson 3.
- Streaming rows from PostgreSQL without pool settings: `JdbcQuery` could open its own read-only transaction with a fetch size.
- Nested records, lists and maps are not encoded yet (those bodies get `406` for Arrow and stay JSON).
- Responses only. Arrow request bodies are not read.
- Spring MVC only. WebFlux support would stream `Flux<Record>`.
- Low-cardinality `String` columns could be dictionary-encoded per batch for smaller payloads.

## Layout

```
columnwire-core/                  records and JDBC ResultSets → Arrow IPC stream, schema cache, type mapping
columnwire-spring-boot-starter/   HttpMessageConverter, JdbcQuery, auto-configuration, Vary advice
columnwire-benchmark/             JMH benchmarks, payload sizes, cross-language fixtures
columnwire-example/               Spring Boot app used by the demo
js/packages/columnwire/           fetchArrow, useArrow, ArrowGrid (npm: columnwire)
js/demo/                          Vite + React comparison page
js/bench/                         V8 decode benchmark
```

## Security and verification

Report vulnerabilities privately, as described in [SECURITY.md](SECURITY.md). Every release can be checked independently:

```sh
gpg --keyserver keys.openpgp.org --recv-keys B9F051CF9545B2AB72829518908A18CEE22E2846
gpg --verify columnwire-spring-boot-starter-0.2.0.jar.asc columnwire-spring-boot-starter-0.2.0.jar
npm audit signatures
```

Since 0.1.1, release jars and the npm tarball also carry GitHub build-provenance attestations (`gh attestation verify <file> -R DHARANEESH-SRINIVASAN/columnwire`). Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md) and the [changelog](CHANGELOG.md).

## License

Apache License 2.0. Built by [Dharaneesh S](https://github.com/DHARANEESH-SRINIVASAN).

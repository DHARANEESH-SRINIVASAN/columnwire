# columnwire

Stream [Apache Arrow](https://arrow.apache.org/) from a Spring Boot backend into React. A fetch client that hands you each record batch as it lands, a hook, and a virtualized grid that reads columns straight from the Arrow buffers without building row objects.

On one million rows in Chrome, the first rows were on screen after **103 ms with Arrow versus 6,573 ms with `fetch().json()`**. Full benchmarks and the Spring Boot side are in the [main repository](https://github.com/DHARANEESH-SRINIVASAN/columnwire). [Try the live demo](https://dharaneesh-srinivasan.github.io/columnwire/).

```sh
npm install columnwire apache-arrow
```

## React

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

`useArrow(url, options)` returns `{ table, status, error, rows, batches, format, elapsedMs, firstBatchMs, refetch }`. `status` moves through `idle`, `loading`, `streaming`, `done` and `error`. Progress updates are coalesced to one per animation frame, and the request is aborted when the component unmounts or the URL changes.

`<ArrowGrid>` props: `table`, `height`, `rowHeight`, `overscan`, `columns`, `columnWidth`, `columnWidths`, `format`, `sortable`, `className`, `style`, `empty`. Click a header to sort, click again to reverse, a third time to clear. Style it with the CSS variables `--cw-border`, `--cw-header-bg`, `--cw-row-alt`, `--cw-text` and `--cw-font`.

## Without React

```ts
import { fetchArrow } from "columnwire";

const { table, rows, batches, format, firstBatchMs } = await fetchArrow("/api/orders", {
  onBatch: ({ rows }) => console.log(`${rows} rows so far`),
});
```

The request sends `Accept: application/vnd.apache.arrow.stream, application/json;q=0.5`. A server without Arrow support answers with JSON, which is turned into the same Arrow `Table`, so the frontend can move first. Non-2xx responses throw `ArrowHttpError` with `status` and `body`.

## Values

Arrow `Int64` arrives as `bigint`, dates and timestamps as epoch milliseconds, dictionary columns (Java enums) as strings, and nulls as `null`. SQL `DECIMAL` columns streamed with the starter's `JdbcQuery` arrive as Arrow decimals, whose `String(value)` is the unscaled integer. The grid formats all of these for display, decimals with their scale.

## License

Apache-2.0

package io.github.dharaneeshsrinivasan.columnwire;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

public final class ArrowRecordEncoder implements AutoCloseable {

    public static final int DEFAULT_BATCH_SIZE = 8192;

    private final BufferAllocator allocator;
    private final boolean ownsAllocator;
    private final int batchSize;

    public ArrowRecordEncoder() {
        this(DEFAULT_BATCH_SIZE);
    }

    public ArrowRecordEncoder(int batchSize) {
        this(new RootAllocator(), true, batchSize);
    }

    public ArrowRecordEncoder(BufferAllocator allocator, int batchSize) {
        this(allocator, false, batchSize);
    }

    private ArrowRecordEncoder(BufferAllocator allocator, boolean ownsAllocator, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        this.allocator = allocator;
        this.ownsAllocator = ownsAllocator;
        this.batchSize = batchSize;
    }

    public int batchSize() {
        return batchSize;
    }

    public <T extends Record> EncodeResult encode(Class<T> type, Iterable<? extends T> rows, OutputStream out)
            throws IOException {
        return encode(type, rows.iterator(), out);
    }

    public <T extends Record> EncodeResult encode(Class<T> type, Stream<? extends T> rows, OutputStream out)
            throws IOException {
        return encode(type, rows.iterator(), out);
    }

    public <T extends Record> EncodeResult encode(Class<T> type, Iterator<? extends T> rows, OutputStream out)
            throws IOException {
        RecordSchema<T> schema = RecordSchema.of(type);
        try {
            return encode("columnwire-" + type.getSimpleName(), schema.arrowSchema(), schema.columns(),
                    RowCursor.of(rows), out);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public EncodeResult encode(ResultSet rows, OutputStream out) throws IOException, SQLException {
        List<ColumnSpec<ResultSet>> columns = JdbcColumns.of(rows.getMetaData());
        List<Field> fields = new ArrayList<>(columns.size());
        for (ColumnSpec<ResultSet> column : columns) {
            fields.add(column.field());
        }
        return encode("columnwire-jdbc", new Schema(fields), columns, RowCursor.of(rows), out);
    }

    private <R> EncodeResult encode(String name, Schema schema, List<ColumnSpec<R>> columns, RowCursor<R> rows,
                                    OutputStream out) throws IOException, SQLException {
        List<Dictionary> dictionaries = new ArrayList<>();
        try (BufferAllocator child = allocator.newChildAllocator(name, 0, Long.MAX_VALUE);
             VectorSchemaRoot root = VectorSchemaRoot.create(schema, child)) {
            try {
                DictionaryProvider.MapDictionaryProvider provider = new DictionaryProvider.MapDictionaryProvider();
                for (ColumnSpec<R> column : columns) {
                    if (column.dictionaryEncoded()) {
                        Dictionary dictionary = dictionary(column, child);
                        dictionaries.add(dictionary);
                        provider.put(dictionary);
                    }
                }
                @SuppressWarnings("unchecked")
                ColumnWriter<R>[] writers = new ColumnWriter[columns.size()];
                for (int c = 0; c < writers.length; c++) {
                    FieldVector vector = root.getVector(c);
                    vector.setInitialCapacity(batchSize);
                    writers[c] = columns.get(c).binder().apply(vector);
                }
                return write(rows, root, writers, provider, out);
            } finally {
                for (Dictionary dictionary : dictionaries) {
                    dictionary.getVector().close();
                }
            }
        }
    }

    private <R> EncodeResult write(RowCursor<R> rows, VectorSchemaRoot root, ColumnWriter<R>[] writers,
                                   DictionaryProvider provider, OutputStream out) throws IOException, SQLException {
        long total = 0;
        int batches = 0;
        try (ArrowStreamWriter writer = new ArrowStreamWriter(root, provider, new NonClosingOutputStream(out))) {
            writer.start();
            root.allocateNew();
            int n = 0;
            while (rows.next()) {
                R row = rows.row();
                for (ColumnWriter<R> w : writers) {
                    try {
                        w.write(row, n);
                    } catch (RuntimeException | Error | SQLException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new IllegalStateException(e);
                    }
                }
                if (++n == batchSize) {
                    root.setRowCount(n);
                    writer.writeBatch();
                    total += n;
                    batches++;
                    n = 0;
                    root.allocateNew();
                }
            }
            if (n > 0) {
                root.setRowCount(n);
                writer.writeBatch();
                total += n;
                batches++;
            }
            writer.end();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return new EncodeResult(total, batches);
    }

    private static Dictionary dictionary(ColumnSpec<?> column, BufferAllocator allocator) {
        Field field = new Field(column.name(), FieldType.nullable(ArrowType.Utf8.INSTANCE), null);
        VarCharVector values = (VarCharVector) field.createVector(allocator);
        List<String> names = column.dictionary();
        values.allocateNew(names.size());
        for (int i = 0; i < names.size(); i++) {
            values.setSafe(i, names.get(i).getBytes(StandardCharsets.UTF_8));
        }
        values.setValueCount(names.size());
        return new Dictionary(values, column.field().getDictionary());
    }

    @Override
    public void close() {
        if (ownsAllocator) {
            allocator.close();
        }
    }
}

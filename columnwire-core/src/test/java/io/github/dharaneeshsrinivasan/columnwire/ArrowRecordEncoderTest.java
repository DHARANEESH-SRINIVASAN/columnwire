package io.github.dharaneeshsrinivasan.columnwire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArrowRecordEncoderTest {

    enum Region { NORTH, SOUTH, EAST, WEST }

    record Everything(boolean flag, byte tiny, short small, int count, long id, float ratio, double price,
                      char grade, Boolean maybe, Integer optionalCount, Long optionalId, Double optionalPrice,
                      String name, UUID uuid, BigDecimal amount, Region region, LocalDate day,
                      LocalDateTime local, LocalTime time, Instant at) {
    }

    record Renamed(@JsonProperty("order_id") long id, @JsonIgnore String secret, String sku) {
    }

    record Nested(String name, List<String> tags) {
    }

    record Point(int x, int y) {
    }

    private BufferAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new RootAllocator();
    }

    @AfterEach
    void tearDown() {
        assertThat(allocator.getAllocatedMemory()).isZero();
        allocator.close();
    }

    @Test
    void roundTripsEveryType() throws IOException {
        UUID uuid = UUID.fromString("0b0e9f0e-3c33-4b55-9a0c-7d8f6b3f2a11");
        Instant at = Instant.parse("2026-11-02T10:15:30.123456Z");
        Everything full = new Everything(true, (byte) -7, (short) 300, 42, 9_000_000_000L, 0.5f, 19.99,
                'A', Boolean.FALSE, 7, 11L, 2.25, "Widget ✓", uuid, new BigDecimal("1234567890.123456789"),
                Region.EAST, LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 2, 9, 30, 0, 500_000),
                LocalTime.of(23, 59, 58, 1_000), at);
        Everything sparse = new Everything(false, (byte) 0, (short) 0, 0, 0L, 0f, 0d, 'z', null, null, null,
                null, null, null, null, null, null, null, null, null);

        byte[] bytes = encode(Everything.class, List.of(full, sparse), 1024);

        try (ArrowStreamReader reader = reader(bytes)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertThat(reader.loadNextBatch()).isTrue();
            assertThat(root.getRowCount()).isEqualTo(2);

            assertThat(root.getVector("flag").getObject(0)).isEqualTo(true);
            assertThat(root.getVector("tiny").getObject(0)).isEqualTo((byte) -7);
            assertThat(root.getVector("small").getObject(0)).isEqualTo((short) 300);
            assertThat(root.getVector("count").getObject(0)).isEqualTo(42);
            assertThat(root.getVector("id").getObject(0)).isEqualTo(9_000_000_000L);
            assertThat(root.getVector("ratio").getObject(0)).isEqualTo(0.5f);
            assertThat(root.getVector("price").getObject(0)).isEqualTo(19.99);
            assertThat(root.getVector("grade").getObject(0).toString()).isEqualTo("A");
            assertThat(root.getVector("maybe").getObject(0)).isEqualTo(false);
            assertThat(root.getVector("optionalCount").getObject(0)).isEqualTo(7);
            assertThat(root.getVector("optionalId").getObject(0)).isEqualTo(11L);
            assertThat(root.getVector("optionalPrice").getObject(0)).isEqualTo(2.25);
            assertThat(root.getVector("name").getObject(0).toString()).isEqualTo("Widget ✓");
            assertThat(root.getVector("uuid").getObject(0).toString()).isEqualTo(uuid.toString());
            assertThat(root.getVector("amount").getObject(0).toString()).isEqualTo("1234567890.123456789");
            assertThat(((Number) root.getVector("day").getObject(0)).longValue())
                    .isEqualTo(LocalDate.of(2026, 11, 2).toEpochDay());
            assertThat(root.getVector("local").getObject(0))
                    .isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 30, 0, 0).plusNanos(500_000));
            assertThat(root.getVector("time").getObject(0))
                    .isEqualTo(LocalTime.of(23, 59, 58, 1_000).toNanoOfDay() / 1_000L);
            assertThat(root.getVector("at").getObject(0)).isEqualTo(1_793_614_530_123_456L);
            assertThat(decodeRegion(reader, root, 0)).isEqualTo("EAST");

            for (String nullable : List.of("maybe", "optionalCount", "optionalId", "optionalPrice", "name", "uuid",
                    "amount", "region", "day", "local", "time", "at")) {
                assertThat(root.getVector(nullable).isNull(1)).as(nullable).isTrue();
            }
            assertThat(root.getVector("flag").getObject(1)).isEqualTo(false);
            assertThat(reader.loadNextBatch()).isFalse();
        }
    }

    @Test
    void mapsJavaTypesToArrowTypes() {
        List<Field> fields = RecordSchema.of(Everything.class).arrowSchema().getFields();
        assertThat(type(fields, "flag")).isEqualTo(ArrowType.Bool.INSTANCE);
        assertThat(type(fields, "count")).isEqualTo(new ArrowType.Int(32, true));
        assertThat(type(fields, "id")).isEqualTo(new ArrowType.Int(64, true));
        assertThat(type(fields, "name")).isEqualTo(ArrowType.Utf8.INSTANCE);
        assertThat(type(fields, "at").toString()).isEqualTo("Timestamp(MICROSECOND, UTC)");
        assertThat(type(fields, "local").toString()).isEqualTo("Timestamp(MICROSECOND, null)");
        assertThat(type(fields, "region")).isEqualTo(new ArrowType.Int(8, true));
        assertThat(field(fields, "region").getDictionary()).isNotNull();
        assertThat(field(fields, "count").isNullable()).isFalse();
        assertThat(field(fields, "optionalCount").isNullable()).isTrue();
    }

    @Test
    void splitsLargeInputsIntoBatches() throws IOException {
        List<Point> points = IntStream.range(0, 20_001).mapToObj(i -> new Point(i, -i)).toList();

        byte[] bytes = encode(Point.class, points, 8192);

        List<Integer> sizes = new ArrayList<>();
        long sum = 0;
        try (ArrowStreamReader reader = reader(bytes)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            int seen = 0;
            while (reader.loadNextBatch()) {
                sizes.add(root.getRowCount());
                FieldVector x = root.getVector("x");
                for (int i = 0; i < root.getRowCount(); i++) {
                    assertThat(x.getObject(i)).isEqualTo(seen + i);
                    sum += (Integer) root.getVector("y").getObject(i);
                }
                seen += root.getRowCount();
            }
        }
        assertThat(sizes).containsExactly(8192, 8192, 3617);
        assertThat(sum).isEqualTo(-IntStream.range(0, 20_001).asLongStream().sum());
    }

    @Test
    void reportsRowsAndBatches() throws IOException {
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, 10)) {
            EncodeResult result = encoder.encode(Point.class,
                    IntStream.range(0, 25).mapToObj(i -> new Point(i, i)), OutputStream.nullOutputStream());
            assertThat(result).isEqualTo(new EncodeResult(25, 3));
        }
    }

    @Test
    void emptyInputStillCarriesTheSchema() throws IOException {
        byte[] bytes = encode(Point.class, List.of(), 16);

        try (ArrowStreamReader reader = reader(bytes)) {
            assertThat(reader.getVectorSchemaRoot().getSchema().getFields()).extracting(Field::getName)
                    .containsExactly("x", "y");
            assertThat(reader.loadNextBatch()).isFalse();
        }
    }

    @Test
    void followsJacksonRenamesAndIgnores() throws IOException {
        byte[] bytes = encode(Renamed.class, List.of(new Renamed(5, "hunter2", "SKU-1")), 16);

        try (ArrowStreamReader reader = reader(bytes)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertThat(root.getSchema().getFields()).extracting(Field::getName).containsExactly("order_id", "sku");
            assertThat(reader.loadNextBatch()).isTrue();
            assertThat(root.getVector("order_id").getObject(0)).isEqualTo(5L);
        }
    }

    @Test
    void rejectsTypesItCannotEncode() {
        assertThat(RecordSchema.supports(Nested.class)).isFalse();
        assertThat(RecordSchema.supports(String.class)).isFalse();
        assertThat(RecordSchema.supports(Point.class)).isTrue();
        assertThatThrownBy(() -> RecordSchema.of(Nested.class))
                .isInstanceOf(UnsupportedRecordException.class)
                .hasMessageContaining("tags")
                .hasMessageContaining("java.util.List");
    }

    @Test
    void leavesTheCallersStreamOpen() throws IOException {
        boolean[] closed = {false};
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, 4)) {
            encoder.encode(Point.class, List.of(new Point(1, 2)), sink);
        }
        assertThat(closed[0]).isFalse();
        assertThat(sink.size()).isPositive();
    }

    private <T extends Record> byte[] encode(Class<T> type, List<T> rows, int batchSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, batchSize)) {
            encoder.encode(type, rows, out);
        }
        return out.toByteArray();
    }

    private ArrowStreamReader reader(byte[] bytes) {
        return new ArrowStreamReader(new ByteArrayInputStream(bytes), allocator);
    }

    private static String decodeRegion(ArrowStreamReader reader, VectorSchemaRoot root, int row) throws IOException {
        FieldVector indices = root.getVector("region");
        Dictionary dictionary = reader.getDictionaryVectors().get(indices.getField().getDictionary().getId());
        try (VarCharVector decoded = (VarCharVector) DictionaryEncoder.decode(indices, dictionary)) {
            return decoded.getObject(row).toString();
        }
    }

    private static Field field(List<Field> fields, String name) {
        return fields.stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }

    private static ArrowType type(List<Field> fields, String name) {
        return field(fields, name).getType();
    }
}

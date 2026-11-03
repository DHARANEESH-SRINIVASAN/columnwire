package io.github.dharaneeshsrinivasan.columnwire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArrowRecordEncoderJdbcTest {

    record StubColumn(String label, int type, String typeName, int precision, boolean signed) {
    }

    private static final String EVERYTHING = "SELECT id, flag, tiny, small, count, ratio, price, approx, amount,"
            + " big_amount, code, name, due, created, created_zoned, clock, NULL AS nothing"
            + " FROM everything ORDER BY id";

    private BufferAllocator allocator;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        allocator = new RootAllocator();
        connection = DriverManager.getConnection("jdbc:h2:mem:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE everything (id BIGINT NOT NULL PRIMARY KEY, flag BOOLEAN, tiny TINYINT,"
                    + " small SMALLINT, count INTEGER, ratio REAL, price DOUBLE PRECISION, approx FLOAT,"
                    + " amount DECIMAL(12, 2), big_amount NUMERIC(38, 10), code CHAR(3), name VARCHAR(40), due DATE,"
                    + " created TIMESTAMP(6), created_zoned TIMESTAMP(6) WITH TIME ZONE, clock TIME(6))");
            statement.execute("INSERT INTO everything VALUES (1, TRUE, -7, 300, 42, 0.5, 19.99, 2.25,"
                    + " 1234567890.12, -1234567890123456789012345678.1234567890, 'AB', 'Widget ✓',"
                    + " DATE '2026-11-02', TIMESTAMP '2026-11-02 09:30:00.0005',"
                    + " TIMESTAMP WITH TIME ZONE '2026-11-02 10:15:30.123456+05:30', TIME '23:59:58.000001')");
            statement.execute("INSERT INTO everything (id) VALUES (2)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
        assertThat(allocator.getAllocatedMemory()).isZero();
        allocator.close();
    }

    @Test
    void roundTripsEverySqlType() throws Exception {
        byte[] bytes = encode(EVERYTHING, 1024);

        try (ArrowStreamReader reader = reader(bytes)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertThat(reader.loadNextBatch()).isTrue();
            assertThat(root.getRowCount()).isEqualTo(2);

            assertThat(root.getVector("ID").getObject(0)).isEqualTo(1L);
            assertThat(root.getVector("FLAG").getObject(0)).isEqualTo(true);
            assertThat(root.getVector("TINY").getObject(0)).isEqualTo((byte) -7);
            assertThat(root.getVector("SMALL").getObject(0)).isEqualTo((short) 300);
            assertThat(root.getVector("COUNT").getObject(0)).isEqualTo(42);
            assertThat(root.getVector("RATIO").getObject(0)).isEqualTo(0.5f);
            assertThat(root.getVector("PRICE").getObject(0)).isEqualTo(19.99);
            assertThat(root.getVector("APPROX").getObject(0)).isEqualTo(2.25);
            assertThat(root.getVector("AMOUNT").getObject(0)).isEqualTo(new BigDecimal("1234567890.12"));
            assertThat(root.getVector("BIG_AMOUNT").getObject(0))
                    .isEqualTo(new BigDecimal("-1234567890123456789012345678.1234567890"));
            assertThat(root.getVector("CODE").getObject(0).toString()).isEqualTo("AB ");
            assertThat(root.getVector("NAME").getObject(0).toString()).isEqualTo("Widget ✓");
            assertThat(((Number) root.getVector("DUE").getObject(0)).longValue())
                    .isEqualTo(LocalDate.of(2026, 11, 2).toEpochDay());
            assertThat(root.getVector("CREATED").getObject(0))
                    .isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 30, 0, 500_000));
            assertThat(root.getVector("CREATED_ZONED").getObject(0))
                    .isEqualTo(micros("2026-11-02T10:15:30.123456+05:30"));
            assertThat(root.getVector("CLOCK").getObject(0))
                    .isEqualTo(LocalTime.of(23, 59, 58, 1_000).toNanoOfDay() / 1_000L);
            assertThat(root.getVector("NOTHING").isNull(0)).isTrue();

            assertThat(root.getVector("ID").getObject(1)).isEqualTo(2L);
            for (Field field : root.getSchema().getFields()) {
                if (!field.getName().equals("ID")) {
                    assertThat(root.getVector(field.getName()).isNull(1)).as(field.getName()).isTrue();
                }
            }
            assertThat(reader.loadNextBatch()).isFalse();
        }
    }

    @Test
    void mapsSqlTypesToArrowTypes() throws Exception {
        List<Field> fields = schema(encode(EVERYTHING, 16));

        assertThat(type(fields, "ID")).isEqualTo(new ArrowType.Int(64, true));
        assertThat(type(fields, "FLAG")).isEqualTo(ArrowType.Bool.INSTANCE);
        assertThat(type(fields, "TINY")).isEqualTo(new ArrowType.Int(8, true));
        assertThat(type(fields, "SMALL")).isEqualTo(new ArrowType.Int(16, true));
        assertThat(type(fields, "COUNT")).isEqualTo(new ArrowType.Int(32, true));
        assertThat(type(fields, "RATIO").toString()).isEqualTo("FloatingPoint(SINGLE)");
        assertThat(type(fields, "PRICE").toString()).isEqualTo("FloatingPoint(DOUBLE)");
        assertThat(type(fields, "APPROX").toString()).isEqualTo("FloatingPoint(DOUBLE)");
        assertThat(type(fields, "AMOUNT")).isEqualTo(new ArrowType.Decimal(12, 2, 128));
        assertThat(type(fields, "BIG_AMOUNT")).isEqualTo(new ArrowType.Decimal(38, 10, 128));
        assertThat(type(fields, "CODE")).isEqualTo(ArrowType.Utf8.INSTANCE);
        assertThat(type(fields, "NAME")).isEqualTo(ArrowType.Utf8.INSTANCE);
        assertThat(type(fields, "DUE").toString()).isEqualTo("Date(DAY)");
        assertThat(type(fields, "CREATED").toString()).isEqualTo("Timestamp(MICROSECOND, null)");
        assertThat(type(fields, "CREATED_ZONED").toString()).isEqualTo("Timestamp(MICROSECOND, UTC)");
        assertThat(type(fields, "CLOCK").toString()).isEqualTo("Time(MICROSECOND, 64)");
        assertThat(type(fields, "NOTHING")).isEqualTo(ArrowType.Null.INSTANCE);
        assertThat(field(fields, "ID").isNullable()).isFalse();
        assertThat(field(fields, "COUNT").isNullable()).isTrue();
    }

    @Test
    void mapsTypesTheTestDatabaseDoesNotReport() throws Exception {
        ResultSet rows = stub(List.of(
                new StubColumn("long_text", Types.LONGVARCHAR, "TEXT", 0, true),
                new StubColumn("national", Types.NCHAR, "NCHAR", 2, true),
                new StubColumn("national_text", Types.NVARCHAR, "NVARCHAR", 20, true),
                new StubColumn("long_national", Types.LONGNVARCHAR, "NTEXT", 0, true),
                new StubColumn("bit", Types.BIT, "BIT", 1, true),
                new StubColumn("unsigned_tiny", Types.TINYINT, "TINYINT UNSIGNED", 3, false),
                new StubColumn("unsigned_small", Types.SMALLINT, "SMALLINT UNSIGNED", 5, false),
                new StubColumn("unsigned_int", Types.INTEGER, "INT UNSIGNED", 10, false),
                new StubColumn("unsigned_big", Types.BIGINT, "BIGINT UNSIGNED", 20, false)), 0);

        List<Field> fields = schema(encode(rows, 16));

        assertThat(fields).extracting(Field::getType).containsExactly(ArrowType.Utf8.INSTANCE,
                ArrowType.Utf8.INSTANCE, ArrowType.Utf8.INSTANCE, ArrowType.Utf8.INSTANCE, ArrowType.Bool.INSTANCE,
                new ArrowType.Int(16, true), new ArrowType.Int(32, true), new ArrowType.Int(64, true),
                new ArrowType.Decimal(20, 0, 128));
    }

    @Test
    void splitsLargeResultsIntoBatches() throws Exception {
        byte[] bytes = encode("SELECT X AS n, -X AS m FROM SYSTEM_RANGE(0, 20000)", 8192);

        List<Integer> sizes = new ArrayList<>();
        long sum = 0;
        try (ArrowStreamReader reader = reader(bytes)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            long seen = 0;
            while (reader.loadNextBatch()) {
                sizes.add(root.getRowCount());
                for (int i = 0; i < root.getRowCount(); i++) {
                    assertThat(root.getVector("N").getObject(i)).isEqualTo(seen + i);
                    sum += (Long) root.getVector("M").getObject(i);
                }
                seen += root.getRowCount();
            }
        }
        assertThat(sizes).containsExactly(8192, 8192, 3617);
        assertThat(sum).isEqualTo(-20_000L * 20_001L / 2);
    }

    @Test
    void reportsRowsAndBatches() throws Exception {
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, 10);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT X FROM SYSTEM_RANGE(1, 25)")) {
            assertThat(encoder.encode(rows, OutputStream.nullOutputStream())).isEqualTo(new EncodeResult(25, 3));
        }
    }

    @Test
    void emptyResultStillCarriesTheSchema() throws Exception {
        byte[] bytes = encode("SELECT id, name FROM everything WHERE id < 0", 16);

        try (ArrowStreamReader reader = reader(bytes)) {
            assertThat(reader.getVectorSchemaRoot().getSchema().getFields()).extracting(Field::getName)
                    .containsExactly("ID", "NAME");
            assertThat(reader.loadNextBatch()).isFalse();
        }
    }

    @Test
    void namesColumnsByTheirLabels() throws Exception {
        assertThat(schema(encode("SELECT id AS \"orderId\", name AS \"customer\" FROM everything", 16)))
                .extracting(Field::getName).containsExactly("orderId", "customer");
    }

    @Test
    void rejectsUnsupportedTypesBeforeWritingAnything() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, 16);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, CAST(X'0102' AS VARBINARY(2)) AS payload"
                     + " FROM everything")) {
            assertThatThrownBy(() -> encoder.encode(rows, sink))
                    .isInstanceOf(UnsupportedRecordException.class)
                    .hasMessageContaining("Column PAYLOAD has SQL type VARBINARY")
                    .hasMessageContaining("Supported: BOOLEAN");
        }
        assertThat(sink.size()).isZero();
    }

    @Test
    void rejectsDecimalsWiderThanArrowAndSuggestsACast() {
        assertThatThrownBy(() -> encode("SELECT CAST(1 AS DECIMAL(50, 2)) AS wide", 16))
                .isInstanceOf(UnsupportedRecordException.class)
                .hasMessageContaining("Column WIDE has SQL type")
                .hasMessageContaining("(50, 2)")
                .hasMessageContaining("CAST(WIDE AS DECIMAL(38, 2))");
    }

    @Test
    void rejectsMultiBitColumns() {
        ResultSet rows = stub(List.of(new StubColumn("flags", Types.BIT, "BIT", 8, true)), 0);

        assertThatThrownBy(() -> encode(rows, 16))
                .isInstanceOf(UnsupportedRecordException.class)
                .hasMessageContaining("Column flags has SQL type BIT(8)");
    }

    @Test
    void passesDriverErrorsThroughUnchanged() {
        ResultSet rows = stub(List.of(new StubColumn("n", Types.INTEGER, "INTEGER", 10, true)), 1);

        assertThatThrownBy(() -> encode(rows, 16)).isInstanceOf(SQLException.class).hasMessage("getInt failed");
    }

    @Test
    void leavesTheResultSetOpenForItsOwner() throws Exception {
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, 16);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(EVERYTHING)) {
            encoder.encode(rows, OutputStream.nullOutputStream());

            assertThat(rows.isClosed()).isFalse();
            assertThat(statement.isClosed()).isFalse();
        }
    }

    private byte[] encode(String sql, int batchSize) throws IOException, SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return encode(rows, batchSize);
        }
    }

    private byte[] encode(ResultSet rows, int batchSize) throws IOException, SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(allocator, batchSize)) {
            encoder.encode(rows, out);
        }
        return out.toByteArray();
    }

    private ArrowStreamReader reader(byte[] bytes) {
        return new ArrowStreamReader(new ByteArrayInputStream(bytes), allocator);
    }

    private List<Field> schema(byte[] bytes) throws IOException {
        try (ArrowStreamReader reader = reader(bytes)) {
            return reader.getVectorSchemaRoot().getSchema().getFields();
        }
    }

    private static long micros(String offsetDateTime) {
        return Columns.micros(OffsetDateTime.parse(offsetDateTime).toInstant());
    }

    private static ResultSet stub(List<StubColumn> columns, int rows) {
        ResultSetMetaData metadata = (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(), new Class<?>[] {ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getColumnCount")) {
                        return columns.size();
                    }
                    StubColumn column = columns.get((Integer) args[0] - 1);
                    return switch (method.getName()) {
                        case "getColumnLabel", "getColumnName" -> column.label();
                        case "getColumnType" -> column.type();
                        case "getColumnTypeName" -> column.typeName();
                        case "getPrecision" -> column.precision();
                        case "getScale" -> 0;
                        case "isSigned" -> column.signed();
                        case "isNullable" -> ResultSetMetaData.columnNullable;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        int[] remaining = {rows};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetaData" -> metadata;
                    case "next" -> remaining[0]-- > 0;
                    default -> throw new SQLException(method.getName() + " failed");
                });
    }

    private static Field field(List<Field> fields, String name) {
        return fields.stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }

    private static ArrowType type(List<Field> fields, String name) {
        return field(fields, name).getType();
    }
}

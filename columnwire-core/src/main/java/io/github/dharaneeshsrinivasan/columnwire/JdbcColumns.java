package io.github.dharaneeshsrinivasan.columnwire;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;

final class JdbcColumns {

    static final List<String> SUPPORTED = List.of(
            "BOOLEAN", "BIT(1)", "TINYINT", "SMALLINT", "INTEGER", "BIGINT", "REAL", "FLOAT", "DOUBLE",
            "DECIMAL", "NUMERIC", "CHAR", "VARCHAR", "LONGVARCHAR", "NCHAR", "NVARCHAR", "LONGNVARCHAR",
            "DATE", "TIME", "TIMESTAMP", "TIMESTAMP_WITH_TIMEZONE", "NULL");

    static final int MAX_DECIMAL_PRECISION = 38;

    private JdbcColumns() {
    }

    static List<ColumnSpec<ResultSet>> of(ResultSetMetaData metadata) throws SQLException {
        int count = metadata.getColumnCount();
        List<ColumnSpec<ResultSet>> columns = new ArrayList<>(count);
        for (int c = 1; c <= count; c++) {
            columns.add(column(metadata, c));
        }
        return List.copyOf(columns);
    }

    private static ColumnSpec<ResultSet> column(ResultSetMetaData metadata, int c) throws SQLException {
        String name = name(metadata, c);
        boolean nullable = metadata.isNullable(c) != ResultSetMetaData.columnNoNulls;
        int type = metadata.getColumnType(c);
        return switch (type) {
            case Types.BOOLEAN -> bool(name, nullable, c);
            case Types.BIT -> {
                if (metadata.getPrecision(c) > 1) {
                    throw unsupported(metadata, c, name, sqlType(type) + "(" + metadata.getPrecision(c) + ")", "");
                }
                yield bool(name, nullable, c);
            }
            case Types.TINYINT -> metadata.isSigned(c) ? int8(name, nullable, c) : int16(name, nullable, c);
            case Types.SMALLINT -> metadata.isSigned(c) ? int16(name, nullable, c) : int32(name, nullable, c);
            case Types.INTEGER -> metadata.isSigned(c) ? int32(name, nullable, c) : int64(name, nullable, c);
            case Types.BIGINT -> metadata.isSigned(c) ? int64(name, nullable, c) : decimal(name, nullable, c, 20, 0);
            case Types.REAL -> float32(name, nullable, c);
            case Types.FLOAT, Types.DOUBLE -> float64(name, nullable, c);
            case Types.DECIMAL, Types.NUMERIC -> {
                int precision = metadata.getPrecision(c);
                int scale = metadata.getScale(c);
                if (precision < 1 || precision > MAX_DECIMAL_PRECISION) {
                    throw unsupported(metadata, c, name, sqlType(type) + "(" + precision + ", " + scale + ")",
                            ": Arrow decimals hold 1 to " + MAX_DECIMAL_PRECISION + " digits. Cast it in the query,"
                                    + " for example CAST(" + name + " AS DECIMAL(" + MAX_DECIMAL_PRECISION + ", "
                                    + Math.max(0, Math.min(scale, MAX_DECIMAL_PRECISION)) + "))");
                }
                yield decimal(name, nullable, c, precision, scale);
            }
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                    object(name, nullable, ArrowType.Utf8.INSTANCE, rs -> rs.getString(c),
                            (v, i, o) -> Columns.utf8((VarCharVector) v, i, (String) o));
            case Types.DATE -> object(name, nullable, new ArrowType.Date(DateUnit.DAY),
                    rs -> rs.getObject(c, LocalDate.class),
                    (v, i, o) -> ((DateDayVector) v).set(i, Math.toIntExact(((LocalDate) o).toEpochDay())));
            case Types.TIME -> object(name, nullable, new ArrowType.Time(TimeUnit.MICROSECOND, 64),
                    rs -> rs.getObject(c, LocalTime.class),
                    (v, i, o) -> ((TimeMicroVector) v).set(i, ((LocalTime) o).toNanoOfDay() / 1_000L));
            case Types.TIMESTAMP -> object(name, nullable, new ArrowType.Timestamp(TimeUnit.MICROSECOND, null),
                    rs -> rs.getObject(c, LocalDateTime.class),
                    (v, i, o) -> ((TimeStampMicroVector) v).set(i, Columns.micros((LocalDateTime) o)));
            case Types.TIMESTAMP_WITH_TIMEZONE -> object(name, nullable,
                    new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"), rs -> rs.getObject(c, OffsetDateTime.class),
                    (v, i, o) -> ((TimeStampMicroTZVector) v).set(i, Columns.micros(((OffsetDateTime) o).toInstant())));
            case Types.NULL -> spec(name, true, ArrowType.Null.INSTANCE, v -> (rs, i) -> {
            });
            default -> throw unsupported(metadata, c, name, sqlType(type), "");
        };
    }

    @FunctionalInterface
    private interface Reader {
        Object read(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface Setter {
        void set(FieldVector vector, int index, Object value);
    }

    private static ColumnSpec<ResultSet> spec(String name, boolean nullable, ArrowType type,
                                              Function<FieldVector, ColumnWriter<ResultSet>> binder) {
        return new ColumnSpec<>(name, new Field(name, new FieldType(nullable, type, null), null), binder, null);
    }

    private static ColumnSpec<ResultSet> object(String name, boolean nullable, ArrowType type, Reader reader,
                                                Setter setter) {
        return spec(name, nullable, type, v -> (rs, i) -> {
            Object value = reader.read(rs);
            if (value == null) {
                v.setNull(i);
            } else {
                setter.set(v, i, value);
            }
        });
    }

    private static ColumnSpec<ResultSet> decimal(String name, boolean nullable, int c, int precision, int scale) {
        return object(name, nullable, new ArrowType.Decimal(precision, scale, 128), rs -> rs.getBigDecimal(c),
                (v, i, o) -> ((DecimalVector) v).set(i, ((BigDecimal) o).setScale(scale)));
    }

    private static ColumnSpec<ResultSet> bool(String name, boolean nullable, int c) {
        return spec(name, nullable, ArrowType.Bool.INSTANCE, v -> {
            BitVector bv = (BitVector) v;
            return (rs, i) -> {
                boolean value = rs.getBoolean(c);
                if (rs.wasNull()) {
                    bv.setNull(i);
                } else {
                    bv.set(i, value ? 1 : 0);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> int8(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.Int(8, true), v -> {
            TinyIntVector tv = (TinyIntVector) v;
            return (rs, i) -> {
                byte value = rs.getByte(c);
                if (rs.wasNull()) {
                    tv.setNull(i);
                } else {
                    tv.set(i, value);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> int16(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.Int(16, true), v -> {
            SmallIntVector sv = (SmallIntVector) v;
            return (rs, i) -> {
                short value = rs.getShort(c);
                if (rs.wasNull()) {
                    sv.setNull(i);
                } else {
                    sv.set(i, value);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> int32(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.Int(32, true), v -> {
            IntVector iv = (IntVector) v;
            return (rs, i) -> {
                int value = rs.getInt(c);
                if (rs.wasNull()) {
                    iv.setNull(i);
                } else {
                    iv.set(i, value);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> int64(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.Int(64, true), v -> {
            BigIntVector lv = (BigIntVector) v;
            return (rs, i) -> {
                long value = rs.getLong(c);
                if (rs.wasNull()) {
                    lv.setNull(i);
                } else {
                    lv.set(i, value);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> float32(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE), v -> {
            Float4Vector fv = (Float4Vector) v;
            return (rs, i) -> {
                float value = rs.getFloat(c);
                if (rs.wasNull()) {
                    fv.setNull(i);
                } else {
                    fv.set(i, value);
                }
            };
        });
    }

    private static ColumnSpec<ResultSet> float64(String name, boolean nullable, int c) {
        return spec(name, nullable, new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE), v -> {
            Float8Vector dv = (Float8Vector) v;
            return (rs, i) -> {
                double value = rs.getDouble(c);
                if (rs.wasNull()) {
                    dv.setNull(i);
                } else {
                    dv.set(i, value);
                }
            };
        });
    }

    private static String name(ResultSetMetaData metadata, int c) throws SQLException {
        String label = metadata.getColumnLabel(c);
        return label == null || label.isEmpty() ? metadata.getColumnName(c) : label;
    }

    private static String sqlType(int type) {
        try {
            return JDBCType.valueOf(type).getName();
        } catch (IllegalArgumentException e) {
            return "type code " + type;
        }
    }

    private static UnsupportedRecordException unsupported(ResultSetMetaData metadata, int c, String name,
                                                          String sqlType, String detail) throws SQLException {
        String message = "Column " + name + " has SQL type " + sqlType + " (" + metadata.getColumnTypeName(c)
                + "), which Columnwire does not encode";
        return new UnsupportedRecordException(detail.isEmpty()
                ? message + ". Supported: " + String.join(", ", SUPPORTED)
                : message + detail);
    }
}

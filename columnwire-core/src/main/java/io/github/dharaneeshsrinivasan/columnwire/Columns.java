package io.github.dharaneeshsrinivasan.columnwire;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
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
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;

final class Columns {

    static final Set<String> SUPPORTED = Set.of(
            "boolean", "byte", "short", "int", "long", "float", "double", "char",
            "String", "CharSequence", "UUID", "BigDecimal", "BigInteger", "enum",
            "LocalDate", "LocalDateTime", "LocalTime", "Instant", "OffsetDateTime", "ZonedDateTime");

    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final ArrowType UTF8 = ArrowType.Utf8.INSTANCE;

    private Columns() {
    }

    static List<ColumnSpec> of(Class<? extends Record> type) {
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            throw new UnsupportedRecordException(type.getName() + " is not a record");
        }
        List<ColumnSpec> columns = new ArrayList<>(components.length);
        for (RecordComponent component : components) {
            Method accessor = component.getAccessor();
            if (ignored(accessor)) {
                continue;
            }
            String name = jsonName(accessor, component.getName());
            columns.add(column(type, component, name, columns.size(), handle(type, accessor)));
        }
        if (columns.isEmpty()) {
            throw new UnsupportedRecordException(type.getName() + " has no encodable components");
        }
        return List.copyOf(columns);
    }

    private static ColumnSpec column(Class<?> owner, RecordComponent component, String name, long id, MethodHandle h) {
        Class<?> t = component.getType();
        if (t == boolean.class) {
            MethodHandle g = h.asType(MethodType.methodType(boolean.class, Record.class));
            return plain(name, false, ArrowType.Bool.INSTANCE, v -> {
                BitVector bv = (BitVector) v;
                return (row, i) -> bv.set(i, (boolean) g.invokeExact(row) ? 1 : 0);
            });
        }
        if (t == byte.class) {
            MethodHandle g = h.asType(MethodType.methodType(byte.class, Record.class));
            return plain(name, false, new ArrowType.Int(8, true), v -> {
                TinyIntVector tv = (TinyIntVector) v;
                return (row, i) -> tv.set(i, (byte) g.invokeExact(row));
            });
        }
        if (t == short.class) {
            MethodHandle g = h.asType(MethodType.methodType(short.class, Record.class));
            return plain(name, false, new ArrowType.Int(16, true), v -> {
                SmallIntVector sv = (SmallIntVector) v;
                return (row, i) -> sv.set(i, (short) g.invokeExact(row));
            });
        }
        if (t == int.class) {
            MethodHandle g = h.asType(MethodType.methodType(int.class, Record.class));
            return plain(name, false, new ArrowType.Int(32, true), v -> {
                IntVector iv = (IntVector) v;
                return (row, i) -> iv.set(i, (int) g.invokeExact(row));
            });
        }
        if (t == long.class) {
            MethodHandle g = h.asType(MethodType.methodType(long.class, Record.class));
            return plain(name, false, new ArrowType.Int(64, true), v -> {
                BigIntVector lv = (BigIntVector) v;
                return (row, i) -> lv.set(i, (long) g.invokeExact(row));
            });
        }
        if (t == float.class) {
            MethodHandle g = h.asType(MethodType.methodType(float.class, Record.class));
            return plain(name, false, new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE), v -> {
                Float4Vector fv = (Float4Vector) v;
                return (row, i) -> fv.set(i, (float) g.invokeExact(row));
            });
        }
        if (t == double.class) {
            MethodHandle g = h.asType(MethodType.methodType(double.class, Record.class));
            return plain(name, false, new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE), v -> {
                Float8Vector dv = (Float8Vector) v;
                return (row, i) -> dv.set(i, (double) g.invokeExact(row));
            });
        }
        if (t == char.class) {
            MethodHandle g = h.asType(MethodType.methodType(char.class, Record.class));
            return plain(name, false, UTF8, v -> {
                VarCharVector sv = (VarCharVector) v;
                return (row, i) -> utf8(sv, i, String.valueOf((char) g.invokeExact(row)));
            });
        }
        MethodHandle g = h.asType(MethodType.methodType(Object.class, Record.class));
        if (t.isEnum()) {
            return enumColumn(name, id, t, g);
        }
        if (t == Boolean.class) {
            return boxed(name, ArrowType.Bool.INSTANCE, g, (v, i, o) -> ((BitVector) v).set(i, (Boolean) o ? 1 : 0));
        }
        if (t == Byte.class) {
            return boxed(name, new ArrowType.Int(8, true), g, (v, i, o) -> ((TinyIntVector) v).set(i, (Byte) o));
        }
        if (t == Short.class) {
            return boxed(name, new ArrowType.Int(16, true), g, (v, i, o) -> ((SmallIntVector) v).set(i, (Short) o));
        }
        if (t == Integer.class) {
            return boxed(name, new ArrowType.Int(32, true), g, (v, i, o) -> ((IntVector) v).set(i, (Integer) o));
        }
        if (t == Long.class) {
            return boxed(name, new ArrowType.Int(64, true), g, (v, i, o) -> ((BigIntVector) v).set(i, (Long) o));
        }
        if (t == Float.class) {
            return boxed(name, new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE), g,
                    (v, i, o) -> ((Float4Vector) v).set(i, (Float) o));
        }
        if (t == Double.class) {
            return boxed(name, new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE), g,
                    (v, i, o) -> ((Float8Vector) v).set(i, (Double) o));
        }
        if (t == String.class || CharSequence.class.isAssignableFrom(t) || t == Character.class
                || t == UUID.class || t == BigInteger.class) {
            return boxed(name, UTF8, g, (v, i, o) -> utf8((VarCharVector) v, i, o.toString()));
        }
        if (t == BigDecimal.class) {
            return boxed(name, UTF8, g, (v, i, o) -> utf8((VarCharVector) v, i, ((BigDecimal) o).toPlainString()));
        }
        if (t == LocalDate.class) {
            return boxed(name, new ArrowType.Date(DateUnit.DAY), g,
                    (v, i, o) -> ((DateDayVector) v).set(i, Math.toIntExact(((LocalDate) o).toEpochDay())));
        }
        if (t == LocalDateTime.class) {
            return boxed(name, new ArrowType.Timestamp(TimeUnit.MICROSECOND, null), g,
                    (v, i, o) -> ((TimeStampMicroVector) v).set(i, micros((LocalDateTime) o)));
        }
        if (t == LocalTime.class) {
            return boxed(name, new ArrowType.Time(TimeUnit.MICROSECOND, 64), g,
                    (v, i, o) -> ((TimeMicroVector) v).set(i, ((LocalTime) o).toNanoOfDay() / 1_000L));
        }
        if (t == Instant.class) {
            return boxed(name, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"), g,
                    (v, i, o) -> ((TimeStampMicroTZVector) v).set(i, micros((Instant) o)));
        }
        if (t == OffsetDateTime.class) {
            return boxed(name, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"), g,
                    (v, i, o) -> ((TimeStampMicroTZVector) v).set(i, micros(((OffsetDateTime) o).toInstant())));
        }
        if (t == ZonedDateTime.class) {
            return boxed(name, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"), g,
                    (v, i, o) -> ((TimeStampMicroTZVector) v).set(i, micros(((ZonedDateTime) o).toInstant())));
        }
        throw new UnsupportedRecordException(owner.getName() + "." + component.getName() + " has type "
                + t.getName() + ", which Columnwire does not encode. Supported: " + String.join(", ", SUPPORTED));
    }

    private static ColumnSpec enumColumn(String name, long id, Class<?> t, MethodHandle g) {
        Object[] constants = t.getEnumConstants();
        List<String> values = new ArrayList<>(constants.length);
        for (Object c : constants) {
            values.add(((Enum<?>) c).name());
        }
        ArrowType.Int index = constants.length <= Byte.MAX_VALUE ? new ArrowType.Int(8, true)
                : constants.length <= Short.MAX_VALUE ? new ArrowType.Int(16, true) : new ArrowType.Int(32, true);
        DictionaryEncoding encoding = new DictionaryEncoding(id, false, index);
        Field field = new Field(name, new FieldType(true, index, encoding), null);
        Function<FieldVector, ColumnWriter> binder = switch (index.getBitWidth()) {
            case 8 -> v -> nullable(v, g, (vec, i, o) -> ((TinyIntVector) vec).set(i, (byte) ((Enum<?>) o).ordinal()));
            case 16 -> v -> nullable(v, g, (vec, i, o) -> ((SmallIntVector) vec).set(i, (short) ((Enum<?>) o).ordinal()));
            default -> v -> nullable(v, g, (vec, i, o) -> ((IntVector) vec).set(i, ((Enum<?>) o).ordinal()));
        };
        return new ColumnSpec(name, field, binder, List.copyOf(values));
    }

    @FunctionalInterface
    private interface Setter {
        void set(FieldVector vector, int index, Object value);
    }

    private static ColumnSpec plain(String name, boolean nullable, ArrowType type,
                                    Function<FieldVector, ColumnWriter> binder) {
        return new ColumnSpec(name, new Field(name, new FieldType(nullable, type, null), null), binder, null);
    }

    private static ColumnSpec boxed(String name, ArrowType type, MethodHandle g, Setter setter) {
        return plain(name, true, type, v -> nullable(v, g, setter));
    }

    private static ColumnWriter nullable(FieldVector vector, MethodHandle g, Setter setter) {
        return (row, i) -> {
            Object value = (Object) g.invokeExact(row);
            if (value == null) {
                vector.setNull(i);
            } else {
                setter.set(vector, i, value);
            }
        };
    }

    private static void utf8(VarCharVector vector, int index, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        vector.setSafe(index, bytes, 0, bytes.length);
    }

    private static long micros(LocalDateTime t) {
        return t.toEpochSecond(ZoneOffset.UTC) * 1_000_000L + t.getNano() / 1_000L;
    }

    private static long micros(Instant t) {
        return t.getEpochSecond() * 1_000_000L + t.getNano() / 1_000L;
    }

    private static MethodHandle handle(Class<?> type, Method accessor) {
        try {
            accessor.setAccessible(true);
            return MethodHandles.lookup().unreflect(accessor);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new UnsupportedRecordException("Cannot read " + type.getName() + "." + accessor.getName()
                    + ": " + e.getMessage());
        }
    }

    private static boolean ignored(Method accessor) {
        Object value = annotationValue(accessor, JSON_IGNORE);
        return Boolean.TRUE.equals(value);
    }

    private static String jsonName(Method accessor, String fallback) {
        Object value = annotationValue(accessor, JSON_PROPERTY);
        return value instanceof String s && !s.isEmpty() ? s : fallback;
    }

    private static Object annotationValue(Method accessor, String annotationType) {
        for (var annotation : accessor.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationType)) {
                try {
                    return annotation.annotationType().getMethod("value").invoke(annotation);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

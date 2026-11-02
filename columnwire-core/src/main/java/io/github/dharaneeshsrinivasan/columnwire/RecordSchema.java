package io.github.dharaneeshsrinivasan.columnwire;

import java.util.ArrayList;
import java.util.List;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

public final class RecordSchema<T extends Record> {

    private static final ClassValue<Object> CACHE = new ClassValue<>() {
        @Override
        protected Object computeValue(Class<?> type) {
            if (!type.isRecord()) {
                return new UnsupportedRecordException(type.getName() + " is not a record");
            }
            try {
                return new RecordSchema<>(type.asSubclass(Record.class));
            } catch (UnsupportedRecordException e) {
                return e;
            }
        }
    };

    private final Class<T> type;
    private final List<ColumnSpec> columns;
    private final Schema schema;

    private RecordSchema(Class<T> type) {
        this.type = type;
        this.columns = Columns.of(type);
        List<Field> fields = new ArrayList<>(columns.size());
        for (ColumnSpec column : columns) {
            fields.add(column.field());
        }
        this.schema = new Schema(fields);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Record> RecordSchema<T> of(Class<T> type) {
        Object cached = CACHE.get(type);
        if (cached instanceof UnsupportedRecordException e) {
            throw new UnsupportedRecordException(e.getMessage());
        }
        return (RecordSchema<T>) cached;
    }

    public static boolean supports(Class<?> type) {
        return CACHE.get(type) instanceof RecordSchema<?>;
    }

    public Class<T> type() {
        return type;
    }

    public Schema arrowSchema() {
        return schema;
    }

    List<ColumnSpec> columns() {
        return columns;
    }
}

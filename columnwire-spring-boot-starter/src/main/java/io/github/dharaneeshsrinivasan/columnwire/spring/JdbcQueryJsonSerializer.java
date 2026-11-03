package io.github.dharaneeshsrinivasan.columnwire.spring;

import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

final class JdbcQueryJsonSerializer extends StdSerializer<JdbcQuery> {

    JdbcQueryJsonSerializer() {
        super(JdbcQuery.class);
    }

    @Override
    public void serialize(JdbcQuery query, JsonGenerator generator, SerializationContext context) {
        query.query(rows -> {
            ColumnMapRowMapper mapper = new ColumnMapRowMapper();
            generator.writeStartArray();
            int row = 0;
            while (rows.next()) {
                Map<String, Object> values = mapper.mapRow(rows, row++);
                context.writeValue(generator, values);
            }
            generator.writeEndArray();
            return null;
        });
    }
}

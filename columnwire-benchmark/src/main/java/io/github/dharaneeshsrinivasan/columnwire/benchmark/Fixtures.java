package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.json.JsonMapper;

public final class Fixtures {

    static final String JDBC_SQL = "SELECT id, sku, region, status, quantity, unit_price, total, order_date,"
            + " created_at, customer, priority, CAST(-total AS DECIMAL(12, 2)) AS refund,"
            + " CAST(-quantity AS TINYINT) AS tiny, CAST(quantity * 100 AS SMALLINT) AS small,"
            + " CAST(unit_price AS REAL) AS ratio,"
            + " CAST(total AS DOUBLE PRECISION) AS approx, CAST(created_at AS TIME(6)) AS clock,"
            + " created_at AT TIME ZONE '+05:30' AS created_zoned, CAST(region AS CHAR(8)) AS code,"
            + " NULL AS nothing FROM orders ORDER BY id";

    private Fixtures() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        List<Order> orders = new ArrayList<>(Orders.generate(63));
        orders.add(new Order(42, "SKU-NULLS", null, null, 0, 0, 0, null, null, null, false));
        Files.write(dir.resolve("orders.json"), JsonMapper.builder().build().writeValueAsBytes(orders));
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(20);
             OutputStream out = Files.newOutputStream(dir.resolve("orders.arrow"))) {
            encoder.encode(Order.class, orders, out);
        }
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        String url = OrderTable.url("fixtures");
        OrderTable.create(url, orders);
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(url, true));
        Files.write(dir.resolve("jdbc.json"),
                JsonMapper.builder().build().writeValueAsBytes(jdbc.queryForList(JDBC_SQL)));
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(20);
             OutputStream out = Files.newOutputStream(dir.resolve("jdbc.arrow"))) {
            OrderTable.encode(jdbc, JDBC_SQL, encoder, out);
        }
    }
}

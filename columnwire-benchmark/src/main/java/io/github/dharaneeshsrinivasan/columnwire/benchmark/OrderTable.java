package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import io.github.dharaneeshsrinivasan.columnwire.EncodeResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

public final class OrderTable {

    public static final String SELECT = "SELECT * FROM orders ORDER BY id";

    private OrderTable() {
    }

    public static String url(String name) {
        return "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;LAZY_QUERY_EXECUTION=TRUE";
    }

    public static void create(String url, List<Order> orders) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS orders");
            statement.execute("CREATE TABLE orders (id BIGINT NOT NULL PRIMARY KEY, sku VARCHAR(16) NOT NULL,"
                    + " region VARCHAR(8), status VARCHAR(9), quantity INTEGER NOT NULL,"
                    + " unit_price DECIMAL(10, 2) NOT NULL, total DECIMAL(12, 2) NOT NULL, order_date DATE,"
                    + " created_at TIMESTAMP(6), customer VARCHAR(32), priority BOOLEAN NOT NULL)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int pending = 0;
                for (Order order : orders) {
                    insert.setLong(1, order.id());
                    insert.setString(2, order.sku());
                    insert.setString(3, order.region() == null ? null : order.region().name());
                    insert.setString(4, order.status() == null ? null : order.status().name());
                    insert.setInt(5, order.quantity());
                    insert.setBigDecimal(6, money(order.unitPrice()));
                    insert.setBigDecimal(7, money(order.total()));
                    insert.setObject(8, order.orderDate(), Types.DATE);
                    insert.setObject(9, order.createdAt() == null ? null
                            : LocalDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC), Types.TIMESTAMP);
                    insert.setString(10, order.customer());
                    insert.setBoolean(11, order.priority());
                    insert.addBatch();
                    if (++pending == 10_000) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
                insert.executeBatch();
            }
        }
    }

    public static EncodeResult encode(JdbcTemplate jdbc, String sql, ArrowRecordEncoder encoder, OutputStream out) {
        return jdbc.query(sql, (ResultSetExtractor<EncodeResult>) rows -> {
            try {
                return encoder.encode(rows, out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.UNNECESSARY);
    }
}

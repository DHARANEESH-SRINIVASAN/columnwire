package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

public final class Fixtures {

    private Fixtures() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        List<Order> orders = new ArrayList<>(Orders.generate(63));
        orders.add(new Order(42, "SKU-NULLS", null, null, 0, 0, 0, null, null, null, false));
        Files.write(dir.resolve("orders.json"), JsonMapper.builder().build().writeValueAsBytes(orders));
        try (ArrowRecordEncoder encoder = new ArrowRecordEncoder(20);
             OutputStream out = Files.newOutputStream(dir.resolve("orders.arrow"))) {
            encoder.encode(Order.class, orders, out);
        }
    }
}

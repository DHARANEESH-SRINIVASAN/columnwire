package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.json.JsonMapper;

public final class PayloadSizes {

    private PayloadSizes() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/payloads");
        Files.createDirectories(out);
        JsonMapper json = JsonMapper.builder().build();
        System.out.println("rows,json_bytes,arrow_bytes,json_gzip_bytes,arrow_gzip_bytes");
        try (ArrowRecordEncoder arrow = new ArrowRecordEncoder()) {
            for (int rows : new int[] {10_000, 100_000, 1_000_000}) {
                List<Order> orders = Orders.generate(rows);
                byte[] jsonBytes = json.writeValueAsBytes(orders);
                ByteArrayOutputStream arrowOut = new ByteArrayOutputStream();
                arrow.encode(Order.class, orders, arrowOut);
                byte[] arrowBytes = arrowOut.toByteArray();
                Files.write(out.resolve("orders-" + rows + ".json"), jsonBytes);
                Files.write(out.resolve("orders-" + rows + ".arrow"), arrowBytes);
                System.out.println(rows + "," + jsonBytes.length + "," + arrowBytes.length + ","
                        + gzip(jsonBytes) + "," + gzip(arrowBytes));
            }
        }
    }

    private static int gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.size();
    }
}

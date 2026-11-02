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
        int[] sizes = args.length > 1 ? rowCounts(args) : new int[] {10_000, 100_000, 1_000_000};
        Files.createDirectories(out);
        JsonMapper json = JsonMapper.builder().build();
        System.out.println("rows,json_bytes,arrow_bytes,json_gzip_bytes,arrow_gzip_bytes");
        try (ArrowRecordEncoder arrow = new ArrowRecordEncoder()) {
            for (int rows : sizes) {
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

    private static int[] rowCounts(String[] args) {
        int[] sizes = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            try {
                sizes[i - 1] = Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Row counts must be whole numbers, got '" + args[i]
                        + "'. Usage: PayloadSizes <output dir> [rows...]", e);
            }
        }
        return sizes;
    }

    private static int gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.size();
    }
}

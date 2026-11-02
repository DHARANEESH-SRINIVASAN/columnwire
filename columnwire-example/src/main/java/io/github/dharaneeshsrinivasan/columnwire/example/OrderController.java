package io.github.dharaneeshsrinivasan.columnwire.example;

import java.time.Instant;
import java.time.LocalDate;
import java.util.SplittableRandom;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private static final int MAX_ROWS = 2_000_000;
    private static final String[] CUSTOMERS = {"Acme Corp", "Globex", "Initech", "Umbrella", "Stark Industries",
            "Wayne Enterprises", "Hooli", "Soylent", "Tyrell", "Cyberdyne"};
    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final Instant EPOCH = Instant.parse("2024-01-01T00:00:00Z");

    @GetMapping("/api/orders")
    Stream<Order> orders(@RequestParam(defaultValue = "100000") int rows) {
        SplittableRandom random = new SplittableRandom(42);
        Order.Region[] regions = Order.Region.values();
        Order.Status[] statuses = Order.Status.values();
        return LongStream.range(0, Math.min(Math.max(rows, 0), MAX_ROWS)).mapToObj(i -> {
            int quantity = 1 + random.nextInt(50);
            double unitPrice = Math.round(random.nextDouble(1, 500) * 100) / 100.0;
            return new Order(1_000_000L + i, "SKU-" + (10_000 + random.nextInt(90_000)),
                    regions[random.nextInt(regions.length)], statuses[random.nextInt(statuses.length)], quantity,
                    unitPrice, Math.round(quantity * unitPrice * 100) / 100.0, START.plusDays(random.nextInt(1000)),
                    EPOCH.plusSeconds(random.nextLong(86_400L * 1000)).plusNanos(random.nextInt(1_000_000) * 1000L),
                    random.nextInt(10) == 0 ? null : CUSTOMERS[random.nextInt(CUSTOMERS.length)],
                    random.nextInt(8) == 0);
        });
    }
}

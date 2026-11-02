package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class Orders {

    private static final String[] CUSTOMERS = {"Acme Corp", "Globex", "Initech", "Umbrella", "Stark Industries",
            "Wayne Enterprises", "Hooli", "Soylent", "Tyrell", "Cyberdyne"};

    private Orders() {
    }

    public static List<Order> generate(int count) {
        SplittableRandom random = new SplittableRandom(42);
        LocalDate start = LocalDate.of(2024, 1, 1);
        Instant epoch = Instant.parse("2024-01-01T00:00:00Z");
        Order.Region[] regions = Order.Region.values();
        Order.Status[] statuses = Order.Status.values();
        List<Order> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int quantity = 1 + random.nextInt(50);
            double unitPrice = Math.round(random.nextDouble(1, 500) * 100) / 100.0;
            orders.add(new Order(
                    1_000_000L + i,
                    "SKU-" + (10_000 + random.nextInt(90_000)),
                    regions[random.nextInt(regions.length)],
                    statuses[random.nextInt(statuses.length)],
                    quantity,
                    unitPrice,
                    Math.round(quantity * unitPrice * 100) / 100.0,
                    start.plusDays(random.nextInt(1000)),
                    epoch.plusSeconds(random.nextLong(86_400L * 1000)).plusNanos(random.nextInt(1_000_000) * 1000L),
                    random.nextInt(10) == 0 ? null : CUSTOMERS[random.nextInt(CUSTOMERS.length)],
                    random.nextInt(8) == 0));
        }
        return orders;
    }
}

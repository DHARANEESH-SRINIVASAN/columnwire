package io.github.dharaneeshsrinivasan.columnwire.example;

import java.time.Instant;
import java.time.LocalDate;

public record Order(long id, String sku, Region region, Status status, int quantity, double unitPrice,
                    double total, LocalDate orderDate, Instant createdAt, String customer, boolean priority) {

    public enum Region { NORTH, SOUTH, EAST, WEST, CENTRAL }

    public enum Status { PENDING, PAID, PACKED, SHIPPED, DELIVERED, RETURNED }
}

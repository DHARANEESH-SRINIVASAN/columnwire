package io.github.dharaneeshsrinivasan.columnwire.spring;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class TestApplication {

    public enum Status { OPEN, SHIPPED }

    public record Order(long id, String sku, Status status, double total, LocalDate day) {
    }

    public record Bag(String name, List<String> items) {
    }

    static final List<Order> ORDERS = List.of(
            new Order(1, "SKU-1", Status.OPEN, 10.5, LocalDate.of(2026, 1, 1)),
            new Order(2, "SKU-2", Status.SHIPPED, 20.25, LocalDate.of(2026, 1, 2)),
            new Order(3, null, null, 0, null));

    @RestController
    static class OrdersController {

        @GetMapping("/orders")
        List<Order> list() {
            return ORDERS;
        }

        @GetMapping("/orders/stream")
        Stream<Order> stream() {
            return ORDERS.stream();
        }

        @GetMapping("/orders/array")
        Order[] array() {
            return ORDERS.toArray(Order[]::new);
        }

        @GetMapping("/orders/entity")
        ResponseEntity<List<Order>> entity() {
            return ResponseEntity.ok().header("X-Total", "3").body(ORDERS);
        }

        @GetMapping("/orders/one")
        Order one() {
            return ORDERS.get(0);
        }

        @GetMapping("/orders/big")
        Stream<Order> big(@RequestParam int n) {
            return IntStream.range(0, n).mapToObj(i -> new Order(i, "SKU-" + i, Status.values()[i % 2], i, null));
        }

        @GetMapping("/bags")
        List<Bag> bags() {
            return List.of(new Bag("a", List.of("x")));
        }
    }
}

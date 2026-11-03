package io.github.dharaneeshsrinivasan.columnwire.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "columnwire.batch-size=1000")
@ExtendWith(OutputCaptureExtension.class)
class ColumnwireJdbcTest {

    private static final String ARROW = "application/vnd.apache.arrow.stream";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTracker tracker;

    @AfterEach
    void releasesEveryConnectionStatementAndResultSet() {
        await().atMost(Duration.ofSeconds(10)).until(() -> tracker.open() == 0);
    }

    @Test
    void servesQueryResultsAsArrowWhenTheClientAsksForIt() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders", ARROW);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue(ARROW);
        assertThat(response.headers().allValues("Vary")).anySatisfy(v -> assertThat(v).contains("Accept"));
        ArrowResponses.Decoded decoded = ArrowResponses.decode(response.body());
        assertThat(decoded.rows()).hasSize(3);
        assertThat(decoded.rows().get(0)).containsEntry("ID", 1L).containsEntry("SKU", "SKU-1")
                .containsEntry("QUANTITY", 3).containsEntry("AMOUNT", new BigDecimal("1234.50"))
                .containsEntry("SHIPPED", true)
                .containsEntry("ORDERED_ON", (int) LocalDate.of(2026, 1, 1).toEpochDay())
                .containsEntry("CREATED_AT", LocalDateTime.of(2026, 1, 1, 9, 30, 0, 123_456_000));
        assertThat(decoded.rows().get(1)).containsEntry("AMOUNT", new BigDecimal("-0.05"))
                .containsEntry("CREATED_AT", LocalDateTime.of(2026, 1, 2, 23, 59, 59, 999_999_000));
        assertThat(decoded.rows().get(2)).containsEntry("ID", 3L).containsEntry("SKU", null)
                .containsEntry("QUANTITY", null).containsEntry("AMOUNT", null).containsEntry("SHIPPED", null)
                .containsEntry("ORDERED_ON", null).containsEntry("CREATED_AT", null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"*/*", "application/json", "application/json, text/plain, */*",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"})
    void sendsEveryoneElseTheSameJsonAsQueryForList(String accept) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders", accept);
        HttpResponse<byte[]> baseline = ArrowResponses.get(port, "/jdbc/orders/list", accept);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/json"));
        String json = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(json).startsWith("[{\"ID\":1,").isEqualTo(new String(baseline.body(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jdbc/orders", "/jdbc/orders/entity"})
    void marksJsonResponsesAsVaryingOnAcceptToo(String path) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, path, "application/json");

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/json"));
        assertThat(String.join(",", response.headers().allValues("Vary"))).contains("Accept");
    }

    @Test
    void addsVaryOnlyOnce() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders", ARROW);

        assertThat(String.join(",", response.headers().allValues("Vary")).split("Accept", -1)).hasSize(2);
    }

    @Test
    void honoursQualityValues() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders", "application/json;q=0.5, " + ARROW);

        assertThat(response.headers().firstValue("Content-Type")).hasValue(ARROW);
    }

    @Test
    void bindsQueryArguments() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders/since?id=2", ARROW);

        assertThat(ArrowResponses.decode(response.body()).rows()).extracting(r -> r.get("ID"))
                .containsExactly(2L, 3L);
    }

    @Test
    void keepsResponseEntityHeaders() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/orders/entity", ARROW);

        assertThat(response.headers().firstValue("X-Total")).hasValue("3");
        assertThat(ArrowResponses.decode(response.body()).rows()).hasSize(3);
    }

    @Test
    void streamsLargeResultsInConfiguredBatches() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/range?n=50000", ARROW);

        ArrowResponses.Decoded decoded = ArrowResponses.decode(response.body());
        assertThat(decoded.rows()).hasSize(50_000);
        assertThat(decoded.batches()).isEqualTo(50);
        assertThat(decoded.rows().get(49_999)).containsEntry("ID", 50_000L).containsEntry("SKU", "SKU-50000");
    }

    @Test
    void failsBeforeTheFirstByteWhenArrowCannotEncodeAColumn(CapturedOutput output) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/jdbc/unsupported", ARROW);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).isNotEqualTo(ARROW);
        assertThat(output).contains("Column PAYLOAD has SQL type VARBINARY (BINARY VARYING)");
        assertThat(ArrowResponses.get(port, "/jdbc/unsupported", "application/json").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {ARROW, "application/json"})
    void closesTheResultSetAndConnectionWhenTheClientDisconnects(String accept) throws Exception {
        long total = 2_000_000;
        long readBefore = tracker.rowsRead();
        long openedBefore = tracker.opened();
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /jdbc/range?n=" + total + " HTTP/1.1\r\nHost: localhost\r\nAccept: " + accept
                    + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] head = in.readNBytes(64 * 1024);
            assertThat(new String(head, 0, 12, StandardCharsets.US_ASCII)).isEqualTo("HTTP/1.1 200");
            socket.setSoLinger(true, 0);
        }

        await().atMost(Duration.ofSeconds(30)).until(() -> tracker.opened() > openedBefore && tracker.open() == 0);
        assertThat(tracker.opened() - openedBefore).isEqualTo(3);
        assertThat(tracker.rowsRead() - readBefore).isPositive().isLessThan(total);
    }
}

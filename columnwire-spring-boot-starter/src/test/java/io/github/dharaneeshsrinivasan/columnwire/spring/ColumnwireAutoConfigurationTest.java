package io.github.dharaneeshsrinivasan.columnwire.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "columnwire.batch-size=1000")
class ColumnwireAutoConfigurationTest {

    private static final String ARROW = "application/vnd.apache.arrow.stream";

    @LocalServerPort
    int port;

    @Test
    void servesArrowWhenTheClientAsksForIt() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders", ARROW);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue(ARROW);
        assertThat(response.headers().allValues("Vary")).anySatisfy(v -> assertThat(v).contains("Accept"));
        ArrowResponses.Decoded decoded = ArrowResponses.decode(response.body());
        assertThat(decoded.rows()).hasSize(3);
        assertThat(decoded.rows().get(0)).containsEntry("id", 1L).containsEntry("sku", "SKU-1")
                .containsEntry("status", "OPEN").containsEntry("total", 10.5)
                .containsEntry("day", (int) LocalDate.of(2026, 1, 1).toEpochDay());
        assertThat(decoded.rows().get(1)).containsEntry("status", "SHIPPED");
        assertThat(decoded.rows().get(2)).containsEntry("sku", null).containsEntry("status", null)
                .containsEntry("day", null);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"*/*", "application/json", "application/json, text/plain, */*",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"})
    void keepsServingJsonToEveryoneElse(String accept) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders", accept);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/json"));
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).startsWith("[{\"id\":1,");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/orders", "/orders/entity"})
    void marksJsonResponsesAsVaryingOnAcceptToo(String path) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, path, "application/json");

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/json"));
        assertThat(String.join(",", response.headers().allValues("Vary"))).contains("Accept");
    }

    @Test
    void addsVaryOnlyOnce() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders", ARROW);

        assertThat(String.join(",", response.headers().allValues("Vary")).split("Accept", -1)).hasSize(2);
    }

    @Test
    void leavesEndpointsArrowCannotServeAlone() throws Exception {
        assertThat(ArrowResponses.get(port, "/bags", "application/json").headers().allValues("Vary"))
                .noneSatisfy(v -> assertThat(v).contains("Accept"));
    }

    @Test
    void honoursQualityValues() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders",
                "application/json;q=0.5, " + ARROW);

        assertThat(response.headers().firstValue("Content-Type")).hasValue(ARROW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/orders/stream", "/orders/array", "/orders/entity"})
    void encodesStreamsArraysAndResponseEntities(String path) throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, path, ARROW);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(ArrowResponses.decode(response.body()).rows()).extracting(r -> r.get("id"))
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void keepsResponseEntityHeaders() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders/entity", ARROW);

        assertThat(response.headers().firstValue("X-Total")).hasValue("3");
    }

    @Test
    void streamsLargeResultsInConfiguredBatches() throws Exception {
        HttpResponse<byte[]> response = ArrowResponses.get(port, "/orders/big?n=50000", ARROW);

        ArrowResponses.Decoded decoded = ArrowResponses.decode(response.body());
        assertThat(decoded.rows()).hasSize(50_000);
        assertThat(decoded.batches()).isEqualTo(50);
        assertThat(decoded.rows().get(49_999)).containsEntry("id", 49_999L).containsEntry("status", "SHIPPED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bags", "/orders/one"})
    void answers406WhenArrowCannotRepresentTheBody(String path) throws Exception {
        assertThat(ArrowResponses.get(port, path, ARROW).statusCode()).isEqualTo(406);
        assertThat(ArrowResponses.get(port, path, "application/json").statusCode()).isEqualTo(200);
    }
}

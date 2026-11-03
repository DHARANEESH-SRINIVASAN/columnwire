package io.github.dharaneeshsrinivasan.columnwire.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "columnwire.enabled=false")
class ColumnwireDisabledTest {

    @LocalServerPort
    int port;

    @Test
    void doesNothingWhenDisabled() throws Exception {
        assertThat(ArrowResponses.get(port, "/orders", "application/vnd.apache.arrow.stream").statusCode())
                .isEqualTo(406);
        assertThat(ArrowResponses.get(port, "/orders", null).statusCode()).isEqualTo(200);
    }

    @Test
    void leavesQueriesOnJsonWhenDisabled() throws Exception {
        assertThat(ArrowResponses.get(port, "/jdbc/orders", "application/vnd.apache.arrow.stream").statusCode())
                .isEqualTo(406);
        HttpResponse<byte[]> json = ArrowResponses.get(port, "/jdbc/orders", null);
        assertThat(json.statusCode()).isEqualTo(200);
        assertThat(new String(json.body(), StandardCharsets.UTF_8)).startsWith("[{\"ID\":1,");
    }
}

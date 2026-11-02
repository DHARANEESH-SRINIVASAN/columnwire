package io.github.dharaneeshsrinivasan.columnwire.spring;

import static org.assertj.core.api.Assertions.assertThat;

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
}

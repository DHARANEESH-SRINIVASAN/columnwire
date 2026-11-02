package io.github.dharaneeshsrinivasan.columnwire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ArrowRuntimeTest {

    public static final class VerifyMain {

        public static void main(String[] args) {
            ArrowRuntime.verify();
        }
    }

    @Test
    void passesOnAConfiguredJvm() {
        assertThatCode(ArrowRuntime::verify).doesNotThrowAnyException();
    }

    @Test
    void namesTheMissingFlagOnAJvmWithoutIt() throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(List.of(java, "-cp", System.getProperty("java.class.path"),
                VerifyMain.class.getName())).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();

        assertThat(process.exitValue()).isNotZero();
        assertThat(output).contains("Apache Arrow cannot reach off-heap memory").contains(ArrowRuntime.JVM_FLAGS);
    }
}

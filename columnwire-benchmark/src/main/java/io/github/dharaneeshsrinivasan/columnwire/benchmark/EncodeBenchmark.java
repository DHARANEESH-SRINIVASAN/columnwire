package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.databind.json.JsonMapper;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 6, time = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g", "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"})
public class EncodeBenchmark {

    @Param({"10000", "100000", "1000000"})
    public int rows;

    private List<Order> orders;
    private JsonMapper json;
    private ArrowRecordEncoder arrow;
    private ByteArrayOutputStream sink;

    @Setup(Level.Trial)
    public void setUp() {
        orders = Orders.generate(rows);
        json = JsonMapper.builder().build();
        arrow = new ArrowRecordEncoder();
        sink = new ByteArrayOutputStream(rows * 300);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        arrow.close();
    }

    @Benchmark
    public int jacksonJson() {
        sink.reset();
        json.writeValue(sink, orders);
        return sink.size();
    }

    @Benchmark
    public int columnwireArrow() throws IOException {
        sink.reset();
        arrow.encode(Order.class, orders, sink);
        return sink.size();
    }
}

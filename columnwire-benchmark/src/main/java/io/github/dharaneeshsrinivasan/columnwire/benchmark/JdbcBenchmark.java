package io.github.dharaneeshsrinivasan.columnwire.benchmark;

import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.json.JsonMapper;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 6, time = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xms8g", "-Xmx8g", "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow"})
public class JdbcBenchmark {

    static final int FIRST_BYTES = 8192;

    @Param({"10000", "100000", "1000000"})
    public int rows;

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private JsonMapper json;
    private ArrowRecordEncoder arrow;
    private ByteArrayOutputStream sink;
    private FirstBytes first;

    @Setup(Level.Trial)
    public void setUp() throws SQLException {
        String url = OrderTable.url("benchmark");
        OrderTable.create(url, Orders.generate(rows));
        dataSource = new SingleConnectionDataSource(url, true);
        jdbc = new JdbcTemplate(dataSource);
        json = JsonMapper.builder().build();
        arrow = new ArrowRecordEncoder();
        sink = new ByteArrayOutputStream(rows * 300);
        first = new FirstBytes(FIRST_BYTES);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        arrow.close();
        dataSource.destroy();
    }

    @Benchmark
    public int queryForListJackson() {
        sink.reset();
        json.writeValue(sink, jdbc.queryForList(OrderTable.SELECT));
        return sink.size();
    }

    @Benchmark
    public int columnwireArrow() {
        sink.reset();
        OrderTable.encode(jdbc, OrderTable.SELECT, arrow, sink);
        return sink.size();
    }

    @Benchmark
    public int queryForListJacksonFirstBytes() {
        first.reset();
        try {
            json.writeValue(first, jdbc.queryForList(OrderTable.SELECT));
        } catch (RuntimeException e) {
            first.rethrowUnlessFull(e);
        }
        return first.size();
    }

    @Benchmark
    public int columnwireArrowFirstBytes() {
        first.reset();
        try {
            OrderTable.encode(jdbc, OrderTable.SELECT, arrow, first);
        } catch (RuntimeException e) {
            first.rethrowUnlessFull(e);
        }
        return first.size();
    }

    static final class FirstBytes extends OutputStream {

        private final int limit;
        private int size;

        FirstBytes(int limit) {
            this.limit = limit;
        }

        void reset() {
            size = 0;
        }

        int size() {
            return size;
        }

        void rethrowUnlessFull(RuntimeException e) {
            if (size < limit) {
                throw e;
            }
        }

        @Override
        public void write(int b) {
            count(1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            count(len);
        }

        private void count(int bytes) {
            size += bytes;
            if (size >= limit) {
                throw new Full();
            }
        }
    }

    static final class Full extends RuntimeException {

        Full() {
            super(null, null, false, false);
        }
    }
}

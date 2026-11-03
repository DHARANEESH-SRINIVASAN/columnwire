package io.github.dharaneeshsrinivasan.columnwire.spring;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = JdbcQueryJsonSerializer.class)
public final class JdbcQuery {

    private final JdbcOperations jdbc;
    private final String sql;
    private final Object[] args;

    private JdbcQuery(JdbcOperations jdbc, String sql, Object[] args) {
        this.jdbc = jdbc;
        this.sql = sql;
        this.args = args;
    }

    public static JdbcQuery of(JdbcOperations jdbc, String sql, Object... args) {
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(sql, "sql");
        return new JdbcQuery(jdbc, sql, args == null ? new Object[0] : args.clone());
    }

    <T> T query(ResultSetExtractor<T> extractor) {
        return jdbc.query(sql, extractor, args);
    }

    @Override
    public String toString() {
        return "JdbcQuery[" + sql + "]";
    }
}

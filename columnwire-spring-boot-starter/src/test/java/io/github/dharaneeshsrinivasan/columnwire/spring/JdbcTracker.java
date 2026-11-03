package io.github.dharaneeshsrinivasan.columnwire.spring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

final class JdbcTracker {

    private final Set<Object> open = ConcurrentHashMap.newKeySet();
    private final AtomicLong opened = new AtomicLong();
    private final AtomicLong rowsRead = new AtomicLong();

    DataSource wrap(DataSource dataSource) {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    Object result = call(dataSource, method, args);
                    return result instanceof Connection connection ? track(Connection.class, connection) : result;
                });
    }

    long open() {
        return open.size();
    }

    long opened() {
        return opened.get();
    }

    long rowsRead() {
        return rowsRead.get();
    }

    private <T> T track(Class<T> type, T target) {
        Object token = new Object();
        open.add(token);
        opened.incrementAndGet();
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    Object result = call(target, method, args);
                    if (method.getName().equals("close")) {
                        open.remove(token);
                    } else if (method.getName().equals("next") && Boolean.TRUE.equals(result)) {
                        rowsRead.incrementAndGet();
                    } else if (result instanceof ResultSet rows) {
                        return track(ResultSet.class, rows);
                    } else if (result instanceof PreparedStatement statement) {
                        return track(PreparedStatement.class, statement);
                    } else if (result instanceof Statement statement) {
                        return track(Statement.class, statement);
                    }
                    return result;
                }));
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

package io.github.dharaneeshsrinivasan.columnwire;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

interface RowCursor<R> {

    boolean next() throws SQLException;

    R row();

    static <R> RowCursor<R> of(Iterator<? extends R> rows) {
        return new RowCursor<>() {
            private R row;

            @Override
            public boolean next() {
                if (!rows.hasNext()) {
                    return false;
                }
                row = rows.next();
                return true;
            }

            @Override
            public R row() {
                return row;
            }
        };
    }

    static RowCursor<ResultSet> of(ResultSet rows) {
        return new RowCursor<>() {
            @Override
            public boolean next() throws SQLException {
                return rows.next();
            }

            @Override
            public ResultSet row() {
                return rows;
            }
        };
    }
}

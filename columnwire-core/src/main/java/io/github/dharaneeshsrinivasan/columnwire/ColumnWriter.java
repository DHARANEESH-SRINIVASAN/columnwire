package io.github.dharaneeshsrinivasan.columnwire;

@FunctionalInterface
interface ColumnWriter<R> {

    void write(R row, int index) throws Throwable;
}

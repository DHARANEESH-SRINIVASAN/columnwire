package io.github.dharaneeshsrinivasan.columnwire;

@FunctionalInterface
interface ColumnWriter {

    void write(Record row, int index) throws Throwable;
}

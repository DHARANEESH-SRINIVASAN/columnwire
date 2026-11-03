package io.github.dharaneeshsrinivasan.columnwire;

import java.util.List;
import java.util.function.Function;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.types.pojo.Field;

record ColumnSpec<R>(String name, Field field, Function<FieldVector, ColumnWriter<R>> binder,
                     List<String> dictionary) {

    boolean dictionaryEncoded() {
        return dictionary != null;
    }
}

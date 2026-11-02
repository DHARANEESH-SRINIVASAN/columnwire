package io.github.dharaneeshsrinivasan.columnwire;

import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;

public final class ArrowRuntime {

    public static final String JVM_FLAGS = "--add-opens=java.base/java.nio=ALL-UNNAMED";

    private ArrowRuntime() {
    }

    public static void verify() {
        try (BufferAllocator allocator = new RootAllocator(); ArrowBuf buffer = allocator.buffer(8)) {
            buffer.setLong(0, 1L);
            if (buffer.nioBuffer(0, 8).remaining() != 8) {
                throw new IllegalStateException("Arrow returned a truncated NIO view of off-heap memory");
            }
        } catch (Throwable e) {
            throw new IllegalStateException("Apache Arrow cannot reach off-heap memory on this JVM, so Columnwire"
                    + " cannot encode responses. Start the JVM with " + JVM_FLAGS
                    + " (on Java 24 or newer also add --sun-misc-unsafe-memory-access=allow to silence the"
                    + " Unsafe warning). Cause: " + e, e);
        }
    }
}

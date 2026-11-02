package io.github.dharaneeshsrinivasan.columnwire.spring;

import io.github.dharaneeshsrinivasan.columnwire.ArrowMediaTypes;
import io.github.dharaneeshsrinivasan.columnwire.ArrowRecordEncoder;
import io.github.dharaneeshsrinivasan.columnwire.RecordSchema;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.BaseStream;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractSmartHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public final class ArrowHttpMessageConverter extends AbstractSmartHttpMessageConverter<Object> {

    public static final MediaType ARROW_STREAM = MediaType.parseMediaType(ArrowMediaTypes.STREAM);

    private final ArrowRecordEncoder encoder;

    public ArrowHttpMessageConverter(ArrowRecordEncoder encoder) {
        super(ARROW_STREAM);
        this.encoder = encoder;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return false;
    }

    @Override
    public boolean canRead(ResolvableType type, MediaType mediaType) {
        return false;
    }

    @Override
    public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints) {
        throw new HttpMessageNotReadableException("Columnwire writes Arrow responses only", inputMessage);
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return canWrite(mediaType) && recordType(type, valueClass) != null;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
        return getSupportedMediaTypes();
    }

    @Override
    protected void addDefaultHeaders(HttpHeaders headers, Object value, MediaType contentType) throws IOException {
        super.addDefaultHeaders(headers, value, contentType);
        if (!headers.getVary().contains(HttpHeaders.ACCEPT)) {
            headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        }
    }

    @Override
    protected boolean supportsRepeatableWrites(Object value) {
        return false;
    }

    @Override
    protected void writeInternal(Object value, ResolvableType type, HttpOutputMessage outputMessage,
                                 Map<String, Object> hints) throws IOException {
        Class<? extends Record> recordType = recordType(type, value.getClass());
        if (recordType == null) {
            throw new HttpMessageNotWritableException("Cannot encode " + type + " as Arrow");
        }
        try {
            encode(recordType, iterator(value), outputMessage);
        } finally {
            if (value instanceof BaseStream<?, ?> stream) {
                stream.close();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void encode(Class<? extends Record> recordType, Iterator<?> rows, HttpOutputMessage out)
            throws IOException {
        encoder.encode((Class) recordType, (Iterator) rows, out.getBody());
    }

    static Class<? extends Record> recordType(ResolvableType type, Class<?> valueClass) {
        ResolvableType element = elementType(type);
        if (element == null && valueClass != null) {
            element = elementType(ResolvableType.forClass(valueClass));
        }
        if (element == null) {
            return null;
        }
        Class<?> raw = element.resolve();
        if (raw == null || !raw.isRecord() || !RecordSchema.supports(raw)) {
            return null;
        }
        return raw.asSubclass(Record.class);
    }

    private static ResolvableType elementType(ResolvableType type) {
        if (type == null || type == ResolvableType.NONE) {
            return null;
        }
        if (type.isArray()) {
            return type.getComponentType();
        }
        for (Class<?> container : new Class<?>[] {Iterable.class, BaseStream.class, Iterator.class}) {
            ResolvableType view = type.as(container);
            if (view != ResolvableType.NONE) {
                ResolvableType generic = view.getGeneric(0);
                return generic.resolve() == null ? null : generic;
            }
        }
        return null;
    }

    private static Iterator<?> iterator(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator();
        }
        if (value instanceof BaseStream<?, ?> stream) {
            return stream.iterator();
        }
        if (value instanceof Iterator<?> iterator) {
            return iterator;
        }
        if (value instanceof Object[] array) {
            return Arrays.asList(array).iterator();
        }
        throw new HttpMessageNotWritableException("Cannot iterate " + value.getClass().getName());
    }
}

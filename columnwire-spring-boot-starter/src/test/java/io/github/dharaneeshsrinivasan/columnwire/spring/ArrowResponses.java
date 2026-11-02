package io.github.dharaneeshsrinivasan.columnwire.spring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.ipc.ArrowStreamReader;

final class ArrowResponses {

    record Decoded(List<Map<String, Object>> rows, int batches) {
    }

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private ArrowResponses() {
    }

    static HttpResponse<byte[]> get(int port, String path, String accept) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (accept != null) {
            request.header("Accept", accept);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static Decoded decode(byte[] body) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        int batches = 0;
        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(new ByteArrayInputStream(body), allocator)) {
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            while (reader.loadNextBatch()) {
                batches++;
                List<FieldVector> vectors = new ArrayList<>();
                for (FieldVector vector : root.getFieldVectors()) {
                    if (vector.getField().getDictionary() != null) {
                        Dictionary dictionary = reader.getDictionaryVectors()
                                .get(vector.getField().getDictionary().getId());
                        vectors.add((VarCharVector) DictionaryEncoder.decode(vector, dictionary));
                    } else {
                        vectors.add(vector);
                    }
                }
                for (int i = 0; i < root.getRowCount(); i++) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    for (int c = 0; c < vectors.size(); c++) {
                        Object value = vectors.get(c).getObject(i);
                        row.put(root.getSchema().getFields().get(c).getName(),
                                value instanceof org.apache.arrow.vector.util.Text t ? t.toString() : value);
                    }
                    rows.add(row);
                }
                for (int c = 0; c < vectors.size(); c++) {
                    if (vectors.get(c) != root.getVector(c)) {
                        vectors.get(c).close();
                    }
                }
            }
        }
        return new Decoded(rows, batches);
    }
}

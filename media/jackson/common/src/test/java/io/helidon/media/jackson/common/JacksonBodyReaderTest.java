package io.helidon.media.jackson.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReaderContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class JacksonBodyReaderTest {

    @Test
    void testDeserializeWithGenerics() throws Exception {
        JacksonBodyReader reader = JacksonBodyReader.create(new ObjectMapper());
        DataChunk dataChunk = DataChunk.create("[{\"title\":\"The Stand\"}]".getBytes(StandardCharsets.UTF_8));
        List<Book> books = reader.read(Single.just(dataChunk), new GenericType<List<Book>>() {
        }, MessageBodyReaderContext.create())
                .get();

        Assertions.assertEquals(1, books.size());
        Assertions.assertTrue(books.get(0) instanceof Book);
    }

    public static class Book {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}

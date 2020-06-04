/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.media.jackson;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReaderContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class JacksonBodyReaderTest {

    @Test
    void testDeserializeWithGenerics() throws Exception {
        JacksonBodyReader reader = JacksonBodyReader.create(new ObjectMapper());
        DataChunk dataChunk = DataChunk.create("[{\"title\":\"The Stand\"}]".getBytes(StandardCharsets.UTF_8));
        List<Book> books = reader.read(Single.just(dataChunk), new GenericType<List<Book>>() {
        }, MessageBodyReaderContext.create())
                .get();

        assertThat(books.size(), is(1));
        assertThat(books.get(0), notNullValue());
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

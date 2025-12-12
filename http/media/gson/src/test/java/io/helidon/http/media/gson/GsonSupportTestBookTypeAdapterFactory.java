/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class GsonSupportTestBookTypeAdapterFactory implements TypeAdapterFactory {

    static final AtomicInteger readCount = new AtomicInteger(0);
    static final AtomicInteger writeCount = new AtomicInteger(0);

    private static final TypeAdapter instance = new TypeAdapter<GsonSupportTest.Book>() {
        @Override
        public void write(JsonWriter writer, GsonSupportTest.Book book) throws IOException {
            writer.beginObject();
            writer.name("title");
            writer.value(book.title());
            writer.name("pages");
            writer.value(book.pages());
            writer.endObject();
            writeCount.incrementAndGet();
        }

        @Override
        public GsonSupportTest.Book read(JsonReader reader) throws IOException {
            reader.beginObject();
            reader.nextName();
            var title = reader.nextString();
            reader.nextName();
            var pages = reader.nextInt();
            reader.endObject();
            readCount.incrementAndGet();
            return new GsonSupportTest.Book(title, pages);
        }
    };

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (typeToken.getRawType().isAssignableFrom(GsonSupportTest.Book.class)) {
            return instance;
        }
        return null;
    }
}

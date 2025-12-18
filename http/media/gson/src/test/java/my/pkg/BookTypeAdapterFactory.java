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
package my.pkg;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class BookTypeAdapterFactory implements TypeAdapterFactory {

    private static final TypeAdapter<Book> instance = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter writer, Book book) throws IOException {
            writer.beginObject();
            writer.name("title");
            writer.value(book.title());
            writer.name("pages");
            writer.value(book.pages());
            writer.endObject();
        }

        @Override
        public Book read(JsonReader reader) throws IOException {
            reader.beginObject();
            String title = null;
            int pages = 0;
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "title" -> title = reader.nextString();
                    case "pages" -> pages = reader.nextInt();
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            return new Book(title, pages);
        }
    };

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (typeToken.getRawType().isAssignableFrom(Book.class)) {
            return (TypeAdapter<T>) instance;
        }
        return null;
    }
}

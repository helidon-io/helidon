/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

public class BookMapper {

    private static final String ISBN = "isbn";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String SUMMARY = "summary";
    private static final String GENRE = "genre";
    private static final String CATEGORY = "category";
    private static final String PUBLISHER = "publisher";
    private static final String COPYRIGHT = "copyright";
    private static final String AUTHORS = "authors";

    private BookMapper() {
    }

    /**
     *
     * @param book Book to convert to JSON
     * @return new JsonObject representing book
     */
    public static JsonObject encode(Book book) {
        JsonObject jo = JsonObject.builder()
                .set(ISBN, book.getIsbn())
                .set(TITLE, book.getTitle())
                .set(DESCRIPTION, book.getDescription())
                .set(SUMMARY, book.getSummary())
                .set(GENRE, book.getGenre())
                .set(CATEGORY, book.getCategory())
                .set(PUBLISHER, book.getPublisher())
                .set(COPYRIGHT, book.getCopyright())
                .set(AUTHORS, AuthorMapper.encode(book.getAuthors()))
                .build();
        return jo;
    }

    public static JsonArray encode(Collection<Book> books) {
        List<JsonValue> values = new ArrayList<>();
        for (Book b : books) {
            values.add(encode(b));
        }
        return JsonArray.create(values);
    }

    /**
     * @param jo JsonObject representation to convert to Book pojo
     * @return New Book pojo
     */
    public static Book decode(JsonObject jo) {
        return updateBook(new Book(), jo);
    }

    /**
     * @param book book to update
     * @param jo JsonObject representation of book to get new values from
     * @return Updated book
     */
    private static Book updateBook(Book book, JsonObject jo) {
        if (jo.containsKey(ISBN)) {
            book.setIsbn(jo.stringValue(ISBN).orElseThrow());
        }
        if (jo.containsKey(TITLE)) {
            book.setTitle(jo.stringValue(TITLE).orElseThrow());
        }
        if (jo.containsKey(DESCRIPTION)) {
            book.setDescription(jo.stringValue(DESCRIPTION).orElseThrow());
        }
        if (jo.containsKey(SUMMARY)) {
            book.setSummary(jo.stringValue(SUMMARY).orElseThrow());
        }
        if (jo.containsKey(GENRE)) {
            book.setGenre(jo.stringValue(GENRE).orElseThrow());
        }
        if (jo.containsKey(CATEGORY)) {
            book.setCategory(jo.stringValue(CATEGORY).orElseThrow());
        }
        if (jo.containsKey(PUBLISHER)) {
            book.setPublisher(jo.stringValue(PUBLISHER).orElseThrow());
        }
        if (jo.containsKey(COPYRIGHT)) {
            book.setCopyright(jo.stringValue(COPYRIGHT).orElseThrow());
        }
        if (jo.containsKey(AUTHORS)) {
            book.setAuthors(AuthorMapper.decode(jo.arrayValue(AUTHORS).orElseThrow()));
        }
        return book;
    }
}

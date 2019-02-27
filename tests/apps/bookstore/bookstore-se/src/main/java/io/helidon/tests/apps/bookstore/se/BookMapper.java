/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.apps.bookstore.se;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import java.util.Collection;

import io.helidon.tests.apps.bookstore.mp.Book;

class BookMapper {

    private static final String ISBN = "isbn";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String SUMMARY = "summary";
    private static final String GENRE = "genre";
    private static final String CATEGORY = "category";
    private static final String PUBLISHER = "publisher";
    private static final String COPYRIGHT = "copyright";
    private static final String AUTHORS = "authors";

    /*
     * Use JsonBuilderFactory (and not the Json factory) to create builders.
     * This lets us cache the builder factory and improves performance dramatically.
     */
    private static final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(null);

    /**
     *
     * @param book Book to convert to JSON
     * @return new JsonObject representing book
     */
    static JsonObject encodeJsonp(Book book) {
        JsonObject jo = jsonFactory.createObjectBuilder()
                .add(ISBN, book.getIsbn())
                .add(TITLE, book.getTitle())
                .add(DESCRIPTION, book.getDescription())
                .add(SUMMARY, book.getSummary())
                .add(GENRE, book.getGenre())
                .add(CATEGORY, book.getCategory())
                .add(PUBLISHER, book.getPublisher())
                .add(COPYRIGHT, book.getCopyright())
                .add(AUTHORS, AuthorMapper.encode(book.getAuthors()))
                .build();
        return jo;
    }

    static JsonArray encodeJsonp(Collection<Book> books) {
        JsonArrayBuilder builder = jsonFactory.createArrayBuilder();
        for (Book b : books) {
            builder.add(encodeJsonp(b));
        }
        return builder.build();
    }

    /**
     * @param jo JsonObject representation to convert to Book pojo
     * @return New Book pojo
     */
    static Book decodeJsonp(JsonObject jo) {
        return updateBook(new Book(), jo);
    }

    /**
     * @param book book to update
     * @param jo JsonObject representation of book to get new values from
     * @return Updated book
     */
    static private Book updateBook(Book book, JsonObject jo) {
        if (!jo.isNull(ISBN)) book.setIsbn(jo.getString(ISBN));
        if (!jo.isNull(TITLE)) book.setTitle(jo.getString(TITLE));
        if (!jo.isNull(DESCRIPTION)) book.setDescription(jo.getString(DESCRIPTION));
        if (!jo.isNull(SUMMARY)) book.setSummary(jo.getString(SUMMARY));
        if (!jo.isNull(GENRE)) book.setGenre(jo.getString(GENRE));
        if (!jo.isNull(CATEGORY)) book.setCategory(jo.getString(CATEGORY));
        if (!jo.isNull(PUBLISHER)) book.setPublisher(jo.getString(PUBLISHER));
        if (!jo.isNull(COPYRIGHT)) book.setCopyright(jo.getString(COPYRIGHT));
        if (!jo.isNull(AUTHORS)) book.setAuthors(AuthorMapper.decode(jo.getJsonArray(AUTHORS)) );
        return book;
    }
}

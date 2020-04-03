/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.tests.apps.bookstore.common.Author;

class AuthorMapper {

    private static final String FIRST = "first";
    private static final String LAST = "last";

    /*
     * Use JsonBuilderFactory (and not the Json factory) to create builders.
     * This lets us cache the builder factory and improves performance dramatically.
     */
    private static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(null);

    private AuthorMapper() {
    }

    /**
     *
     * @param author Author to convert to JSON
     * @return new JsonObject representing author
     */
    static JsonObject encode(Author author) {
        JsonObject jo = JSON_FACTORY.createObjectBuilder()
                .add(FIRST, author.getFirst())
                .add(LAST, author.getLast())
                .build();
        return jo;
    }

    /**
     * @param jo JsonObject representation to convert
     * @return New Author pojo
     */
    static Author decode(JsonObject jo) {
        return updateAuthor(new Author(), jo);
    }

    /**
     *
     * @param author author to update
     * @param jo JsonObject representation of author to get new values from
     * @return Updated author
     */
    private static Author updateAuthor(Author author, JsonObject jo) {
        if (jo.containsKey(FIRST)) author.setFirst(jo.getString(FIRST));
        if (jo.containsKey(LAST)) author.setLast(jo.getString(LAST));
        return author;
    }

    static JsonArray encode(List<Author> authors) {
        JsonArrayBuilder builder = JSON_FACTORY.createArrayBuilder();
        for (Author a : authors) {
            builder.add(encode(a));
        }
        return builder.build();
    }

    static List<Author> decode(JsonArray authors) {
        List<Author> list = new ArrayList<>();
        for (JsonObject jo : authors.getValuesAs(JsonObject.class)) {
            list.add(decode(jo));
        }
        return list;
    }

}

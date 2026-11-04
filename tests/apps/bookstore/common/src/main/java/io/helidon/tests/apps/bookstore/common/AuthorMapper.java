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
import java.util.List;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

class AuthorMapper {

    private static final String FIRST = "first";
    private static final String LAST = "last";

    private AuthorMapper() {
    }

    /**
     *
     * @param author Author to convert to JSON
     * @return new JsonObject representing author
     */
    static JsonObject encode(Author author) {
        JsonObject jo = JsonObject.builder()
                .set(FIRST, author.getFirst())
                .set(LAST, author.getLast())
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
        if (jo.containsKey(FIRST)) {
            author.setFirst(jo.stringValue(FIRST).orElseThrow());
        }
        if (jo.containsKey(LAST)) {
            author.setLast(jo.stringValue(LAST).orElseThrow());
        }
        return author;
    }

    static JsonArray encode(List<Author> authors) {
        List<JsonValue> values = new ArrayList<>();
        for (Author a : authors) {
            values.add(encode(a));
        }
        return JsonArray.create(values);
    }

    static List<Author> decode(JsonArray authors) {
        List<Author> list = new ArrayList<>();
        for (JsonValue value : authors.values()) {
            list.add(decode(value.asObject()));
        }
        return list;
    }

}

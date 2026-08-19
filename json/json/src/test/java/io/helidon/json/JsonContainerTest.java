/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonContainerTest {

    @Test
    void objectEqualityUsesKeyValueMappings() {
        JsonObject constructed = JsonObject.builder()
                .set("a", "one")
                .set("b", "two")
                .build();
        JsonObject parsed = JsonParser.create("{\"b\":\"two\",\"a\":\"one\"}").readJsonObject();
        JsonObject swapped = JsonParser.create("{\"a\":\"two\",\"b\":\"one\"}").readJsonObject();
        JsonObject extra = JsonParser.create("{\"a\":\"one\",\"b\":\"two\",\"c\":\"three\"}").readJsonObject();

        assertThat(constructed, is(parsed));
        assertThat(parsed, is(constructed));
        assertThat(constructed.hashCode(), is(parsed.hashCode()));
        assertThat(constructed, is(not(swapped)));
        assertThat(swapped, is(not(constructed)));
        assertThat(constructed, is(not(extra)));
        assertThat(extra, is(not(constructed)));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void objectKeysCannotMutateContent(ParserMethod parserMethod) {
        JsonObject object = parserMethod.createParser("""
                {"before":"one","value":"first","middle":"two","value":"last","after":"three"}
                """).readJsonObject();

        assertThat(object.size(), is(4));
        assertThat(object.stringValue("value").orElseThrow(), is("last"));
        assertThat(object.keysAsStrings(), is(Set.of("before", "value", "middle", "after")));
        assertThat(object.toString(),
                   is("{\"before\":\"one\",\"value\":\"last\",\"middle\":\"two\",\"after\":\"three\"}"));
        assertThrows(UnsupportedOperationException.class, () -> object.keysAsStrings().remove("value"));
        assertThat(object.stringValue("value").orElseThrow(), is("last"));
    }

    @Test
    void objectRejectsJavaNullValues() {
        LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();
        values.put("value", null);

        assertThrows(NullPointerException.class, () -> JsonObject.create(values));
    }

    @Test
    void arrayEqualityUsesImmutableOrderedValues() {
        List<JsonValue> source = new ArrayList<>(List.of(JsonString.create("one"), JsonString.create("two")));
        JsonArray constructed = JsonArray.create(source);
        JsonArray parsed = JsonParser.create("[\"one\",\"two\"]").readJsonArray();
        JsonArray reversed = JsonParser.create("[\"two\",\"one\"]").readJsonArray();

        source.set(0, JsonString.create("changed"));

        assertThat(constructed.get(0).orElseThrow().asString().value(), is("one"));
        assertThat(constructed, is(parsed));
        assertThat(parsed, is(constructed));
        assertThat(constructed.hashCode(), is(parsed.hashCode()));
        assertThat(constructed, is(not(reversed)));
        assertThat(reversed, is(not(constructed)));
        assertThrows(UnsupportedOperationException.class,
                     () -> constructed.values().add(JsonString.create("three")));
        assertThrows(UnsupportedOperationException.class,
                     () -> parsed.values().add(JsonString.create("three")));
    }

    @Test
    void arrayRejectsJavaNullValues() {
        List<JsonValue> values = new ArrayList<>();
        values.add(null);

        assertThrows(NullPointerException.class, () -> JsonArray.create(values));
    }

    @Test
    void numericContainersUseNumberValueEquality() {
        JsonArray first = JsonParser.create("[1,1.0]").readJsonArray();
        JsonArray same = JsonArray.create(JsonNumber.create(1), JsonNumber.create(1.0));
        JsonArray differentScale = JsonParser.create("[1.0,1]").readJsonArray();

        assertThat(first, is(same));
        assertThat(first.hashCode(), is(same.hashCode()));
        assertThat(first, is(not(differentScale)));
    }
}

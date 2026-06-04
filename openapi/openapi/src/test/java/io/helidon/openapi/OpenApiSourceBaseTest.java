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

package io.helidon.openapi;

import java.math.BigDecimal;

import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OpenApiSourceBaseTest {

    @Test
    void exampleValueParsesJsonObject() {
        JsonValue value = TestSource.example("{\"message\":\"Accepted\"}");

        assertThat(value.type(), is(JsonValueType.OBJECT));
        assertThat(value.asObject().stringValue("message").orElseThrow(), is("Accepted"));
    }

    @Test
    void exampleValueParsesJsonArray() {
        JsonValue value = TestSource.example("[\"one\",\"two\"]");

        assertThat(value.type(), is(JsonValueType.ARRAY));
        assertThat(value.asArray().get(1).orElseThrow().asString().value(), is("two"));
    }

    @Test
    void exampleValueParsesJsonScalar() {
        JsonValue value = TestSource.example("42");

        assertThat(value.type(), is(JsonValueType.NUMBER));
        assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("42")));
    }

    @Test
    void exampleValueFallsBackToStringForNonJsonText() {
        JsonValue value = TestSource.example("hello/world");

        assertThat(value.type(), is(JsonValueType.STRING));
        assertThat(value.asString().value(), is("hello/world"));
    }

    @Test
    void exampleValueFallsBackToStringForPartialJsonText() {
        JsonValue value = TestSource.example("42 is the answer");

        assertThat(value.type(), is(JsonValueType.STRING));
        assertThat(value.asString().value(), is("42 is the answer"));
    }

    private static final class TestSource extends OpenApiSourceBase {
        private static JsonValue example(String value) {
            return exampleValue(value);
        }

        @Override
        public void describe(OpenApiDocumentContext context, OpenApiDocument.Builder document) {
        }
    }
}

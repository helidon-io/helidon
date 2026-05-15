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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectBuilderTest {

    @Test
    void shouldSetAdditionalNumericValues() {
        BigInteger bigInteger = new BigInteger("123456789012345678901234567890");

        JsonObject result = JsonObject.builder()
                .set("byte", (byte) 7)
                .set("short", (short) 1024)
                .set("bigInteger", bigInteger)
                .build();

        assertThat(result.toString(),
                   is("{\"byte\":7,\"short\":1024,\"bigInteger\":123456789012345678901234567890}"));
        assertThat(result.numberValue("byte").orElseThrow(), is(new BigDecimal("7")));
        assertThat(result.numberValue("short").orElseThrow(), is(new BigDecimal("1024")));
        assertThat(result.numberValue("bigInteger").orElseThrow(), is(new BigDecimal(bigInteger)));
    }

    @Test
    void shouldAcceptSubtypeListsForArrayValues() {
        List<JsonString> values = List.of(JsonString.create("Ada"), JsonString.create("Bob"));

        JsonObject result = JsonObject.builder()
                .setValues("names", values)
                .build();

        assertThat(result.toString(), is("{\"names\":[\"Ada\",\"Bob\"]}"));
    }

    @Test
    void shouldCopyValuesFromExistingObject() {
        JsonObject source = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .build();

        JsonObject result = JsonObject.builder()
                .from(source)
                .build();

        assertThat(result.toString(), is("{\"name\":\"Ada\",\"active\":true}"));
    }

    @Test
    void shouldAllowOverridesAfterFrom() {
        JsonObject source = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .build();

        JsonObject result = JsonObject.builder()
                .from(source)
                .set("active", false)
                .set("team", "json")
                .build();

        assertThat(result.toString(), is("{\"name\":\"Ada\",\"active\":false,\"team\":\"json\"}"));
    }

    @Test
    void shouldRejectNullSource() {
        assertThrows(NullPointerException.class, () -> JsonObject.builder().from(null));
    }
}

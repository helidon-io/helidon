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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectBuilderTest {

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

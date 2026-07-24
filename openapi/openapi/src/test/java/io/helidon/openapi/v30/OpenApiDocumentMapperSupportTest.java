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

package io.helidon.openapi.v30;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValueType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentMapperSupportTest {

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.jsonObject(null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.jsonValue(null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.jsonNumber(null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.copyAllowed(null, Set.of("x")));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.copyAllowed(Map.of(), null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.allowed(null, Set.of("x")));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.allowed("x", null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.copy(null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.objectMap((Map<?, ?>) null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.objectMap((JsonObject) null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.object(null, object -> { }));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.object(Map.of(), null));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.objectList(null, object -> object));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.objectList(List.of(), null));
    }

    @Test
    void rejectsNullMapKeys() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(null, "value");

        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.jsonObject(values));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.copy(values));
        assertThrows(NullPointerException.class, () -> OpenApiDocumentMapperSupport.objectMap(values));
    }

    @Test
    void preservesJsonNullData() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("value", null);
        Map<String, Object> copied = new LinkedHashMap<>();

        JsonObject jsonObject = OpenApiDocumentMapperSupport.jsonObject(values);
        OpenApiDocumentMapperSupport.copyField(copied, "value", values);

        assertThat(OpenApiDocumentMapperSupport.jsonValue(JsonNull.instance()), sameInstance(JsonNull.instance()));
        assertThat(jsonObject.value("value").map(jsonValue -> jsonValue.type()).orElseThrow(), is(JsonValueType.NULL));
        assertThat(OpenApiDocumentMapperSupport.objectMap(jsonObject).get("value"), is((Object) null));
        assertThat(copied.containsKey("value"), is(true));
        assertThat(copied.get("value"), is((Object) null));
    }
}

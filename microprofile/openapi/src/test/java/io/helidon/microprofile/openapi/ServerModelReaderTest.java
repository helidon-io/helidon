/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.io.StringReader;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.openapi.test.MyModelReader;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Makes sure that the app-supplied model reader participates in constructing
 * the OpenAPI model.
 */
@HelidonTest
@AddConfig(key = "mp.openapi.model.reader", value = "io.helidon.microprofile.openapi.test.MyModelReader")
@AddConfig(key = "mp.openapi.filter", value = "io.helidon.microprofile.openapi.test.MySimpleFilter")
@AddBean(TestApp.class)
class ServerModelReaderTest {

    private static final String APPLICATION_OPENAPI_JSON = MediaTypes.APPLICATION_OPENAPI_JSON.text();

    @Inject
    private WebTarget webTarget;

    @Test
    void checkCustomModelReader() {
        try (Response response = webTarget.path("/openapi").request(APPLICATION_OPENAPI_JSON).get()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.getMediaType().toString(), is(APPLICATION_OPENAPI_JSON));
            String text = response.readEntity(String.class);
            JsonStructure json = readJson(text);

            // The model reader adds the following key/value (among others) to the model.
            JsonValue v = json.getValue(String.format("/paths/%s/get/summary",
                                                      escapeJsonPointer(MyModelReader.MODEL_READER_PATH)));
            assertThat(v.getValueType(), is(JsonValue.ValueType.STRING));
            assertThat(((JsonString) v).getString(), is(MyModelReader.SUMMARY));
        }
    }

    @Test
    void makeSureFilteredPathIsMissing() {
        try (Response response = webTarget.path("/openapi").request(APPLICATION_OPENAPI_JSON).get()) {
            assertThat(response.getStatus(), is(200));
            assertThat(response.getMediaType().toString(), is(APPLICATION_OPENAPI_JSON));
            String text = response.readEntity(String.class);
            JsonStructure json = readJson(text);

            // Although the model reader adds this path, the filter should have removed it.
            JsonException ex = assertThrows(JsonException.class, () ->
                    json.getValue(String.format("/paths/%s/get/summary", escapeJsonPointer(MyModelReader.DOOMED_PATH))));

            assertThat(ex.getMessage(), containsString(
                    String.format("contains no mapping for the name '%s'", MyModelReader.DOOMED_PATH)));
        }
    }

    private static JsonStructure readJson(String str) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(str))) {
            return jsonReader.read();
        }
    }

    private static String escapeJsonPointer(String pointer) {
        return pointer.replaceAll("~", "~0").replaceAll("/", "~1");
    }
}

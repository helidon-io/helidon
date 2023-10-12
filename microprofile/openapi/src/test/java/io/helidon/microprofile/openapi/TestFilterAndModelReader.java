/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.microprofile.openapi.test.MyModelReader;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@AddConfig(key = "mp.openapi.model.reader", value = "io.helidon.microprofile.openapi.test.MyModelReader")
@AddConfig(key = "mp.openapi.filter", value = "io.helidon.microprofile.openapi.test.MySimpleFilter")
class TestFilterAndModelReader {

    @Inject
    private WebTarget webTarget;

    /**
     * Converts a JSON pointer possibly containing slashes and tildes into a
     * JSON pointer with such characters properly escaped.
     *
     * @param pointer original JSON pointer expression
     * @return escaped (if needed) JSON pointer
     */
    static String escapeForJsonPointer(String pointer) {
        return pointer.replaceAll("\\~", "~0").replaceAll("\\/", "~1");
    }

    @Test
    void checkCustomModelReader() {
        JsonObject openApiResult = webTarget.path("/openapi")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        // The model reader adds a path to the model. Check for it.
        JsonValue v = openApiResult.getValue(String.format("/paths/%s/get/summary",
                                                           escapeForJsonPointer(MyModelReader.MODEL_READER_PATH)));
        assertThat("Json value type of path added by reader", v.getValueType(), is(JsonValue.ValueType.STRING));
        JsonString s = (JsonString) v;
        assertThat("Summary value added by reader", s.getString(), is(MyModelReader.SUMMARY));
    }

    @Test
    void makeSureFilteredPathIsMissing() {
        JsonObject openApiResult = webTarget.path("/openapi")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);

        /*
         Although the model reader adds the "doomed" path, the filter should remove it.
         */
        JsonException ex = assertThrows(JsonException.class,
                                        () -> {
                                            JsonValue v = openApiResult.getValue(
                                                    String.format("/paths/%s/get/summary",
                                                                  escapeForJsonPointer(MyModelReader.DOOMED_PATH)));
                                        });
        assertThat("Exception message",
                   ex.getMessage(),
                   containsString(String.format("contains no mapping for the name '%s'",
                                                MyModelReader.DOOMED_PATH)));
    }
}

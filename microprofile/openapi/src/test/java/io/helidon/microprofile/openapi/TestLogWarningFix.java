/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.openapi.TestUtil.resource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestLogWarningFix {

    private static final java.util.logging.Logger SNAKE_YAML_INTROSPECTOR_LOGGER =
            java.util.logging.Logger.getLogger(org.yaml.snakeyaml.introspector.PropertySubstitute.class.getPackage().getName());

    @Test
    void testLogWarningAbsent() {
        var testHandler = new MonitoringHandler();
        SNAKE_YAML_INTROSPECTOR_LOGGER.addHandler(testHandler);
        try {

            OpenAPI openAPI = parse("/openapi-greeting.yml");
            assertThat("Warning messages", testHandler.messages(), is(Collections.emptyList()));

        } finally {
            SNAKE_YAML_INTROSPECTOR_LOGGER.removeHandler(testHandler);
        }
    }

    private static OpenAPI parse(String path) {
        String document = resource(path);
        return OpenApiParser.parse(OpenApiHelper.types(), OpenAPI.class, new StringReader(document));
    }

    private static class MonitoringHandler extends Handler {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());


        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> messages() {
            return messages;
        }
    }
}

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

package io.helidon.webserver.tests.observe.log4j;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.log.LogObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ObserveLog4jTest {
    private static final String LOGGER_NAME = "com.oracle.issue12066.application";

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ObserveFeature.builder()
                                    .addObserver(LogObserver.builder()
                                                         .permitAll(true)
                                                         .build())
                                    .build());
    }

    @Test
    void shouldExposeAndManageSlf4jLoggerBackedByLog4j(WebClient client) {
        LoggerFactory.getLogger(LOGGER_NAME).info("Create Log4j-backed SLF4J logger");

        ClientResponseTyped<String> allLoggers = client.get("/observe/log/loggers")
                .request(String.class);

        assertThat(allLoggers.status(), is(Status.OK_200));
        assertThat(allLoggers.entity(), containsString(LOGGER_NAME));

        ClientResponseTyped<String> setLevel = client.post("/observe/log/loggers/" + LOGGER_NAME)
                .header(HeaderNames.CONTENT_TYPE, "application/json")
                .submit("{\"level\":\"DEBUG\"}", String.class);

        assertThat(setLevel.status(), is(Status.NO_CONTENT_204));

        ClientResponseTyped<JsonObject> loggerResponse = client.get("/observe/log/loggers/" + LOGGER_NAME)
                .request(JsonObject.class);

        assertThat(loggerResponse.status(), is(Status.OK_200));
        JsonObject logger = loggerResponse.entity().objectValue(LOGGER_NAME).orElse(null);
        assertThat("Entity: " + loggerResponse.entity(), logger, notNullValue());
        assertThat("Entity: " + loggerResponse.entity(),
                   logger.stringValue("configuredLevel").orElse(null),
                   is("DEBUG"));

        ClientResponseTyped<String> unsetLevel = client.delete("/observe/log/loggers/" + LOGGER_NAME)
                .request(String.class);

        assertThat(unsetLevel.status(), is(Status.NO_CONTENT_204));
    }
}

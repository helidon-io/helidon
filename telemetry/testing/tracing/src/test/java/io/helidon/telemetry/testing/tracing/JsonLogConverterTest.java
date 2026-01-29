/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.telemetry.testing.tracing;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;

class JsonLogConverterTest {

    @Test
    void testJsonLogConverterWithConcurrentUpdates() throws Exception {

        Logger logger = Logger.getLogger(JsonLogConverterTest.class.getName());
        JsonLogConverterImpl.TestLogHandler testLogHandler = JsonLogConverterImpl.TestLogHandler.create();
        logger.addHandler(testLogHandler);

        // Trigger a concurrent update exception if the code is not handling multithreading properly
        // by starting a stream and then adding a new message.
        logger.log(Level.INFO, "First msg");
        testLogHandler.resourceSpans()
                .forEach(span -> logger.log(Level.INFO, "Second msg"));
        assertThat("Fetch of spans during update", testLogHandler.resourceSpans(), hasSize(2));

    }

    @Test
    void testEmptyStringValueAttribute() throws Exception {
        /*
        Mimic the default (empty) helidon socket attribute.
        {"key":"helidon.socket","value":{}}
         */

        Logger logger = Logger.getLogger(JsonLogConverterTest.class.getName());
        try (var converter = new JsonLogConverterImpl(logger)) {

            logger.log(Level.INFO, """
                    {
                       "resource" : {
                         "attributes" : [ {
                           "key" : "service.name",
                           "value" : {
                             "stringValue" : "otel-config-example"
                           }
                         }, {
                           "key" : "telemetry.sdk.language",
                           "value" : {
                             "stringValue" : "java"
                           }
                         }, {
                           "key" : "telemetry.sdk.name",
                           "value" : {
                             "stringValue" : "opentelemetry"
                           }
                         }, {
                           "key" : "telemetry.sdk.version",
                           "value" : {
                             "stringValue" : "1.58.0"
                           }
                         }, {
                           "key" : "x",
                           "value" : {
                             "stringValue" : "x-value"
                           }
                         }, {
                           "key" : "y",
                           "value" : {
                             "intValue" : "9"
                           }
                         } ]
                       },
                       "scopeSpans" : [ {
                         "scope" : {
                           "name" : "otel-config-example",
                           "attributes" : [ ]
                         },
                         "spans" : [ {
                           "traceId" : "6edae103f4de519e341ed47e84888e6f",
                           "spanId" : "89076986e7a0b27e",
                           "name" : "GET",
                           "kind" : 2,
                           "startTimeUnixNano" : "1769634903759517000",
                           "endTimeUnixNano" : "1769634931221218583",
                           "attributes" : [ {
                             "key" : "server.address",
                             "value" : {
                               "stringValue" : "helidon-unit"
                             }
                           }, {
                             "key" : "net.host.name",
                             "value" : {
                               "stringValue" : "helidon-unit"
                             }
                           }, {
                             "key" : "user_agent.original",
                             "value" : {
                               "stringValue" : "Helidon 4.4.0-SNAPSHOT"
                             }
                           }, {
                             "key" : "url.path",
                             "value" : {
                               "stringValue" : "/greeting/Joe"
                             }
                           }, {
                             "key" : "http.request.method",
                             "value" : {
                               "stringValue" : "GET"
                             }
                           }, {
                             "key" : "helidon.socket",
                             "value" : { }
                           }, {
                             "key" : "server.port",
                             "value" : {
                               "stringValue" : "8080"
                             }
                           }, {
                             "key" : "http.response.status_code",
                             "value" : {
                               "stringValue" : "404"
                             }
                           }, {
                             "key" : "url.scheme",
                             "value" : {
                               "stringValue" : "http"
                             }
                           }, {
                             "key" : "net.host.port",
                             "value" : {
                               "intValue" : "8080"
                             }
                           } ],
                           "events" : [ ],
                           "links" : [ ],
                           "status" : { },
                           "flags" : 257
                         } ]
                       } ]
                     }""");

            var logSpan = converter.resourceSpans(1).getFirst()
                    .scopeSpans().getFirst()
                    .logSpans().getFirst();

            /*
            The presence of helidon.socket with an empty value caused a problem in the converter that should be fixed.
             */
            assertThat("Attributes", logSpan.attributes(), allOf(
                    hasEntry("server.address", "helidon-unit"),
                    hasEntry("url.path", "/greeting/Joe"),
                    hasEntry("helidon.socket", "")));
        }
    }
}

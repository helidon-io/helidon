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
package io.helidon.tests.integration.tracerbaggage;

import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.webserver.WebServer;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private static WebServer webServer;

    @BeforeAll
    static void startTheServer() {
        webServer = Main.startServer().await();

    }

    @AfterAll
    static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Test for: https://github.com/helidon-io/helidon/issues/6970
     */
    @Test
    void baggageCanaryMinimal() {
        final var tracer = io.helidon.tracing.Tracer.global();
        final var span = tracer.spanBuilder("baggageCanaryMinimal").start();
        // Set baggage and confirm that it's known in the span
        span.baggage("fubar", "1");
        assertThat(span.baggage("fubar").orElse(null), is("1"));

        // Inject the span (context) into the consumer
        final var consumer = HeaderConsumer
                .create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        tracer.inject(span.context(), HeaderProvider.empty(), consumer);

        // Check baggage propagated
        final var allKeys = consumer.keys().toString();
        assertTrue(allKeys.contains("fubar")
                , () -> "No injected baggage-fubar found in " + allKeys);

        span.end();
    }


}

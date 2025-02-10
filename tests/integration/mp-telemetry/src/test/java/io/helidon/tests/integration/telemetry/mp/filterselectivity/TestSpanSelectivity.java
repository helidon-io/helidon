/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.telemetry.mp.filterselectivity;

import java.time.Duration;
import java.util.List;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(TestSpanExporter.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")

class TestSpanSelectivity {

    @Inject
    private WebTarget webTarget;

    @Inject
    private TestSpanExporter testSpanExporter;

    @BeforeEach
    void clearSpanData() {
        testSpanExporter.clear();
    }

    @Test
    void checkSpansForDefaultGreeting() {
        Response response = webTarget.path("/greet").request(MediaType.TEXT_PLAIN).get();
        assertThat("Request status", response.getStatus(), is(200));

        List<SpanData> spanData = testSpanExporter.spanData(2); // Automatic GET span plus the resource span
        assertThat("Span data", spanData, hasSize(2));
    }

    @Test
    void checkSpansForPersonalizedGreeting() throws InterruptedException {
        Response response = webTarget.path("/greet/Joe").request(MediaType.TEXT_PLAIN).get();
        assertThat("Request status", response.getStatus(), is(200));

        List<SpanData> spanData = testSpanExporter.spanData(Duration.ofSeconds(2));
        assertThat("Span data", spanData, hasSize(1));
    }
}

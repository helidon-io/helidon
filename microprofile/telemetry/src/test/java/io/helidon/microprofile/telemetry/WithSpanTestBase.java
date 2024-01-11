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
package io.helidon.microprofile.telemetry;

import java.util.List;

import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(WithSpanBean.class)
@AddBean(TestSpanExporter.class)
@AddBean(TracedResource.class)
@AddConfig(key = "otel.sdk.disabled", value = "false")
@AddConfig(key = "otel.traces.exporter", value = "in-memory")
class WithSpanTestBase {

    @Inject
    WithSpanBean withSpanBean;

    @Inject
    TestSpanExporter testSpanExporter;

    @Inject
    WebTarget webTarget;

    @BeforeEach
    void clear() {
        testSpanExporter.clear();
    }

    void testSpanNameFromPath(SpanPathTestInfo spanPathTestInfo) {
        JaxRsCdiExtension jaxRsCdiExtension = CDI.current().getBeanManager().getExtension(JaxRsCdiExtension.class);
        List<JaxRsApplication> apps = jaxRsCdiExtension.applicationsToRun();
        Response response = webTarget.path(spanPathTestInfo.requestPath)
                .request(MediaType.TEXT_PLAIN)
                .get();
        assertThat("Status accessing " + spanPathTestInfo.requestPath, response.getStatus(), is(200));

        List<SpanData> spanData = testSpanExporter.spanData(2); // Automatic GET span and then the resource method span
        assertThat("Span name", spanData.get(0).getName(), is(spanPathTestInfo.expectedSpanName));
    }

    record SpanPathTestInfo(String requestPath, String expectedSpanName) {}
}

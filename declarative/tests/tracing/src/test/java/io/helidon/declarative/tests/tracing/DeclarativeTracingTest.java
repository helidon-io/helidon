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

package io.helidon.declarative.tests.tracing;

import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.http.HeaderNames;
import io.helidon.http.NotFoundException;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeclarativeTracingTest {
    private final Http1Client client;
    private final TestSpanExporter exporter;

    protected DeclarativeTracingTest(Http1Client client, TestTracerFactory exporter) {
        this.client = client;
        this.exporter = exporter.exporter();
    }

    @Test
    @Order(1)
    void testTraced() {
        var response = client.get("/endpoint/traced").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("traced"));

        // 3:
        // HTTP request
        // content write
        // annotated method
        var data = exporter.spanData(3);
        exporter.clear();

        SpanData httpRequest = null;
        SpanData contentWrite = null;
        SpanData tracedMethod = null;

        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
                httpRequest = spanDatum;
                break;
            case "content-write":
                contentWrite = spanDatum;
                break;
            default:
                tracedMethod = spanDatum;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing \"Http Request\"", httpRequest, notNullValue());
        assertThat("Found names: " + names + ", missing \"content-write\"", contentWrite, notNullValue());
        assertThat("Found names: " + names + ", missing traced method ", tracedMethod, notNullValue());

        String traceId = httpRequest.getTraceId();
        String parentSpanId = httpRequest.getSpanId();

        // other spans must be in the same trace id, and children of parentSpanId
        assertThat("Trace ID must be the same for all spans, content-write", contentWrite.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, content-write", contentWrite.getParentSpanId(), is(parentSpanId));
        assertThat("Trace ID must be the same for all spans, traced method", tracedMethod.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, traced method", tracedMethod.getParentSpanId(), is(parentSpanId));

        // and the tracedMethod must have all the expected values (from annotation and code)
        assertThat(tracedMethod.getName(), is(TestEndpoint.class.getName() + ".traced"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        var attributes = tracedMethod.getAttributes();

        assertAttribute(attributes, "endpoint", "Test");
    }

    @Test
    @Order(2)
    void testGreet() {
        String userAgent = "UNIT_TEST";
        var response = client.get("/endpoint/greet")
                .header(HeaderNames.USER_AGENT, userAgent)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello!"));

        // 3:
        // HTTP request
        // content write
        // annotated method
        var data = exporter.spanData(3);
        exporter.clear();

        SpanData httpRequest = null;
        SpanData contentWrite = null;
        SpanData tracedMethod = null;

        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
                httpRequest = spanDatum;
                break;
            case "content-write":
                contentWrite = spanDatum;
                break;
            default:
                tracedMethod = spanDatum;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing \"Http Request\"", httpRequest, notNullValue());
        assertThat("Found names: " + names + ", missing \"content-write\"", contentWrite, notNullValue());
        assertThat("Found names: " + names + ", missing traced method ", tracedMethod, notNullValue());

        String traceId = httpRequest.getTraceId();
        String parentSpanId = httpRequest.getSpanId();

        // other spans must be in the same trace id, and children of parentSpanId
        assertThat("Trace ID must be the same for all spans, content-write", contentWrite.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, content-write", contentWrite.getParentSpanId(), is(parentSpanId));
        assertThat("Trace ID must be the same for all spans, traced method", tracedMethod.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, traced method", tracedMethod.getParentSpanId(), is(parentSpanId));

        // and the tracedMethod must have all the expected values (from annotation and code)
        assertThat(tracedMethod.getName(), is("explicit-name"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        assertThat(tracedMethod.getStatus().getStatusCode(), is(StatusCode.UNSET));
        var attributes = tracedMethod.getAttributes();

        assertAttribute(attributes, "endpoint", "Test");
        assertAttribute(attributes, "custom", "customValue");
        assertAttribute(attributes, "userAgent", userAgent);
    }

    @Test
    @Order(3)
    void testFailed() {
        var response = client.get("/endpoint/failed")
                .request(String.class);

        assertThat(response.status(), is(Status.NOT_FOUND_404));

        // 3:
        // HTTP request
        // content write
        // annotated method
        var data = exporter.spanData(3);
        exporter.clear();

        SpanData httpRequest = null;
        SpanData contentWrite = null;
        SpanData tracedMethod = null;

        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
                httpRequest = spanDatum;
                break;
            case "content-write":
                contentWrite = spanDatum;
                break;
            default:
                tracedMethod = spanDatum;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing \"Http Request\"", httpRequest, notNullValue());
        assertThat("Found names: " + names + ", missing \"content-write\"", contentWrite, notNullValue());
        assertThat("Found names: " + names + ", missing traced method ", tracedMethod, notNullValue());

        String traceId = httpRequest.getTraceId();
        String parentSpanId = httpRequest.getSpanId();

        // other spans must be in the same trace id, and children of parentSpanId
        assertThat("Trace ID must be the same for all spans, content-write", contentWrite.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, content-write", contentWrite.getParentSpanId(), is(parentSpanId));
        assertThat("Trace ID must be the same for all spans, traced method", tracedMethod.getTraceId(), is(traceId));
        assertThat("Parent span must be correctly set, traced method", tracedMethod.getParentSpanId(), is(parentSpanId));

        // and the tracedMethod must have all the expected values (from annotation and code)
        assertThat(tracedMethod.getName(), is(TestEndpoint.class.getName() + ".failed"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        assertThat(tracedMethod.getStatus().getStatusCode(), is(StatusCode.ERROR));
        var attributes = tracedMethod.getAttributes();

        assertAttribute(attributes, "endpoint", "Test");

        // exception
        assertThat(tracedMethod.getTotalRecordedEvents(), is(1));

        EventData event = tracedMethod.getEvents().getFirst();
        assertThat(event.getName(), is("exception"));
        attributes = event.getAttributes();

        assertAttribute(attributes, "exception.message", "Bad bad");
        assertAttribute(attributes, "exception.type", NotFoundException.class.getName());
    }

    @Test
    @Order(4)
    void testInheritedMethodTraced() {
        var response = client.get("/inherited/traced").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("inherited traced"));

        var data = exporter.spanData(3);
        exporter.clear();

        SpanData tracedMethod = null;
        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
            case "content-write":
                break;
            default:
                tracedMethod = spanDatum;
                break;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing inherited traced method", tracedMethod, notNullValue());
        assertThat(tracedMethod.getName(), is("inherited-traced"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        assertThat(tracedMethod.getStatus().getStatusCode(), is(StatusCode.UNSET));

        assertAttribute(tracedMethod.getAttributes(), "source", "contract");
    }

    @Test
    @Order(5)
    void testInheritedTypeTraced() {
        var response = client.get("/inherited/type-traced").request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("type traced"));

        var data = exporter.spanData(3);
        exporter.clear();

        SpanData tracedMethod = null;
        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
            case "content-write":
                break;
            default:
                tracedMethod = spanDatum;
                break;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing inherited type traced method", tracedMethod, notNullValue());
        assertThat(tracedMethod.getName(), is("inherited-type-typeTraced"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        assertThat(tracedMethod.getStatus().getStatusCode(), is(StatusCode.UNSET));

        assertAttribute(tracedMethod.getAttributes(), "source", "contract-type");
    }

    @Test
    @Order(6)
    void testInheritedTaggedParam() {
        var response = client.get("/inherited/tagged")
                .queryParam("id", "7")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("tagged 7"));

        var data = exporter.spanData(3);
        exporter.clear();

        SpanData tracedMethod = null;
        for (SpanData spanDatum : data) {
            switch (spanDatum.getName()) {
            case "HTTP Request":
            case "content-write":
                break;
            default:
                tracedMethod = spanDatum;
                break;
            }
        }

        Set<String> names = data.stream()
                .map(SpanData::getName)
                .collect(Collectors.toSet());

        assertThat("Found names: " + names + ", missing inherited tagged traced method", tracedMethod, notNullValue());
        assertThat(tracedMethod.getName(), is("inherited-tagged"));
        assertThat(tracedMethod.getKind(), is(SpanKind.SERVER));
        assertThat(tracedMethod.getStatus().getStatusCode(), is(StatusCode.UNSET));

        assertAttribute(tracedMethod.getAttributes(), "source", "contract-type");
        Long actualValue = tracedMethod.getAttributes().get(AttributeKey.longKey("id"));
        assertThat("Long Attribute of key: id", actualValue, is(7L));
    }

    private void assertAttribute(Attributes attributes, String key, String expectedValue) {
        var actualValue = attributes.get(AttributeKey.stringKey(key));

        assertThat("String Attribute of key: " + key, actualValue, is(expectedValue));
    }
}

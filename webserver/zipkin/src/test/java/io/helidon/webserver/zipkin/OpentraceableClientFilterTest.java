/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.zipkin;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.MultivaluedHashMap;

import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.opentracing.OpentracingClientFilter;

import brave.internal.HexCodec;
import brave.opentracing.BraveSpanContext;
import brave.propagation.TraceContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * The ZipkinClientTest.
 */
public class OpentraceableClientFilterTest {

    private final Tracer tracer = ZipkinTracerBuilder.forService("test-service").build();
    private final OpentracingClientFilter filter = new OpentracingClientFilter();
    private final MultivaluedHashMap<Object, Object> map = new MultivaluedHashMap<>();
    private final Configuration configurationMock = Mockito.mock(Configuration.class);
    private final ClientRequestContext requestContextMock;

    public OpentraceableClientFilterTest() {
        this.requestContextMock = Mockito.mock(ClientRequestContext.class);
        when(this.requestContextMock.getUri()).thenReturn(URI.create("http://localhost/foo/bar"));
        when(this.requestContextMock.getMethod()).thenReturn("GET");
    }

    @BeforeEach
    public void connectMocks() throws Exception {
        Mockito.doReturn(configurationMock)
               .when(requestContextMock)
               .getConfiguration();

        Mockito.doReturn(map)
               .when(requestContextMock)
               .getHeaders();
    }

    @Test
    public void testNewSpanCreated() throws Exception {
        Mockito.doReturn(tracer)
               .when(requestContextMock)
               .getProperty(Mockito.anyString());

        filter.filter(requestContextMock);


        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-TraceId"), IsCollectionContaining.hasItem(IsInstanceOf.instanceOf(String.class))));
        String traceId = (String) map.getFirst("X-B3-TraceId");
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-SpanId"), IsCollectionContaining.hasItem(Is.is(traceId))));
    }

    @Test
    public void testTracerFromRequest() throws Exception {
        ServerRequest serverRequestMock = Mockito.mock(ServerRequest.class);
        WebServer webServerMock = Mockito.mock(WebServer.class);
        ServerConfiguration configurationMock = Mockito.mock(ServerConfiguration.class);
        RequestHeaders headersMock = Mockito.mock(RequestHeaders.class);

        Mockito.doReturn(webServerMock)
               .when(serverRequestMock)
               .webServer();

        Mockito.doReturn(configurationMock)
               .when(webServerMock)
               .configuration();

        Mockito.doReturn(tracer)
               .when(configurationMock)
               .tracer();

        Span span = tracer.buildSpan("my-good-parent").start();

        Mockito.doReturn(span.context())
               .when(serverRequestMock)
               .spanContext();

        Mockito.doReturn(serverRequestMock)
               .when(requestContextMock)
               .getProperty(OpentracingClientFilter.SERVER_REQUEST_PROPERTY_NAME);

        Mockito.doReturn(headersMock)
               .when(serverRequestMock)
               .headers();

        filter.filter(requestContextMock);

        TraceContext traceContext = ((BraveSpanContext) span.context()).unwrap();
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-TraceId"), IsCollectionContaining.hasItem(Is.is(traceContext.traceIdString()))));
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-SpanId"), IsCollectionContaining.hasItem(IsInstanceOf.instanceOf(String.class))));
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-ParentSpanId"), IsCollectionContaining.hasItem(Is.is(HexCodec.toLowerHex(traceContext.spanId())))));

        for (Map.Entry<Object, List<Object>> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    public void testChildSpanGetsCreated() throws Exception {
        Mockito.doReturn(tracer)
               .when(configurationMock)
               .getProperty(OpentracingClientFilter.TRACER_PROPERTY_NAME);

        Span span = tracer.buildSpan("my-parent").start();

        Mockito.doReturn(span.context())
               .when(requestContextMock)
               .getProperty(OpentracingClientFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME);

        filter.filter(requestContextMock);

        TraceContext traceContext = ((BraveSpanContext) span.context()).unwrap();
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-TraceId"), IsCollectionContaining.hasItem(Is.is(traceContext.traceIdString()))));
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-SpanId"), IsCollectionContaining.hasItem(IsInstanceOf.instanceOf(String.class))));
        assertThat(map, IsMapContaining.hasEntry(Is.is("X-B3-ParentSpanId"), IsCollectionContaining.hasItem(Is.is(HexCodec.toLowerHex(traceContext.spanId())))));

        for (Map.Entry<Object, List<Object>> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    public void testMissingTracer() throws Exception {

        filter.filter(requestContextMock);

        assertThat(map.entrySet(), IsEmptyCollection.empty());
    }
}

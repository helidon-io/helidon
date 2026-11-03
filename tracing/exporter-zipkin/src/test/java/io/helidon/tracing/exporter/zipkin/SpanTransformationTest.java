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

package io.helidon.tracing.exporter.zipkin;

import java.net.InetAddress;
import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class SpanTransformationTest {
    private final OtelToZipkinSpanTransformer transformer = OtelToZipkinSpanTransformer.create(() -> null);

    @Test
    void convertsIdentifiersTimeServiceAndScope() {
        var span = ZipkinTestSpans.span()
                .setParentSpanContext(SpanContext.create("0123456789abcdef0123456789abcdef", "fedcba9876543210",
                                                        TraceFlags.getSampled(), TraceState.getDefault())).build();
        Span result = transformer.generateSpan(span);
        assertThat(result.traceId(), is(span.getTraceId()));
        assertThat(result.id(), is(span.getSpanId()));
        assertThat(result.parentId(), is("fedcba9876543210"));
        assertThat(result.timestamp(), is(1000L));
        assertThat(result.duration(), is(5L));
        assertThat(result.localServiceName(), is("test-service"));
        assertThat(result.tags().get("otel.scope.name"), is("test-scope"));
        assertThat(result.tags().get("otel.library.name"), is("test-scope"));
        assertThat(result.tags().get("otel.scope.version"), is("1.2"));
        assertThat(result.tags().get("otel.library.version"), is("1.2"));
    }

    @Test
    void omitsInvalidParentAndInternalKindAndRoundsDurationUp() {
        Span result = transformer.generateSpan(ZipkinTestSpans.span().setKind(SpanKind.INTERNAL)
                                                      .setEndEpochNanos(1_000_001).build());
        assertThat(result.parentId(), nullValue());
        assertThat(result.kind(), nullValue());
        assertThat(result.duration(), is(1L));
    }

    @Test
    void mapsAllExternalKinds() {
        for (SpanKind kind : List.of(SpanKind.CLIENT, SpanKind.SERVER, SpanKind.CONSUMER, SpanKind.PRODUCER)) {
            assertThat(transformer.generateSpan(ZipkinTestSpans.span().setKind(kind).build()).kind().name(), is(kind.name()));
        }
    }

    @Test
    void convertsAttributesStatusAndDroppedCounts() {
        Attributes attributes = Attributes.builder().put("text", "café")
                .put("flag", true).put("number", 12L).put("decimal", 1.5)
                .put(AttributeKey.stringArrayKey("array"), List.of("one", "two")).build();
        Span result = transformer.generateSpan(ZipkinTestSpans.span().setAttributes(attributes)
                                                      .setTotalAttributeCount(attributes.size() + 2)
                                                      .setTotalRecordedEvents(3).setStatus(StatusData.create(StatusCode.ERROR, "failed")).build());
        assertThat(result.tags().get("text"), is("café"));
        assertThat(result.tags().get("flag"), is("true"));
        assertThat(result.tags().get("number"), is("12"));
        assertThat(result.tags().get("decimal"), is("1.5"));
        assertThat(result.tags().get("array"), is("one,two"));
        assertThat(result.tags().get("error"), is("failed"));
        assertThat(result.tags().get("otel.status_code"), is("ERROR"));
        assertThat(result.tags().get("otel.dropped_attributes_count"), is("2"));
        assertThat(result.tags().get("otel.dropped_events_count"), is("3"));
    }

    @Test
    void preservesExistingErrorTagAndOmitsUnsetStatus() {
        var span = ZipkinTestSpans.span().setAttributes(Attributes.builder().put("error", "existing").build());
        assertThat(transformer.generateSpan(span.setStatus(StatusData.create(StatusCode.ERROR, "replacement")).build()).tags().get("error"),
                   is("existing"));
        Span result = transformer.generateSpan(span.setStatus(StatusData.unset()).build());
        assertThat(result.tags().containsKey("otel.status_code"), is(false));
        assertThat(result.tags().get("error"), is("existing"));
    }

    @Test
    void convertsEventsAndOmitsLinks() {
        var span = ZipkinTestSpans.span().setEvents(List.of(EventData.create(1_003_000, "event",
                                                                          Attributes.builder().put("count", 7L).build())))
                .setLinks(List.of(LinkData.create(SpanContext.getInvalid()))).build();
        Span result = transformer.generateSpan(span);
        assertThat(result.annotations(), hasSize(1));
        assertThat(result.annotations().getFirst().timestamp(), is(1003L));
        assertThat(result.annotations().getFirst().value(), is("\"event\":{\"count\":7}"));
    }

    @Test
    void createsRemoteEndpointOnlyForClientOrProducerWithPeerService() {
        var span = ZipkinTestSpans.span().setAttributes(Attributes.builder().put("peer.service", "backend")
                                                              .put("server.socket.address", "192.0.2.1")
                                                              .put("server.socket.port", 8080).build());
        assertThat(transformer.generateSpan(span.setKind(SpanKind.CLIENT).build()).remoteServiceName(), is("backend"));
        assertThat(transformer.generateSpan(span.setKind(SpanKind.PRODUCER).build()).remoteEndpoint().port(), is(8080));
        assertThat(transformer.generateSpan(span.setKind(SpanKind.SERVER).build()).remoteEndpoint(), nullValue());
        assertThat(transformer.generateSpan(span.setKind(SpanKind.CLIENT).setAttributes(Attributes.empty()).build())
                           .remoteEndpoint(), nullValue());
    }

    @Test
    void supportsExplicitIpv4AndIpv6AndDefaultServiceName() throws Exception {
        for (String address : List.of("192.0.2.1", "2001:db8::1")) {
            InetAddress ip = InetAddress.getByName(address);
            Span result = OtelToZipkinSpanTransformer.create(() -> ip)
                    .generateSpan(ZipkinTestSpans.span().setResource(Resource.empty()).build());
            assertThat(result.localServiceName(), is(Resource.getDefault().getAttribute(AttributeKey.stringKey("service.name"))));
            assertThat(ip.getAddress().length == 4 ? result.localEndpoint().ipv4() : result.localEndpoint().ipv6(), is(address));
        }
    }
}

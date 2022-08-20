/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.hamcrest.number.IsCloseTo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

/**
 * Tests {@link io.helidon.webserver.NettyRequestHeaders}.
 */
class NettyRequestHeadersTest {
    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));
    private static final Http.HeaderName FOO_HEADER = Http.Header.create("Foo");

    NettyRequestHeaders withHeader(String name, String... values) {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(name, List.of(values));
        return new NettyRequestHeaders(headers);
    }

    @Test
    void allConcatenated() {
        NettyRequestHeaders hs = withHeader("Foo", "val1", "val 2", "val3,val4", "val5");
        assertThat(hs.value(FOO_HEADER).orElse(null), is("val1,val 2,val3,val4,val5"));
    }

    @Test
    void allSepar() {
        NettyRequestHeaders hs = withHeader("Foo", "val1", "val 2", "val3,val4", "val5", "val 6,val7,val 8", "val9");
        assertThat(hs.values(Http.Header.create("Foo")),
                   hasItems("val1", "val 2", "val3", "val4", "val5", "val 6", "val7", "val 8", "val9"));
    }

    @Test
    void contentType() {
        NettyRequestHeaders hs = withHeader(Http.Header.CONTENT_TYPE.defaultCase(), MediaTypes.APPLICATION_JSON.text());
        assertThat(hs.contentType().isPresent(), is(true));
        assertThat(hs.contentType().get().mediaType(), is(MediaTypes.APPLICATION_JSON));
    }

    @Test
    void contentLength() {
        NettyRequestHeaders hs = withHeader(Http.Header.CONTENT_LENGTH.defaultCase(), "1024");
        assertThat(hs.contentLength().isPresent(), is(true));
        assertThat(hs.contentLength().getAsLong(), is(1024L));
    }

    @Test
    void cookies() {
        NettyRequestHeaders hs
                = withHeader(Http.Header.COOKIE.defaultCase(),
                             "foo=bar; aaa=bbb; c=what_the_hell; aaa=ccc",
                             "$version=1; some=other; $Domain=google.com, aaa=eee, d=cool; $Domain=google.com; $Path=\"/foo\"");
        Parameters cookies = hs.cookies();
        assertThat(cookies.all("foo"), contains("bar"));
        assertThat(cookies.all("c"), contains("what_the_hell"));
        assertThat(cookies.all("aaa"), contains("bbb", "ccc", "eee"));
        assertThat(cookies.all("some"), contains("other"));
        assertThat(cookies.all("d"), contains("cool"));
    }

    @Test
    void acceptedTypes() {
        NettyRequestHeaders hs = withHeader(Http.Header.ACCEPT.defaultCase(),
                                            "text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4");
        assertThat(hs.acceptedTypes().size(), is(4));
        // ordered by q
        assertThat(hs.acceptedTypes().get(0), is(createMt("text", "html", Map.of("level", "1"))));
        assertThat(hs.acceptedTypes().get(1), is(createMt("text", "html", Map.of("q", "0.7"))));
        assertThat(hs.acceptedTypes().get(2),
                   is(createMt("text", "html", Map.of("level", "2", "q", "0.4"))));
        assertThat(hs.acceptedTypes().get(3), is(createMt("text", "*", Map.of("q", "0.3"))));
        assertThat(hs.acceptedTypes().get(3).qualityFactor(), IsCloseTo.closeTo(0, 0.3));
    }

    @Test
    void hucDefaultAccept() {
        try {
            NettyRequestHeaders hs = withHeader(Http.Header.ACCEPT.defaultCase(),
                                                NettyRequestHeaders.HUC_ACCEPT_DEFAULT.values());
            assertThat(hs.acceptedTypes().get(0).mediaType(), is(MediaTypes.TEXT_HTML));
            assertThat(hs.acceptedTypes().get(1), is(createMt("image", "gif")));
            assertThat(hs.acceptedTypes().get(2), is(createMt("image", "jpeg")));
            assertThat(hs.acceptedTypes().get(3).mediaType(), is(MediaTypes.WILDCARD));
            assertThat(hs.acceptedTypes().get(3).qualityFactor(), IsCloseTo.closeTo(0, 0.2));
        } catch (IllegalStateException ex) {
            Assertions.fail(ex.getMessage(), ex);
        }
    }

    @Test
    void isAccepted() {
        NettyRequestHeaders hs = withHeader(Http.Header.ACCEPT.defaultCase(), "text/*;q=0.3, application/json;q=0.7");
        assertThat(hs.isAccepted(MediaTypes.TEXT_HTML), is(true));
        assertThat(hs.isAccepted(MediaTypes.TEXT_XML), is(true));
        assertThat(hs.isAccepted(MediaTypes.APPLICATION_JSON), is(true));
        assertThat(hs.isAccepted(MediaTypes.APPLICATION_OCTET_STREAM), is(false));
    }

    @Test
    void bestAccepted() {
        NettyRequestHeaders hs = withHeader(Http.Header.ACCEPT.defaultCase(),
                                            "text/*;q=0.3, text/html;q=0.7, text/xml;q=0.4");
        assertThat(hs.bestAccepted(MediaTypes.APPLICATION_JSON,
                                   MediaTypes.TEXT_PLAIN,
                                   MediaTypes.TEXT_XML).orElse(null),
                   is(MediaTypes.TEXT_XML));
        assertThat(hs.bestAccepted(MediaTypes.APPLICATION_JSON,
                                   MediaTypes.TEXT_HTML,
                                   MediaTypes.TEXT_XML).orElse(null),
                   is(MediaTypes.TEXT_HTML));
        assertThat(hs.bestAccepted(MediaTypes.APPLICATION_JSON,
                                   MediaTypes.TEXT_PLAIN).orElse(null),
                   is(MediaTypes.TEXT_PLAIN));
        assertThat(hs.bestAccepted(MediaTypes.APPLICATION_JSON).isPresent(), is(false));
        assertThat(hs.bestAccepted().isPresent(), is(false));
    }

    @Test
    void acceptDatetime() {
        NettyRequestHeaders hs = withHeader(Http.Header.ACCEPT_DATETIME.defaultCase(), "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.acceptDatetime().orElse(null), is(ZDT));
    }

    @Test
    void date() {
        NettyRequestHeaders hs = withHeader(Http.Header.DATE.defaultCase(), "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.date().orElse(null), is(ZDT));
    }

    @Test
    void ifModifiedSince() {
        NettyRequestHeaders hs = withHeader(Http.Header.IF_MODIFIED_SINCE.defaultCase(), "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.ifModifiedSince().orElse(null), is(ZDT));
    }

    @Test
    void ifUnmodifiedSince() {
        NettyRequestHeaders hs = withHeader(Http.Header.IF_UNMODIFIED_SINCE.defaultCase(), "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.ifUnmodifiedSince().orElse(null), is(ZDT));
    }

    @Test
    void referer() {
        NettyRequestHeaders hs = withHeader(Http.Header.REFERER.defaultCase(), "http://www.google.com");
        assertThat(hs.referer().map(URI::toString).orElse(null), is("http://www.google.com"));
    }

    private HttpMediaType createMt(String type, String subtype) {
        return HttpMediaType.create(MediaTypes.create(type, subtype));
    }

    private HttpMediaType createMt(String type, String subtype, Map<String, String> params) {
        return HttpMediaType.builder()
                .mediaType(MediaTypes.create(type, subtype))
                .parameters(params)
                .build();
    }
}

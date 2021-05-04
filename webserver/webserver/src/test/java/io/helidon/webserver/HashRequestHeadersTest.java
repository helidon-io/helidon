/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

import org.hamcrest.number.IsCloseTo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

/**
 * Tests {@link HashRequestHeaders}.
 */
public class HashRequestHeadersTest {
    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));

    HashRequestHeaders withHeader(String name, String... values) {
        Map<String, List<String>> map = new HashMap<>(1);
        map.put(name, new ArrayList<>(Arrays.asList(values)));
        return new HashRequestHeaders(map);
    }

    @Test
    public void allConcatenated() {
        HashRequestHeaders hs = withHeader("Foo", "val1", "val 2", "val3,val4", "val5");
        assertThat(hs.value("Foo").orElse(null), is("val1,val 2,val3,val4,val5"));
    }

    @Test
    public void allSepar() {
        HashRequestHeaders hs = withHeader("Foo", "val1", "val 2", "val3,val4", "val5", "val 6,val7,val 8", "val9");
        assertThat(hs.values("Foo"),
                   hasItems("val1", "val 2", "val3", "val4", "val5", "val 6", "val7", "val 8", "val9"));
    }

    @Test
    public void contentType() {
        HashRequestHeaders hs = withHeader(Http.Header.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
        assertThat(hs.contentType().isPresent(), is(true));
        assertThat(hs.contentType().get(), is(MediaType.APPLICATION_JSON));
    }

    @Test
    public void contentLength() {
        HashRequestHeaders hs = withHeader(Http.Header.CONTENT_LENGTH, "1024");
        assertThat(hs.contentLength().isPresent(), is(true));
        assertThat(hs.contentLength().getAsLong(), is(1024L));
    }

    @Test
    public void cookies() {
        HashRequestHeaders hs
                = withHeader(Http.Header.COOKIE,
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
    public void acceptedTypes() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT,
                                           "text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4");
        assertThat(hs.acceptedTypes().size(), is(4));
        assertThat(hs.acceptedTypes().get(0), is(createMt("text", "*", Map.of("q", "0.3"))));
        assertThat(hs.acceptedTypes().get(0).qualityFactor(), IsCloseTo.closeTo(0, 0.3));
        assertThat(hs.acceptedTypes().get(1), is(createMt("text", "html", Map.of("q", "0.7"))));
        assertThat(hs.acceptedTypes().get(2), is(createMt("text", "html", Map.of("level", "1"))));
        assertThat(hs.acceptedTypes().get(3),
                   is(createMt("text", "html", Map.of("level", "2", "q", "0.4"))));
    }

    @Test
    public void hucDefaultAccept(){
        try {
            HashRequestHeaders hs = withHeader(Http.Header.ACCEPT, HashRequestHeaders.HUC_ACCEPT_DEFAULT);
            assertThat(hs.acceptedTypes().get(0), is(MediaType.TEXT_HTML));
            assertThat(hs.acceptedTypes().get(1), is(createMt("image", "gif")));
            assertThat(hs.acceptedTypes().get(2), is(createMt("image", "jpeg")));
            assertThat(hs.acceptedTypes().get(3), is(createMt("*", "*", Map.of("q", ".2"))));
        } catch(IllegalStateException ex){
            Assertions.fail(ex.getMessage(), ex);
        }
    }

    private MediaType createMt(String type, String subtype) {
        return MediaType.builder()
                .type(type)
                .subtype(subtype)
                .build();
    }

    private MediaType createMt(String type, String subtype, Map<String, String> params) {
        return MediaType.builder()
                .type(type)
                .subtype(subtype)
                .parameters(params)
                .build();
    }

    @Test
    public void isAccepted() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT, "text/*;q=0.3, application/json;q=0.7");
        assertThat(hs.isAccepted(MediaType.TEXT_HTML), is(true));
        assertThat(hs.isAccepted(MediaType.TEXT_XML), is(true));
        assertThat(hs.isAccepted(MediaType.APPLICATION_JSON), is(true));
        assertThat(hs.isAccepted(MediaType.APPLICATION_OCTET_STREAM), is(false));
    }

    @Test
    public void bestAccepted() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT,
                                           "text/*;q=0.3, text/html;q=0.7, text/xml;q=0.4");
        assertThat(hs.bestAccepted(MediaType.APPLICATION_JSON,
                                   MediaType.TEXT_PLAIN,
                                   null,
                                   MediaType.TEXT_XML).orElse(null),
                   is(MediaType.TEXT_XML));
        assertThat(hs.bestAccepted(MediaType.APPLICATION_JSON,
                                   MediaType.TEXT_HTML,
                                   MediaType.TEXT_XML).orElse(null),
                   is(MediaType.TEXT_HTML));
        assertThat(hs.bestAccepted(MediaType.APPLICATION_JSON,
                                   MediaType.TEXT_PLAIN).orElse(null),
                   is(MediaType.TEXT_PLAIN));
        assertThat(hs.bestAccepted(MediaType.APPLICATION_JSON).isPresent(), is(false));
        assertThat(hs.bestAccepted().isPresent(), is(false));
    }

    @Test
    public void acceptDatetime() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT_DATETIME, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.acceptDatetime().orElse(null), is(ZDT));
    }

    @Test
    public void date() {
        HashRequestHeaders hs = withHeader(Http.Header.DATE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.date().orElse(null), is(ZDT));
    }

    @Test
    public void ifModifiedSince() {
        HashRequestHeaders hs = withHeader(Http.Header.IF_MODIFIED_SINCE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.ifModifiedSince().orElse(null), is(ZDT));
    }

    @Test
    public void ifUnmodifiedSince() {
        HashRequestHeaders hs = withHeader(Http.Header.IF_UNMODIFIED_SINCE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertThat(hs.ifUnmodifiedSince().orElse(null), is(ZDT));
    }

    @Test
    public void referer() {
        HashRequestHeaders hs = withHeader(Http.Header.REFERER, "http://www.google.com");
        assertThat(hs.referer().map(URI::toString).orElse(null), is("http://www.google.com"));
    }
}

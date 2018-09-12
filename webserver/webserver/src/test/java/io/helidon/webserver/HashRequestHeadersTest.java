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

package io.helidon.webserver;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("val1,val 2,val3,val4,val5", hs.value("Foo").orElse(null));
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
        assertTrue(hs.contentType().isPresent());
        assertEquals(MediaType.APPLICATION_JSON, hs.contentType().get());
    }

    @Test
    public void contentLength() {
        HashRequestHeaders hs = withHeader(Http.Header.CONTENT_LENGTH, "1024");
        assertTrue(hs.contentLength().isPresent());
        assertEquals(1024L, hs.contentLength().getAsLong());
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
        assertEquals(4, hs.acceptedTypes().size());
        assertEquals(new MediaType("text", "*", CollectionsHelper.mapOf("q", "0.3")), hs.acceptedTypes().get(0));
        assertEquals(0, hs.acceptedTypes().get(0).qualityFactor(), 0.3);
        assertEquals(new MediaType("text", "html", CollectionsHelper.mapOf("q", "0.7")), hs.acceptedTypes().get(1));
        assertEquals(new MediaType("text", "html", CollectionsHelper.mapOf("level", "1")), hs.acceptedTypes().get(2));
        assertEquals(new MediaType("text", "html", CollectionsHelper.mapOf("level", "2", "q", "0.4")),
                     hs.acceptedTypes().get(3));
    }

    @Test
    public void isAccepted() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT, "text/*;q=0.3, application/json;q=0.7");
        assertTrue(hs.isAccepted(MediaType.TEXT_HTML));
        assertTrue(hs.isAccepted(MediaType.TEXT_XML));
        assertTrue(hs.isAccepted(MediaType.APPLICATION_JSON));
        assertFalse(hs.isAccepted(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    public void bestAccepted() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT,
                                           "text/*;q=0.3, text/html;q=0.7, text/xml;q=0.4");
        assertEquals(MediaType.TEXT_XML, hs.bestAccepted(MediaType.APPLICATION_JSON,
                                                         MediaType.TEXT_PLAIN,
                                                         null,
                                                         MediaType.TEXT_XML).orElse(null));
        assertEquals(MediaType.TEXT_HTML, hs.bestAccepted(MediaType.APPLICATION_JSON,
                                                         MediaType.TEXT_HTML,
                                                         MediaType.TEXT_XML).orElse(null));
        assertEquals(MediaType.TEXT_PLAIN, hs.bestAccepted(MediaType.APPLICATION_JSON,
                                                         MediaType.TEXT_PLAIN).orElse(null));
        assertFalse(hs.bestAccepted(MediaType.APPLICATION_JSON).isPresent());
        assertFalse(hs.bestAccepted().isPresent());
    }

    @Test
    public void acceptDatetime() {
        HashRequestHeaders hs = withHeader(Http.Header.ACCEPT_DATETIME, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertEquals(ZDT, hs.acceptDatetime().orElse(null));
    }

    @Test
    public void date() {
        HashRequestHeaders hs = withHeader(Http.Header.DATE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertEquals(ZDT, hs.date().orElse(null));
    }

    @Test
    public void ifModifiedSince() {
        HashRequestHeaders hs = withHeader(Http.Header.IF_MODIFIED_SINCE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertEquals(ZDT, hs.ifModifiedSince().orElse(null));
    }

    @Test
    public void ifUnmodifiedSince() {
        HashRequestHeaders hs = withHeader(Http.Header.IF_UNMODIFIED_SINCE, "Tue, 3 Jun 2008 11:05:30 GMT");
        assertEquals(ZDT, hs.ifUnmodifiedSince().orElse(null));
    }

    @Test
    public void referer() {
        HashRequestHeaders hs = withHeader(Http.Header.REFERER, "http://www.google.com");
        assertEquals("http://www.google.com", hs.referer().map(URI::toString).orElse(null));
    }
}

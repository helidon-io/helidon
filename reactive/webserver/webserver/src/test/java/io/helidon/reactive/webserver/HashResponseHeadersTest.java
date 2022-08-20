/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.SetCookie;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.Test;

import static io.helidon.reactive.webserver.utils.TestUtils.assertException;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link HashResponseHeaders}.
 */
class HashResponseHeadersTest {

    public static final Http.HeaderName HEADER_A = Header.create("a");

    @Test
    void acceptPatches() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.addAcceptPatches(MediaTypes.APPLICATION_JSON, MediaTypes.TEXT_XML);
        assertThat(h.acceptPatches(), contains(HttpMediaType.APPLICATION_JSON, HttpMediaType.create(MediaTypes.TEXT_XML)));
    }

    @Test
    void contentType() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.contentType(MediaTypes.APPLICATION_JSON);
        assertThat(h.contentType().orElse(null), is(HttpMediaType.APPLICATION_JSON));
        h.remove(Header.CONTENT_TYPE);
        assertThat(h.contentType().isPresent(), is(false));
    }

    @Test
    void expires() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        ZonedDateTime now = ZonedDateTime.now();
        h.expires(now);
        assertThat(h.expires().orElse(null), is(now.truncatedTo(ChronoUnit.SECONDS).withFixedOffsetZone()));
        h.remove(Header.EXPIRES);
        assertThat(h.expires().isPresent(), is(false));
        Instant instant = Instant.now();
        h.expires(instant);
        assertThat(h.expires().map(ZonedDateTime::toInstant).orElse(null), is(instant.truncatedTo(ChronoUnit.SECONDS)));
    }

    @Test
    void lastModified() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        ZonedDateTime now = ZonedDateTime.now();
        h.lastModified(now);
        assertThat(h.lastModified().orElse(null), is(now.truncatedTo(ChronoUnit.SECONDS).withFixedOffsetZone()));
        h.remove(Header.LAST_MODIFIED);
        assertThat(h.lastModified().isPresent(), is(false));
        Instant instant = Instant.now();
        h.lastModified(instant);
        assertThat(h.lastModified().map(ZonedDateTime::toInstant).orElse(null), is(instant.truncatedTo(ChronoUnit.SECONDS)));
    }

    @Test
    void location() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        URI uri = URI.create("http://www.oracle.com");
        h.location(uri);
        assertThat(h.location().orElse(null), is(uri));
        h.remove(Header.LOCATION);
        assertThat(h.location().isPresent(), is(false));
    }

    @Test
    void addCookie() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.addCookie("foo", "bar");

        h.addCookie("aaa", "bbbb", Duration.ofMinutes(10));
        h.addCookie("who", "me", Duration.ofMinutes(0));

        h.addCookie(SetCookie.builder("itis", "cool")
                            .domainAndPath(URI.create("http://oracle.com/foo"))
                            .expires(ZonedDateTime.of(2080, 1, 1, 0, 0, 0, 0, ZoneId.of("Z")))
                            .secure(true)
                            .build());

        assertThat(h.all(Header.SET_COOKIE, List::of), contains("foo=bar",
                                                                "aaa=bbbb; Max-Age=600",
                                                                "who=me",
                                                                "itis=cool; Expires=Mon, 1 Jan 2080 00:00:00 GMT; Domain=oracle.com;"
                                                                   + " Path=/foo; Secure"));
    }

    @Test
    void addAndClearCookies() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.addCookie("foo1", "bar1");
        h.addCookie("foo2", "bar2");
        assertThat(h.all(Header.SET_COOKIE, List::of), contains(
                "foo1=bar1",
                "foo2=bar2"));
        h.clearCookie("foo1");
        assertThat(h.all(Header.SET_COOKIE, List::of), contains(
                "foo1=deleted; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Path=/",
                "foo2=bar2"));
    }

    @Test
    void clearCookie() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.clearCookie("foo1");
        assertThat(h.all(Header.SET_COOKIE, List::of), contains(
                "foo1=deleted; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Path=/"));
    }

    @Test
    void immutableWhenCompleted() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(mockBareResponse());
        h.set(HEADER_A, "b");
        h.add(HeaderValue.create(HEADER_A, "b"));
        h.setIfAbsent(HeaderValue.create(HEADER_A, "b"));
        h.remove(HEADER_A);

        h.send().toCompletableFuture().get();
        assertException(() -> h.set(HEADER_A, "b"), AlreadyCompletedException.class);
        assertException(() -> h.add(HeaderValue.create(HEADER_A, "b")), AlreadyCompletedException.class);
        assertException(() -> h.setIfAbsent(HeaderValue.create(HEADER_A, "b")), AlreadyCompletedException.class);
        assertException(() -> h.remove(HEADER_A), AlreadyCompletedException.class);
    }

    @Test
    void beforeSent() throws Exception {
        StringBuffer sb = new StringBuffer();
        HashResponseHeaders h = new HashResponseHeaders(mockBareResponse());
        h.beforeSend(headers -> sb.append("B:" + (h == headers)));
        assertThat(sb.toString(), is(""));
        h.send();
        h.send();
        h.send();
        h.send().toCompletableFuture().get();
        assertThat(sb.toString(), is("B:true"));
    }

    @Test
    void headersFiltrationFor204() throws Exception {
        BareResponse bareResponse = mockBareResponse();
        HashResponseHeaders h = new HashResponseHeaders(bareResponse);
        h.set(Header.CONTENT_TYPE, "text/plain");
        h.set(Header.create("some"), "some_value");
        h.set(Header.TRANSFER_ENCODING, "custom");
        h.httpStatus(Http.Status.NO_CONTENT_204);
        h.send().toCompletableFuture().get();
        verify(bareResponse).writeStatusAndHeaders(any(), argThat(m -> m.containsKey("some")
                && !m.containsKey(Header.CONTENT_TYPE.defaultCase())
                && !m.containsKey(Header.TRANSFER_ENCODING.defaultCase())));
    }

    private BareResponse mockBareResponse() {
        CompletableFuture<BareResponse> headersFuture = new CompletableFuture<>();
        CompletableFuture<BareResponse> future = new CompletableFuture<>();
        BareResponse result = mock(BareResponse.class);
        when(result.whenHeadersCompleted()).thenReturn(Single.create(headersFuture));
        when(result.whenCompleted()).thenReturn(Single.create(future));
        doAnswer(invocationOnMock -> {
            headersFuture.complete(result);
            return null;
        }).when(result).writeStatusAndHeaders(any(), any());
        return result;
    }
}

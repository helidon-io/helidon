/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.SetCookie;
import io.helidon.common.reactive.Single;

import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.utils.TestUtils.assertException;
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
public class HashResponseHeadersTest {

    @Test
    public void acceptPatches() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.addAcceptPatches(MediaType.APPLICATION_JSON, MediaType.TEXT_XML);
        assertThat(h.acceptPatches(), IsIterableContainingInOrder.contains(MediaType.APPLICATION_JSON, MediaType.TEXT_XML));
    }

    @Test
    public void contentType() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.contentType(MediaType.APPLICATION_JSON);
        assertThat(h.contentType().orElse(null), is(MediaType.APPLICATION_JSON));
        h.contentType(null);
        assertThat(h.contentType().isPresent(), is(false));
    }

    @Test
    public void expires() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(null);
        ZonedDateTime now = ZonedDateTime.now();
        h.expires(now);
        assertThat(h.expires().orElse(null), is(now.truncatedTo(ChronoUnit.SECONDS).withFixedOffsetZone()));
        h.expires((ZonedDateTime) null);
        assertThat(h.expires().isPresent(), is(false));
        Instant instant = Instant.now();
        h.expires(instant);
        assertThat(h.expires().map(ZonedDateTime::toInstant).orElse(null), is(instant.truncatedTo(ChronoUnit.SECONDS)));
    }

    @Test
    public void lastModified() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(null);
        ZonedDateTime now = ZonedDateTime.now();
        h.lastModified(now);
        assertThat(h.lastModified().orElse(null), is(now.truncatedTo(ChronoUnit.SECONDS).withFixedOffsetZone()));
        h.lastModified((ZonedDateTime) null);
        assertThat(h.lastModified().isPresent(), is(false));
        Instant instant = Instant.now();
        h.lastModified(instant);
        assertThat(h.lastModified().map(ZonedDateTime::toInstant).orElse(null), is(instant.truncatedTo(ChronoUnit.SECONDS)));
    }

    @Test
    public void location() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(null);
        URI uri = URI.create("http://www.oracle.com");
        h.location(uri);
        assertThat(h.location().orElse(null), is(uri));
        h.location(null);
        assertThat(h.location().isPresent(), is(false));
    }

    @Test
    public void addCookie() {
        HashResponseHeaders h = new HashResponseHeaders(null);
        h.addCookie("foo", "bar");

        h.addCookie("aaa", "bbbb", Duration.ofMinutes(10));
        h.addCookie("who", "me", Duration.ofMinutes(0));

        h.addCookie(SetCookie.builder("itis", "cool")
                            .domainAndPath(URI.create("http://oracle.com/foo"))
                            .expires(ZonedDateTime.of(2080, 1, 1, 0, 0, 0, 0, ZoneId.of("Z")))
                            .secure(true)
                            .build());

        assertThat(h.all(Http.Header.SET_COOKIE), contains("foo=bar",
                                                           "aaa=bbbb; Max-Age=600",
                                                           "who=me",
                                                           "itis=cool; Expires=Mon, 1 Jan 2080 00:00:00 GMT; Domain=oracle.com;"
                                                                   + " Path=/foo; Secure"));
    }

    @Test
    public void immutableWhenCompleted() throws Exception {
        HashResponseHeaders h = new HashResponseHeaders(mockBareResponse());
        h.put("a", "b");
        h.put("a", Arrays.asList("b"));
        h.add("a", "b");
        h.add("a", Arrays.asList("b"));
        h.putIfAbsent("a", "b");
        h.putIfAbsent("a", Arrays.asList("b"));
        h.computeIfAbsent("a", k -> Arrays.asList("b"));
        h.computeSingleIfAbsent("a", k -> "b");
        h.putAll(h);
        h.addAll(h);
        h.remove("a");

        h.send().toCompletableFuture().get();
        assertException(() -> h.put("a", "b"), AlreadyCompletedException.class);
        assertException(() -> h.put("a", Arrays.asList("b")), AlreadyCompletedException.class);
        assertException(() -> h.add("a", "b"), AlreadyCompletedException.class);
        assertException(() -> h.add("a", Arrays.asList("b")), AlreadyCompletedException.class);
        assertException(() -> h.putIfAbsent("a", "b"), AlreadyCompletedException.class);
        assertException(() -> h.putIfAbsent("a", Arrays.asList("b")), AlreadyCompletedException.class);
        assertException(() -> h.computeIfAbsent("a", k -> Arrays.asList("b")), AlreadyCompletedException.class);
        assertException(() -> h.computeSingleIfAbsent("a", k -> "b"), AlreadyCompletedException.class);
        assertException(() -> h.putAll(h), AlreadyCompletedException.class);
        assertException(() -> h.addAll(h), AlreadyCompletedException.class);
        assertException(() -> h.remove("a"), AlreadyCompletedException.class);
    }

    @Test
    public void beforeSent() throws Exception {
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
    public void headersFiltrationFor204() throws Exception {
        BareResponse bareResponse = mockBareResponse();
        HashResponseHeaders h = new HashResponseHeaders(bareResponse);
        h.put(Http.Header.CONTENT_TYPE, "text/plain");
        h.put("some", "some_value");
        h.put(Http.Header.TRANSFER_ENCODING, "custom");
        h.httpStatus(Http.Status.NO_CONTENT_204);
        h.send().toCompletableFuture().get();
        verify(bareResponse).writeStatusAndHeaders(any(), argThat(m -> m.containsKey("some")
                && !m.containsKey(Http.Header.CONTENT_TYPE)
                && !m.containsKey(Http.Header.TRANSFER_ENCODING)));
    }

    private BareResponse mockBareResponse() {
        CompletableFuture<BareResponse> headersFuture = new CompletableFuture<>();
        CompletableFuture<BareResponse> future = new CompletableFuture<>();
        BareResponse result = mock(BareResponse.class);
        when(result.whenHeadersCompleted()).thenReturn(Single.from(headersFuture));
        when(result.whenCompleted()).thenReturn(Single.from(future));
        doAnswer(invocationOnMock -> {
            headersFuture.complete(result);
            return null;
        }).when(result).writeStatusAndHeaders(any(), any());
        return result;
    }
}
